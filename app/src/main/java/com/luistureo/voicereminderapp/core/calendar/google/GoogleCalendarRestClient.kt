package com.luistureo.voicereminderapp.core.calendar.google

import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.model.ReminderRecurrenceUnit
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
    private val client: OkHttpClient = OkHttpClient()
) {

    suspend fun createReminderEvent(
        accessToken: String,
        reminder: Reminder
    ): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(buildEventsUrl())
            .addAuthHeaders(accessToken)
            .post(reminder.toEventJson().toString().toJsonBody())
            .build()

        executeEventRequest(request)
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

        executeEventRequest(request)
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

        client.newCall(request).execute().use { response ->
            when {
                response.isSuccessful -> true
                response.code == 404 || response.code == 410 -> true
                else -> throw GoogleCalendarApiException(
                    code = response.code,
                    body = response.body?.string().orEmpty()
                )
            }
        }
    }

    suspend fun listEvents(
        accessToken: String,
        timeMin: Instant,
        timeMax: Instant
    ): List<GoogleCalendarEvent> = withContext(Dispatchers.IO) {
        val url = buildEventsUrl().toHttpUrl().newBuilder()
            .addQueryParameter("singleEvents", "true")
            .addQueryParameter("orderBy", "startTime")
            .addQueryParameter("showDeleted", "false")
            .addQueryParameter("maxResults", "2500")
            .addQueryParameter("timeMin", DateTimeFormatter.ISO_INSTANT.format(timeMin))
            .addQueryParameter("timeMax", DateTimeFormatter.ISO_INSTANT.format(timeMax))
            .build()

        val request = Request.Builder()
            .url(url)
            .addAuthHeaders(accessToken)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw GoogleCalendarApiException(
                    code = response.code,
                    body = response.body?.string().orEmpty()
                )
            }

            val responseBody = response.body?.string().orEmpty()
            val items = JSONObject(responseBody).optJSONArray("items") ?: JSONArray()

            (0 until items.length()).mapNotNull { index ->
                items.optJSONObject(index)?.toGoogleCalendarEvent()
            }
        }
    }

    private fun executeEventRequest(request: Request): String {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw GoogleCalendarApiException(
                    code = response.code,
                    body = response.body?.string().orEmpty()
                )
            }

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
                        .put("isCompleted", isCompleted.toString())
                        .put("isUrgent", isUrgent.toString())
                        .put("syncVersion", "1")
                )
            )
        }
    }

    private fun Reminder.buildDescription(statusLabel: String): String {
        return buildString {
            append(detail)
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
        val description = optString("description")
        val start = optJSONObject("start") ?: return null
        val end = optJSONObject("end")
        val privateProperties = optJSONObject("extendedProperties")
            ?.optJSONObject("private")

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
            isUrgent = isUrgent
        )
    }

    private fun parseZonedDateTime(value: String): ZonedDateTime? {
        return runCatching { ZonedDateTime.parse(value) }.getOrNull()
    }

    private fun parseLocalDate(value: String): LocalDate? {
        return runCatching { LocalDate.parse(value) }.getOrNull()
    }

    private fun String.toJsonBody() = toRequestBody("application/json".toMediaType())

    private fun String.encodePathSegment(): String {
        return URLEncoder.encode(this, "UTF-8").replace("+", "%20")
    }

    private companion object {
        val DEFAULT_EVENT_DURATION: Duration = Duration.ofMinutes(30)
    }
}

class GoogleCalendarApiException(
    val code: Int,
    val body: String
) : Exception("Google Calendar respondio $code: $body")
