package com.luistureo.voicereminderapp.core.calendar.microsoft

import android.content.Context
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarSuspensionPolicy
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarSyncLogger
import com.luistureo.voicereminderapp.core.calendar.unified.MeetingContentSanitizer
import com.luistureo.voicereminderapp.core.calendar.unified.MeetingUrlPolicy
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarApiUsageGuard
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarHttpRequestExecutor
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarIncrementalCursor
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarIncrementalSyncPolicy
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarIncrementalSyncStore
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarBackoffPolicy
import com.luistureo.voicereminderapp.core.calendar.unified.NoOpCalendarIncrementalSyncStore
import com.luistureo.voicereminderapp.core.calendar.unified.SharedPreferencesCalendarIncrementalSyncStore
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarIdempotencyKey
import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import com.luistureo.voicereminderapp.domain.model.Reminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class MicrosoftGraphCalendarGateway(
    private val authController: MicrosoftCalendarAuthController,
    private val client: OkHttpClient = OkHttpClient(),
    private val requestExecutor: CalendarHttpRequestExecutor = CalendarHttpRequestExecutor(client),
    private val incrementalSyncStore: CalendarIncrementalSyncStore =
        NoOpCalendarIncrementalSyncStore
) : MicrosoftCalendarGateway {
    override val isConfigured: Boolean
        get() = authController.isAuthConfigured

    override val isConnected: Boolean
        get() = authController.isConnected

    override suspend fun upsertReminder(reminder: Reminder): String = withContext(Dispatchers.IO) {
        val accessToken = authController.acquireAccessToken()
        val eventId = reminder.externalIdsByProvider[CalendarProvider.MICROSOFT_CALENDAR]
            ?.takeIf { it.isNotBlank() }
        if (eventId == null) {
            createEvent(accessToken, reminder)
        } else {
            try {
                updateEvent(accessToken, eventId, reminder)
            } catch (exception: MicrosoftGraphApiException) {
                if (exception.statusCode == 404 || exception.statusCode == 410) {
                    CalendarSyncLogger.fallback(
                        provider = CalendarProvider.MICROSOFT_CALENDAR,
                        action = "update_event",
                        fallbackReason = "remote_event_missing_create_new"
                    )
                    createEvent(accessToken, reminder)
                } else {
                    throw exception
                }
            }
        }
    }

    override suspend fun deleteEvent(eventId: String): Unit = withContext(Dispatchers.IO) {
        val accessToken = authController.acquireAccessToken()
        val request = Request.Builder()
            .url("${MicrosoftCalendarConfig.GRAPH_BASE_URL}/me/events/${eventId.encodePathSegment()}")
            .addBearerToken(accessToken)
            .delete()
            .build()

        requestExecutor.execute(
            request,
            CalendarProvider.MICROSOFT_CALENDAR,
            "delete_event"
        ).use { response ->
            if (response.isSuccessful || response.code == 404 || response.code == 410) {
                CalendarSyncLogger.apiStatus(
                    CalendarProvider.MICROSOFT_CALENDAR,
                    action = "delete_event",
                    statusCode = response.code
                )
            } else {
                CalendarSyncLogger.apiError(
                    CalendarProvider.MICROSOFT_CALENDAR,
                    action = "delete_event",
                    statusCode = response.code
                )
                throw MicrosoftGraphApiException(
                    statusCode = response.code,
                    retryAfterSeconds = CalendarBackoffPolicy.parseRetryAfterSeconds(
                        response.header("Retry-After"),
                        System.currentTimeMillis()
                    )
                )
            }
        }
    }

    override suspend fun listEvents(
        timeMin: Instant,
        timeMax: Instant
    ): List<MicrosoftCalendarEvent> = withContext(Dispatchers.IO) {
        listEventsInternal(timeMin, timeMax, allowRecovery = true)
    }

    private suspend fun listEventsInternal(
        timeMin: Instant,
        timeMax: Instant,
        allowRecovery: Boolean
    ): List<MicrosoftCalendarEvent> {
        val accessToken = authController.acquireAccessToken()
        val accountKey = authController.syncAccountKey ?: "single_account"
        val now = System.currentTimeMillis()
        val storedCursor = incrementalSyncStore.get(
            CalendarProvider.MICROSOFT_CALENDAR,
            accountKey,
            PRIMARY_CALENDAR_KEY
        )
        val useIncremental = CalendarIncrementalSyncPolicy.canReuse(storedCursor, now)
        CalendarSyncLogger.incrementalSync(
            provider = CalendarProvider.MICROSOFT_CALENDAR,
            used = useIncremental,
            fullSyncReason = if (useIncremental) null else if (storedCursor == null) {
                "delta_link_missing"
            } else {
                "sync_window_exhausted"
            }
        )
        val initialUrl = if (useIncremental) {
            storedCursor?.cursor.orEmpty()
        } else {
            buildString {
                append(MicrosoftCalendarConfig.GRAPH_BASE_URL)
                append("/me/calendarView/delta?startDateTime=")
                append(DateTimeFormatter.ISO_INSTANT.format(timeMin).encodeQueryValue())
                append("&endDateTime=")
                append(DateTimeFormatter.ISO_INSTANT.format(timeMax).encodeQueryValue())
            }
        }

        val events = mutableListOf<MicrosoftCalendarEvent>()
        var nextUrl: String? = initialUrl
        var deltaLink: String? = null
        while (nextUrl != null) {
            val request = Request.Builder()
                .url(nextUrl)
                .addBearerToken(accessToken)
                .addHeader(
                    "Prefer",
                    "outlook.timezone=\"UTC\", odata.maxpagesize=$DELTA_PAGE_SIZE"
                )
                .get()
                .build()
            CalendarSyncLogger.microsoftDeltaLifecycle("delta_request_started")
            val response = requestExecutor.execute(
                request,
                CalendarProvider.MICROSOFT_CALENDAR,
                "calendar_delta"
            )
            response.use {
                if (!response.isSuccessful) {
                    val error = response.readGraphErrorMetadata()
                    CalendarSyncLogger.apiStatus(
                        CalendarProvider.MICROSOFT_CALENDAR,
                        action = "calendar_delta",
                        statusCode = response.code
                    )
                    CalendarSyncLogger.microsoftGraphError(
                        statusCode = response.code,
                        graphErrorCode = error.code,
                        sanitizedMessage = error.sanitizedMessage,
                        requestId = error.requestId,
                        clientRequestId = error.clientRequestId
                    )
                    val invalidDeltaLink = response.code in setOf(400, 410) && useIncremental
                    if (invalidDeltaLink && allowRecovery) {
                        CalendarSyncLogger.microsoftDeltaLifecycle(
                            "delta_link_invalid_detected",
                            response.code
                        )
                        incrementalSyncStore.clear(
                            CalendarProvider.MICROSOFT_CALENDAR,
                            accountKey,
                            PRIMARY_CALENDAR_KEY
                        )
                        CalendarSyncLogger.microsoftDeltaLifecycle("delta_link_cleared")
                        CalendarSyncLogger.microsoftDeltaLifecycle("recovery_full_sync_started")
                        return try {
                            listEventsInternal(timeMin, timeMax, allowRecovery = false).also {
                                CalendarSyncLogger.microsoftDeltaLifecycle(
                                    "recovery_full_sync_succeeded"
                                )
                            }
                        } catch (recoveryError: Throwable) {
                            CalendarSyncLogger.microsoftDeltaLifecycle("recovery_full_sync_failed")
                            throw recoveryError
                        }
                    }
                    throw response.toGraphApiException(
                        error = error,
                        isDeltaLinkInvalid = invalidDeltaLink
                    )
                }
                CalendarSyncLogger.apiStatus(
                    CalendarProvider.MICROSOFT_CALENDAR,
                    action = "calendar_delta",
                    statusCode = response.code
                )
                val payload = JSONObject(response.body?.string().orEmpty())
                val values = payload.optJSONArray("value") ?: JSONArray()
                for (index in 0 until values.length()) {
                    values.optJSONObject(index)?.toMicrosoftCalendarEvent()?.let(events::add)
                }
                nextUrl = payload.optString("@odata.nextLink").takeIf { it.isNotBlank() }
                deltaLink = payload.optString("@odata.deltaLink").takeIf { it.isNotBlank() }
                    ?: deltaLink
            }
        }

        deltaLink?.let { cursor ->
            incrementalSyncStore.save(
                CalendarProvider.MICROSOFT_CALENDAR,
                accountKey,
                PRIMARY_CALENDAR_KEY,
                CalendarIncrementalCursor(
                    cursor = cursor,
                    rangeStartEpochMillis = storedCursor?.rangeStartEpochMillis
                        ?: timeMin.toEpochMilli(),
                    rangeEndEpochMillis = storedCursor?.rangeEndEpochMillis
                        ?: timeMax.toEpochMilli(),
                    fullSyncAtEpochMillis = storedCursor?.fullSyncAtEpochMillis ?: now
                )
            )
        }
        return events
    }

    private suspend fun createEvent(accessToken: String, reminder: Reminder): String {
        val payload = reminder.toGraphEventJson().put(
            "transactionId",
            CalendarIdempotencyKey.microsoftTransactionId(reminder.id)
        )
        val request = Request.Builder()
            .url("${MicrosoftCalendarConfig.GRAPH_BASE_URL}/me/events")
            .addBearerToken(accessToken)
            .post(payload.toString().toJsonBody())
            .build()
        return executeEventRequest(request, action = "create_event")
    }

    private suspend fun updateEvent(
        accessToken: String,
        eventId: String,
        reminder: Reminder
    ): String {
        val request = Request.Builder()
            .url("${MicrosoftCalendarConfig.GRAPH_BASE_URL}/me/events/${eventId.encodePathSegment()}")
            .addBearerToken(accessToken)
            .patch(reminder.toGraphEventJson().toString().toJsonBody())
            .build()
        executeEventRequest(request, action = "update_event")
        return eventId
    }

    private suspend fun executeEventRequest(request: Request, action: String): String {
        requestExecutor.execute(
            request,
            CalendarProvider.MICROSOFT_CALENDAR,
            action
        ).use { response ->
            if (!response.isSuccessful) {
                CalendarSyncLogger.apiError(
                    CalendarProvider.MICROSOFT_CALENDAR,
                    action = action,
                    statusCode = response.code
                )
                throw MicrosoftGraphApiException(
                    statusCode = response.code,
                    retryAfterSeconds = CalendarBackoffPolicy.parseRetryAfterSeconds(
                        response.header("Retry-After"),
                        System.currentTimeMillis()
                    )
                )
            }
            CalendarSyncLogger.apiStatus(
                CalendarProvider.MICROSOFT_CALENDAR,
                action = action,
                statusCode = response.code
            )
            return JSONObject(response.body?.string().orEmpty()).getString("id")
        }
    }

    private fun Reminder.toGraphEventJson(): JSONObject {
        val startInstant = Instant.ofEpochMilli(scheduledAtEpochMillis)
        val endInstant = startInstant.plus(DEFAULT_EVENT_DURATION)
        val subject = if (isCompleted) "Completado: $title" else title
        return JSONObject().apply {
            put("subject", subject)
            put(
                "body",
                JSONObject()
                    .put("contentType", "text")
                    .put("content", buildGraphDescription())
            )
            put("start", startInstant.toGraphDateTime())
            put("end", endInstant.toGraphDateTime())
            put("isAllDay", isAllDay)
        }
    }

    private fun Reminder.buildGraphDescription(): String {
        return buildString {
            append(detail)
            meetingUrl?.takeIf(MeetingUrlPolicy::isSupportedMeetingUrl)?.let { joinUrl ->
                append("\nUnirse a la reunión: ")
                append(joinUrl)
            }
            append("\n\nCreado desde Smart Reminder Assistant.")
            append("\nOrigen original: ")
            append(originProvider.name)
            append("\nSmartReminderId: ")
            append(id)
            if (isSuspended) {
                append("\n")
                append(CalendarSuspensionPolicy.SUSPENDED_DETAIL_NOTE)
            }
        }
    }

    private fun Instant.toGraphDateTime(): JSONObject {
        val utcDateTime = atZone(ZoneOffset.UTC).toLocalDateTime()
        return JSONObject()
            .put("dateTime", DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(utcDateTime))
            .put("timeZone", "UTC")
    }

    private fun JSONObject.toMicrosoftCalendarEvent(): MicrosoftCalendarEvent? {
        if (optBoolean("isCancelled")) return null
        val eventId = optString("id").takeIf { it.isNotBlank() } ?: return null
        val subject = optString("subject").ifBlank { "Evento de Microsoft Calendar" }
        val rawBody = optJSONObject("body")
            ?.optString("content")
            ?.takeIf { it.isNotBlank() }
        val bodyPreview = optString("bodyPreview")
        val metadataSource = listOfNotNull(rawBody, bodyPreview).joinToString("\n")
        val originProviderHint = ORIGIN_PROVIDER_PATTERN.find(metadataSource)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { stored -> CalendarProvider.entries.firstOrNull { it.name == stored } }
        val localIdHint = SMART_REMINDER_ID_PATTERN.find(metadataSource)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        val detail = MeetingContentSanitizer.cleanDescription(rawBody).ifBlank {
            MeetingContentSanitizer.cleanDescription(bodyPreview)
        }
        val startAt = optJSONObject("start")?.toEpochMillis() ?: return null
        val structuredJoinUrl = optJSONObject("onlineMeeting")
            ?.optString("joinUrl")
            ?.takeIf(MeetingUrlPolicy::isSupportedMeetingUrl)
        val legacyStructuredUrl = optString("onlineMeetingUrl")
            .takeIf(MeetingUrlPolicy::isSupportedMeetingUrl)
        val otherStructuredUrl = optJSONObject("onlineMeeting")
            ?.optString("joinWebUrl")
            ?.takeIf(MeetingUrlPolicy::isSupportedMeetingUrl)
            ?: optString("joinWebUrl").takeIf(MeetingUrlPolicy::isSupportedMeetingUrl)
        val htmlFallbackUrl = if (
            structuredJoinUrl == null && legacyStructuredUrl == null && otherStructuredUrl == null
        ) {
            MeetingContentSanitizer.extractSupportedMeetingUrl(rawBody)
                ?: MeetingContentSanitizer.extractSupportedMeetingUrl(bodyPreview)
        } else {
            null
        }
        val meetingUrl = structuredJoinUrl ?: legacyStructuredUrl ?: otherStructuredUrl
            ?: htmlFallbackUrl
        CalendarSyncLogger.meetingLinkSource(
            provider = CalendarProvider.MICROSOFT_CALENDAR,
            structured = structuredJoinUrl != null || legacyStructuredUrl != null ||
                    otherStructuredUrl != null,
            htmlFallback = htmlFallbackUrl != null,
            onlineMeeting = optBoolean("isOnlineMeeting") || optJSONObject("onlineMeeting") != null,
            providerNamePresent = optString("onlineMeetingProvider").isNotBlank()
        )
        val updatedAt = optString("lastModifiedDateTime")
            .takeIf { it.isNotBlank() }
            ?.let(::parseInstantMillis)

        return MicrosoftCalendarEvent(
            id = eventId,
            title = subject.removePrefix("Completado:").trim().ifBlank { subject },
            detail = detail,
            startAtEpochMillis = startAt,
            isAllDay = optBoolean("isAllDay"),
            isCompleted = subject.startsWith("Completado:", ignoreCase = true),
            isUrgent = subject.contains("urgente", ignoreCase = true) ||
                    detail.contains("urgente", ignoreCase = true),
            meetingUrl = meetingUrl,
            meetingProvider = MeetingUrlPolicy.providerForUrl(meetingUrl)
                ?: CalendarProvider.MICROSOFT_CALENDAR.takeIf {
                    optString("onlineMeetingProvider").contains("teams", ignoreCase = true)
                },
            isOnlineMeeting = optBoolean("isOnlineMeeting") || meetingUrl != null,
            originProviderHint = originProviderHint,
            isManagedCopy = metadataSource.contains("Creado desde Smart Reminder Assistant."),
            localIdHint = localIdHint,
            updatedAtEpochMillis = updatedAt
        )
    }

    private fun JSONObject.toEpochMillis(): Long? {
        val dateTime = optString("dateTime").takeIf { it.isNotBlank() } ?: return null
        return runCatching { OffsetDateTime.parse(dateTime).toInstant().toEpochMilli() }
            .recoverCatching {
                val timeZone = optString("timeZone").takeIf { it.isNotBlank() } ?: "UTC"
                LocalDateTime.parse(dateTime)
                    .atZone(runCatching { ZoneId.of(timeZone) }.getOrDefault(ZoneOffset.UTC))
                    .toInstant()
                    .toEpochMilli()
            }
            .getOrNull()
    }

    private fun parseInstantMillis(value: String): Long? {
        return runCatching { Instant.parse(value).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .getOrNull()
    }

    private fun Response.readGraphErrorMetadata(): MicrosoftGraphErrorMetadata {
        val payload = runCatching { JSONObject(body?.string().orEmpty()) }.getOrNull()
        val graphError = payload?.optJSONObject("error")
        val innerError = graphError?.optJSONObject("innerError")
            ?: graphError?.optJSONObject("innererror")
        return MicrosoftGraphErrorMetadata(
            code = graphError?.optString("code")?.toSafeIdentifier(),
            sanitizedMessage = graphError?.optString("message")?.sanitizeGraphMessage(),
            requestId = (header("request-id")
                ?: innerError?.optString("request-id"))?.toSafeIdentifier(),
            clientRequestId = (header("client-request-id")
                ?: innerError?.optString("client-request-id"))?.toSafeIdentifier()
        )
    }

    private fun Response.toGraphApiException(
        error: MicrosoftGraphErrorMetadata,
        isDeltaLinkInvalid: Boolean = false
    ): MicrosoftGraphApiException {
        return MicrosoftGraphApiException(
            statusCode = code,
            graphErrorCode = error.code,
            sanitizedMessage = error.sanitizedMessage,
            requestId = error.requestId,
            clientRequestId = error.clientRequestId,
            isDeltaLinkInvalid = isDeltaLinkInvalid,
            retryAfterSeconds = CalendarBackoffPolicy.parseRetryAfterSeconds(
                header("Retry-After"),
                System.currentTimeMillis()
            )
        )
    }

    private fun String?.toSafeIdentifier(): String? {
        return this?.trim()
            ?.takeIf { it.isNotBlank() && it.length <= SAFE_IDENTIFIER_MAX_LENGTH }
            ?.takeIf { value -> value.all { it.isLetterOrDigit() || it in "-_." } }
    }

    private fun String?.sanitizeGraphMessage(): String? {
        val raw = this?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return raw
            .replace(EMAIL_PATTERN, "<redacted-email>")
            .replace(URL_PATTERN, "<redacted-url>")
            .replace(TOKEN_PATTERN, "<redacted-token>")
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .take(SAFE_MESSAGE_MAX_LENGTH)
    }

    private fun Request.Builder.addBearerToken(accessToken: String): Request.Builder {
        return addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
    }

    private fun String.toJsonBody() = toRequestBody(JSON_MEDIA_TYPE)

    private fun String.encodePathSegment(): String {
        return URLEncoder.encode(this, "UTF-8").replace("+", "%20")
    }

    private fun String.encodeQueryValue(): String {
        return URLEncoder.encode(this, "UTF-8").replace("+", "%20")
    }

    companion object {
        private val ORIGIN_PROVIDER_PATTERN = Regex("Origen original: ([A-Z_]+)")
        private val SMART_REMINDER_ID_PATTERN = Regex("SmartReminderId: (\\d+)")
        private const val PRIMARY_CALENDAR_KEY = "primary"
        private const val DELTA_PAGE_SIZE = 250
        private const val SAFE_IDENTIFIER_MAX_LENGTH = 128
        private const val SAFE_MESSAGE_MAX_LENGTH = 160
        private val EMAIL_PATTERN = Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE)
        private val URL_PATTERN = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
        private val TOKEN_PATTERN = Regex(
            "(?i)(bearer|access[_ -]?token|refresh[_ -]?token)\\s*[:=]?\\s*[^\\s,;]+"
        )
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val DEFAULT_EVENT_DURATION: Duration = Duration.ofHours(1)

        fun create(
            context: Context,
            authController: MicrosoftCalendarAuthController
        ): MicrosoftGraphCalendarGateway {
            val client = OkHttpClient()
            return MicrosoftGraphCalendarGateway(
                authController = authController,
                client = client,
                requestExecutor = CalendarHttpRequestExecutor(
                    client,
                    CalendarApiUsageGuard.get(context)
                ),
                incrementalSyncStore = SharedPreferencesCalendarIncrementalSyncStore(context)
            )
        }
    }
}

class MicrosoftGraphApiException(
    val statusCode: Int,
    val graphErrorCode: String? = null,
    val sanitizedMessage: String? = null,
    val requestId: String? = null,
    val clientRequestId: String? = null,
    val isDeltaLinkInvalid: Boolean = false,
    val retryAfterSeconds: Long? = null
) : Exception("Microsoft Graph respondio con codigo HTTP $statusCode.")

private data class MicrosoftGraphErrorMetadata(
    val code: String?,
    val sanitizedMessage: String?,
    val requestId: String?,
    val clientRequestId: String?
)
