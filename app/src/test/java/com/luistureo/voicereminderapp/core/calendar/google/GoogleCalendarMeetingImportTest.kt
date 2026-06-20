package com.luistureo.voicereminderapp.core.calendar.google

import com.luistureo.voicereminderapp.core.calendar.unified.CalendarHttpRequestExecutor
import java.time.Instant
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import com.luistureo.voicereminderapp.domain.model.CalendarProvider

class GoogleCalendarMeetingImportTest {
    @Test
    fun conferenceDataVideoUrlHasPriorityOverHangoutAndHtml() = runBlocking {
        val event = importSingle(
            """{"items":[{"id":"g1","summary":"Meet","description":"<div>Agenda</div><a href='https://meet.google.com/html-link'>Join</a>","start":{"dateTime":"2026-06-22T15:00:00Z"},"end":{"dateTime":"2026-06-22T16:00:00Z"},"conferenceData":{"entryPoints":[{"entryPointType":"phone","uri":"tel:+123"},{"entryPointType":"video","uri":"https://meet.google.com/conference-link"}]},"hangoutLink":"https://meet.google.com/hangout-link"}],"nextSyncToken":"fresh"}"""
        )

        assertEquals("https://meet.google.com/conference-link", event.meetingUrl)
        assertEquals("Agenda", event.description)
        assertFalse(event.description.contains("<div>"))
    }

    @Test
    fun hangoutLinkIsUsedWhenConferenceDataIsAbsent() = runBlocking {
        val event = importSingle(
            """{"items":[{"id":"g2","summary":"Meet","description":"Normal","start":{"dateTime":"2026-06-22T15:00:00Z"},"hangoutLink":"https://meet.google.com/hangout-link"}],"nextSyncToken":"fresh"}"""
        )
        assertEquals("https://meet.google.com/hangout-link", event.meetingUrl)
    }

    @Test
    fun managedGoogleCopyCarriesPermanentMicrosoftOriginHint() = runBlocking {
        val event = importSingle(
            """{"items":[{"id":"g3","summary":"Teams copy","description":"Agenda","start":{"dateTime":"2026-06-22T15:00:00Z"},"extendedProperties":{"private":{"smartReminderId":"42","originProvider":"MICROSOFT_CALENDAR"}}}],"nextSyncToken":"fresh"}"""
        )

        assertEquals(CalendarProvider.MICROSOFT_CALENDAR, event.originProviderHint)
        assertEquals(42, event.localIdHint)
        assertEquals(true, event.isManagedCopy)
    }

    private suspend fun importSingle(body: String): GoogleCalendarEvent {
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody())
                .build()
        }).build()
        return GoogleCalendarRestClient(client, CalendarHttpRequestExecutor(client))
            .listEvents("token", Instant.EPOCH, Instant.EPOCH.plusSeconds(3600))
            .single()
    }
}
