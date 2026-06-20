package com.luistureo.voicereminderapp.core.calendar.google

import android.content.Context
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarSyncLogger
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarHttpRequestExecutor
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarIncrementalCursor
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarIncrementalSyncPolicy
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarIncrementalSyncStore
import com.luistureo.voicereminderapp.core.calendar.unified.NoOpCalendarIncrementalSyncStore
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarApiUsageGuard
import com.luistureo.voicereminderapp.core.calendar.unified.SharedPreferencesCalendarIncrementalSyncStore
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarIdempotencyKey
import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.model.ReminderRecurrenceUnit
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarSuspensionPolicy
import com.luistureo.voicereminderapp.core.calendar.unified.MeetingContentSanitizer
import com.luistureo.voicereminderapp.core.calendar.unified.MeetingUrlPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class GoogleCalendarRestClient(
    private val client: OkHttpClient = OkHttpClient(),
    private val requestExecutor: CalendarHttpRequestExecutor = CalendarHttpRequestExecutor(client),
    private val incrementalSyncStore: CalendarIncrementalSyncStore =
        NoOpCalendarIncrementalSyncStore
) {

    suspend fun createReminderEvent(
        accessToken: String,
        reminder: Reminder
    ): String = withContext(Dispatchers.IO) {
        val eventId = CalendarIdempotencyKey.googleEventId(reminder.id)
        val request = Request.Builder()
            .url(buildEventsUrl())
            .addAuthHeaders(accessToken)
            .post(reminder.toEventJson().put("id", eventId).toString().toJsonBody())
            .build()

        executeEventRequest(
            request,
            action = "create_event",
            existingIdOnConflict = eventId
        )
    }

    suspend fun updateReminderEvent(
        accessToken: String,
        eventId: String,
        reminder: Reminder
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${buildEventsUrl()}/${eventId.encodePathSegment()}")
            .addAuthHeaders(accessToken)
            .patch(reminder.toEventJson().toString().toJsonBody())
            .build()

        executeEventRequest(request, action = "update_event")
    }

    suspend fun deleteReminderEvent(
        accessToken: String,
        eventId: String
    ): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${buildEventsUrl()}/${eventId.encodePathSegment()}")
            .addAuthHeaders(accessToken)
            .delete()
            .build()

        requestExecutor.execute(
            request,
            CalendarProvider.GOOGLE_CALENDAR,
            "delete_event"
        ).use { response ->
            when {
                response.isSuccessful -> {
                    CalendarSyncLogger.apiStatus(
                        CalendarProvider.GOOGLE_CALENDAR,
                        action = "delete_event",
                        statusCode = response.code
                    )
                    true
                }
                response.code == 404 || response.code == 410 -> {
                    CalendarSyncLogger.apiStatus(
                        CalendarProvider.GOOGLE_CALENDAR,
                        action = "delete_event_missing_remote",
                        statusCode = response.code
                    )
                    true
                }
                else -> {
                    CalendarSyncLogger.apiError(
                        CalendarProvider.GOOGLE_CALENDAR,
                        action = "delete_event",
                        statusCode = response.code
                    )
                    throw GoogleCalendarApiException(
                        code = response.code,
                        body = response.body?.string().orEmpty()
                    )
                }
            }
        }
    }

    suspend fun listEvents(
        accessToken: String,
        timeMin: Instant,
        timeMax: Instant,
        accountKey: String = "single_account"
    ): List<GoogleCalendarEvent> = withContext(Dispatchers.IO) {
        listEventsInternal(accessToken, timeMin, timeMax, accountKey, allowRecovery = true)
    }

    private suspend fun listEventsInternal(
        accessToken: String,
        timeMin: Instant,
        timeMax: Instant,
        accountKey: String,
        allowRecovery: Boolean
    ): List<GoogleCalendarEvent> {
        val now = System.currentTimeMillis()
        val storedCursor = incrementalSyncStore.get(
            CalendarProvider.GOOGLE_CALENDAR,
            accountKey,
            GoogleCalendarConfig.CALENDAR_ID
        )
        val useIncremental = CalendarIncrementalSyncPolicy.canReuse(storedCursor, now)
        CalendarSyncLogger.incrementalSync(
            provider = CalendarProvider.GOOGLE_CALENDAR,
            used = useIncremental,
            fullSyncReason = if (useIncremental) null else if (storedCursor == null) {
                "sync_token_missing"
            } else {
                "sync_window_exhausted"
            }
        )

        val events = mutableListOf<GoogleCalendarEvent>()
        var pageToken: String? = null
        var nextSyncToken: String? = null
        do {
            val urlBuilder = buildEventsUrl().toHttpUrl().newBuilder()
                .addQueryParameter("singleEvents", "true")
                .addQueryParameter("showDeleted", "true")
                .addQueryParameter("maxResults", "250")
            if (useIncremental) {
                urlBuilder.addQueryParameter("syncToken", storedCursor?.cursor)
            } else {
                urlBuilder
                    .addQueryParameter("orderBy", "startTime")
                    .addQueryParameter("timeMin", DateTimeFormatter.ISO_INSTANT.format(timeMin))
                    .addQueryParameter("timeMax", DateTimeFormatter.ISO_INSTANT.format(timeMax))
            }
            pageToken?.let { urlBuilder.addQueryParameter("pageToken", it) }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .addAuthHeaders(accessToken)
                .get()
                .build()
            val response = requestExecutor.execute(
                request,
                CalendarProvider.GOOGLE_CALENDAR,
                "list_events"
            )
            response.use {
                if (response.code == 410 && useIncremental && allowRecovery) {
                    incrementalSyncStore.clear(
                        CalendarProvider.GOOGLE_CALENDAR,
                        accountKey,
                        GoogleCalendarConfig.CALENDAR_ID
                    )
                    CalendarSyncLogger.incrementalTokenRefreshed(
                        CalendarProvider.GOOGLE_CALENDAR,
                        "sync_token_invalid"
                    )
                    return listEventsInternal(
                        accessToken,
                        timeMin,
                        timeMax,
                        accountKey,
                        allowRecovery = false
                    )
                }
                if (!response.isSuccessful) {
                    CalendarSyncLogger.apiError(
                        CalendarProvider.GOOGLE_CALENDAR,
                        action = "list_events",
                        statusCode = response.code
                    )
                    throw GoogleCalendarApiException(
                        code = response.code,
                        body = response.body?.string().orEmpty()
                    )
                }
                CalendarSyncLogger.apiStatus(
                    CalendarProvider.GOOGLE_CALENDAR,
                    action = "list_events",
                    statusCode = response.code
                )
                val payload = JSONObject(response.body?.string().orEmpty())
                val items = payload.optJSONArray("items") ?: JSONArray()
                for (index in 0 until items.length()) {
                    items.optJSONObject(index)?.toGoogleCalendarEvent()?.let(events::add)
                }
                pageToken = payload.optString("nextPageToken").takeIf { it.isNotBlank() }
                nextSyncToken = payload.optString("nextSyncToken").takeIf { it.isNotBlank() }
                    ?: nextSyncToken
            }
        } while (pageToken != null)

        nextSyncToken?.let { token ->
            incrementalSyncStore.save(
                CalendarProvider.GOOGLE_CALENDAR,
                accountKey,
                GoogleCalendarConfig.CALENDAR_ID,
                CalendarIncrementalCursor(
                    cursor = token,
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

    private suspend fun executeEventRequest(
        request: Request,
        action: String,
        existingIdOnConflict: String? = null
    ): String {
        requestExecutor.execute(
            request,
            CalendarProvider.GOOGLE_CALENDAR,
            action
        ).use { response ->
            if (response.code == 409 && existingIdOnConflict != null) {
                CalendarSyncLogger.apiStatus(
                    CalendarProvider.GOOGLE_CALENDAR,
                    action = "create_event_idempotent_conflict",
                    statusCode = response.code
                )
                return existingIdOnConflict
            }
            if (!response.isSuccessful) {
                CalendarSyncLogger.apiError(
                    CalendarProvider.GOOGLE_CALENDAR,
                    action = action,
                    statusCode = response.code
                )
                throw GoogleCalendarApiException(
                    code = response.code,
                    body = response.body?.string().orEmpty()
                )
            }
            CalendarSyncLogger.apiStatus(
                CalendarProvider.GOOGLE_CALENDAR,
                action = action,
                statusCode = response.code
            )

            val responseBody = response.body?.string().orEmpty()
            return JSONObject(responseBody).getString("id")
        }
    }

    private fun Reminder.toEventJson(): JSONObject {
        val zoneId = ZoneId.systemDefault()
        val startAt = Instant.ofEpochMilli(scheduledAtEpochMillis).atZone(zoneId)
        val endAt = startAt.plus(DEFAULT_EVENT_DURATION)
        val statusLabel = if (isCompleted) "Completado" else "Pendiente"

        return JSONObject().apply {
            put("summary", if (isCompleted) "Completado: $title" else title)
            put("description", buildDescription(statusLabel))
            put(
                "start",
                JSONObject()
                    .put("dateTime", startAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                    .put("timeZone", zoneId.id)
            )
            put(
                "end",
                JSONObject()
                    .put("dateTime", endAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                    .put("timeZone", zoneId.id)
            )
            recurrence?.toGoogleRRule()?.let { rrule ->
                put("recurrence", org.json.JSONArray().put(rrule))
            }
            put(
                "extendedProperties",
                JSONObject().put(
                    "private",
                    JSONObject()
                        .put("smartReminderId", id.toString())
                        .put("source", source.name)
                        .put("originProvider", originProvider.name)
                        .put("isCompleted", isCompleted.toString())
                        .put("isUrgent", isUrgent.toString())
                        .put("isSuspended", isSuspended.toString())
                        .put("syncVersion", "1")
                )
            )
        }
    }

    private fun Reminder.buildDescription(statusLabel: String): String {
        return buildString {
            append(detail)
            meetingUrl?.takeIf(MeetingUrlPolicy::isSupportedMeetingUrl)?.let { joinUrl ->
                append("\nUnirse a la reunión: ")
                append(joinUrl)
            }
            append("\n\n")
            append("Estado: ")
            append(statusLabel)
            append("\nUrgente: ")
            append(if (isUrgent) "Si" else "No")
            recurrenceLabel?.let { label ->
                append("\nRepeticion: ")
                append(label)
            }
            append("\n\nCreado desde Smart Reminder Assistant.")
            if (isSuspended) {
                append("\n")
                append(CalendarSuspensionPolicy.SUSPENDED_DETAIL_NOTE)
            }
        }
    }

    private fun com.luistureo.voicereminderapp.domain.model.ReminderRecurrence.toGoogleRRule(): String? {
        if (!isActive) return null

        val frequency = when (unit) {
            ReminderRecurrenceUnit.DAY -> "DAILY"
            ReminderRecurrenceUnit.WEEK -> "WEEKLY"
            ReminderRecurrenceUnit.MONTH -> "MONTHLY"
            ReminderRecurrenceUnit.YEAR -> "YEARLY"
        }

        val parts = mutableListOf(
            "FREQ=$frequency",
            "INTERVAL=$normalizedInterval"
        )

        if (unit == ReminderRecurrenceUnit.WEEK && weekdays.isNotEmpty()) {
            val byDay = weekdays
                .sortedBy { it.dayOfWeek.value }
                .joinToString(separator = ",") { weekday ->
                    weekday.name.take(2).uppercase(Locale.US)
                }
            parts += "BYDAY=$byDay"
        }

        return "RRULE:${parts.joinToString(separator = ";")}"
    }

    private fun buildEventsUrl(): String {
        return "https://www.googleapis.com/calendar/v3/calendars/${GoogleCalendarConfig.CALENDAR_ID.encodePathSegment()}/events"
    }

    private fun Request.Builder.addAuthHeaders(accessToken: String): Request.Builder {
        return addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
    }

    private fun JSONObject.toGoogleCalendarEvent(): GoogleCalendarEvent? {
        if (optString("status") == "cancelled") return null

        val id = optString("id").takeIf { it.isNotBlank() } ?: return null
        val title = optString("summary").takeIf { it.isNotBlank() } ?: "Evento sin titulo"
        val rawDescription = optString("description")
        val description = MeetingContentSanitizer.cleanDescription(rawDescription)
        val start = optJSONObject("start") ?: return null
        val end = optJSONObject("end")
        val privateProperties = optJSONObject("extendedProperties")
            ?.optJSONObject("private")
        val originProviderHint = privateProperties?.optString("originProvider")
            ?.let { stored -> CalendarProvider.entries.firstOrNull { it.name == stored } }
        val localIdHint = privateProperties?.optString("smartReminderId")?.toIntOrNull()
        val meetingUrl = extractMeetingUrl(this, rawDescription)
        val updatedAtEpochMillis = optString("updated")
            .takeIf { it.isNotBlank() }
            ?.let(::parseInstantMillis)

        val startDateTime = start.optString("dateTime")
            .takeIf { it.isNotBlank() }
            ?.let(::parseZonedDateTime)
        val endDateTime = end?.optString("dateTime")
            ?.takeIf { it.isNotBlank() }
            ?.let(::parseZonedDateTime)
        val startDate = start.optString("date")
            .takeIf { it.isNotBlank() }
            ?.let(::parseLocalDate)
        val endDate = end?.optString("date")
            ?.takeIf { it.isNotBlank() }
            ?.let(::parseLocalDate)
        val isCompleted = privateProperties?.optString("isCompleted") == "true" ||
                title.startsWith("Completado:", ignoreCase = true)
        val isUrgent = privateProperties?.optString("isUrgent") == "true" ||
                title.contains("urgente", ignoreCase = true) ||
                description.contains("urgente", ignoreCase = true)

        return GoogleCalendarEvent(
            id = id,
            title = title.removePrefix("Completado:").trim().ifBlank { title },
            description = description,
            startDateTime = startDateTime,
            endDateTime = endDateTime,
            startDate = startDate,
            endDate = endDate,
            isAllDay = startDate != null,
            isCompleted = isCompleted,
            isUrgent = isUrgent,
            meetingUrl = meetingUrl,
            meetingProvider = MeetingUrlPolicy.providerForUrl(meetingUrl)
                ?: CalendarProvider.GOOGLE_CALENDAR.takeIf {
                    optJSONObject("conferenceData") != null
                },
            isOnlineMeeting = meetingUrl != null || optJSONObject("conferenceData") != null,
            originProviderHint = originProviderHint,
            isManagedCopy = localIdHint != null,
            localIdHint = localIdHint,
            updatedAtEpochMillis = updatedAtEpochMillis
        )
    }

    private fun extractMeetingUrl(eventJson: JSONObject, description: String): String? {
        val entryPoints = eventJson.optJSONObject("conferenceData")
            ?.optJSONArray("entryPoints")
        if (entryPoints != null) {
            for (index in 0 until entryPoints.length()) {
                val entryPoint = entryPoints.optJSONObject(index) ?: continue
                val uri = entryPoint.optString("uri")
                if ((entryPoint.optString("entryPointType") == "video" ||
                        entryPoint.optString("entryPointType").isBlank()) &&
                    MeetingUrlPolicy.isSupportedMeetingUrl(uri)
                ) {
                    CalendarSyncLogger.meetingLinkSource(
                        CalendarProvider.GOOGLE_CALENDAR,
                        structured = true,
                        htmlFallback = false
                    )
                    return uri.trim()
                }
            }
        }
        val hangoutLink = eventJson.optString("hangoutLink")
            .takeIf(MeetingUrlPolicy::isSupportedMeetingUrl)
        if (hangoutLink != null) {
            CalendarSyncLogger.meetingLinkSource(
                CalendarProvider.GOOGLE_CALENDAR,
                structured = true,
                htmlFallback = false
            )
            return hangoutLink.trim()
        }
        val fallback = MeetingContentSanitizer.extractSupportedMeetingUrl(description)
            ?: MeetingUrlPolicy.extractFirstSupportedUrl(eventJson.optString("location"))
        CalendarSyncLogger.meetingLinkSource(
            CalendarProvider.GOOGLE_CALENDAR,
            structured = false,
            htmlFallback = fallback != null
        )
        return fallback
    }

    private fun parseZonedDateTime(value: String): ZonedDateTime? {
        return runCatching { ZonedDateTime.parse(value) }.getOrNull()
    }

    private fun parseLocalDate(value: String): LocalDate? {
        return runCatching { LocalDate.parse(value) }.getOrNull()
    }

    private fun parseInstantMillis(value: String): Long? {
        return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
    }

    private fun String.toJsonBody() = toRequestBody("application/json".toMediaType())

    private fun String.encodePathSegment(): String {
        return URLEncoder.encode(this, "UTF-8").replace("+", "%20")
    }

    companion object {
        val DEFAULT_EVENT_DURATION: Duration = Duration.ofMinutes(30)

        fun create(context: Context): GoogleCalendarRestClient {
            val client = OkHttpClient()
            return GoogleCalendarRestClient(
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

class GoogleCalendarApiException(
    val code: Int,
    val body: String
) : Exception("Google Calendar respondio con codigo HTTP $code")
