package com.luistureo.voicereminderapp.core.calendar.microsoft

import android.app.Activity
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarHttpRequestExecutor
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarIncrementalCursor
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarIncrementalSyncStore
import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import java.time.Instant
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MicrosoftGraphCalendarGatewayTest {
    @Test
    fun structuredJoinUrlHasPriorityAndHtmlBodyIsCleaned() = runBlocking {
        val client = clientReturning(
            200,
            """{"value":[{"id":"event-1","subject":"Teams","body":{"contentType":"html","content":"<html><head><style>.x{color:red}</style></head><body><div>Agenda breve</div><div>Microsoft Teams Meeting</div><a href='https://teams.microsoft.com/l/meetup-join/html'>Join</a></body></html>"},"bodyPreview":"Preview","start":{"dateTime":"2026-06-22T15:00:00","timeZone":"UTC"},"onlineMeeting":{"joinUrl":"https://teams.microsoft.com/l/meetup-join/structured"},"onlineMeetingUrl":"https://teams.microsoft.com/l/meetup-join/legacy","isOnlineMeeting":true,"onlineMeetingProvider":"teamsForBusiness"}],"@odata.deltaLink":"fresh"}"""
        )
        val gateway = MicrosoftGraphCalendarGateway(
            RecordingAuthController(), client, CalendarHttpRequestExecutor(client)
        )

        val event = gateway.listEvents(Instant.EPOCH, Instant.EPOCH.plusSeconds(3600)).single()

        assertEquals("https://teams.microsoft.com/l/meetup-join/structured", event.meetingUrl)
        assertEquals("Agenda breve", event.detail)
        assertFalse(event.detail.contains("<html>"))
    }

    @Test
    fun teamsUrlFallsBackToHtmlBodyWhenStructuredFieldsAreMissing() = runBlocking {
        val client = clientReturning(
            200,
            """{"value":[{"id":"event-2","subject":"Teams","body":{"contentType":"html","content":"<div>Agenda</div><a href='https://teams.microsoft.com/l/meetup-join/from-html'>Unirse</a>"},"start":{"dateTime":"2026-06-22T15:00:00","timeZone":"UTC"},"isOnlineMeeting":true,"onlineMeetingProvider":"teamsForBusiness"}],"@odata.deltaLink":"fresh"}"""
        )
        val gateway = MicrosoftGraphCalendarGateway(
            RecordingAuthController(), client, CalendarHttpRequestExecutor(client)
        )

        val event = gateway.listEvents(Instant.EPOCH, Instant.EPOCH.plusSeconds(3600)).single()

        assertEquals("https://teams.microsoft.com/l/meetup-join/from-html", event.meetingUrl)
        assertFalse(event.detail.contains("teams.microsoft.com"))
    }

    @Test
    fun teamsLiveStructuredUrlAndManagedOriginMetadataAreMapped() = runBlocking {
        val client = clientReturning(
            200,
            """{"value":[{"id":"event-live","subject":"Teams personal","body":{"contentType":"text","content":"Agenda\n\nCreado desde Smart Reminder Assistant.\nOrigen original: GOOGLE_CALENDAR\nSmartReminderId: 12"},"start":{"dateTime":"2026-06-22T15:00:00","timeZone":"UTC"},"onlineMeeting":{"joinUrl":"https://teams.live.com/meet/abc"},"isOnlineMeeting":true,"onlineMeetingProvider":"teamsForBusiness"}],"@odata.deltaLink":"fresh"}"""
        )
        val gateway = MicrosoftGraphCalendarGateway(
            RecordingAuthController(), client, CalendarHttpRequestExecutor(client)
        )

        val event = gateway.listEvents(Instant.EPOCH, Instant.EPOCH.plusSeconds(3600)).single()

        assertEquals("https://teams.live.com/meet/abc", event.meetingUrl)
        assertEquals(CalendarProvider.GOOGLE_CALENDAR, event.originProviderHint)
        assertEquals(12, event.localIdHint)
        assertTrue(event.isManagedCopy)
    }

    @Test
    fun invalidDeltaLinkClearsOnlyCursorAndRunsOneFullRecovery() = runBlocking {
        val store = RecordingCursorStore(
            CalendarIncrementalCursor(
                cursor = "https://graph.microsoft.com/v1.0/me/calendarView/delta?%24deltatoken=expired",
                rangeStartEpochMillis = 0L,
                rangeEndEpochMillis = Long.MAX_VALUE,
                fullSyncAtEpochMillis = 1L
            )
        )
        val responses = ArrayDeque<ResponseSpec>().apply {
            add(
                ResponseSpec(
                    400,
                    """{"error":{"code":"syncStateNotFound","message":"Cursor expired for private@example.com https://teams.microsoft.com/l/private","innerError":{"request-id":"request-123","client-request-id":"client-456"}}}"""
                )
            )
            add(
                ResponseSpec(
                    200,
                    """{"value":[{"id":"event-1","subject":"Teams","body":{"content":"Safe"},"start":{"dateTime":"2026-06-22T15:00:00","timeZone":"UTC"},"end":{"dateTime":"2026-06-22T16:00:00","timeZone":"UTC"},"onlineMeeting":{"joinUrl":"https://teams.microsoft.com/l/meetup-join/private"},"lastModifiedDateTime":"2026-06-19T12:00:00Z"}],"@odata.deltaLink":"https://graph.microsoft.com/v1.0/me/calendarView/delta?%24deltatoken=fresh"}"""
                )
            )
        }
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            requests += chain.request()
            val spec = responses.removeFirst()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(spec.code)
                .message(spec.code.toString())
                .body(spec.body.toResponseBody())
                .build()
        }).build()
        val auth = RecordingAuthController()
        val gateway = MicrosoftGraphCalendarGateway(
            authController = auth,
            client = client,
            requestExecutor = CalendarHttpRequestExecutor(client),
            incrementalSyncStore = store
        )

        val events = gateway.listEvents(
            Instant.parse("2026-06-01T00:00:00Z"),
            Instant.parse("2026-07-01T00:00:00Z")
        )

        assertEquals(2, requests.size)
        assertEquals(1, store.clearCount)
        assertNotNull(store.cursor)
        assertTrue(store.cursor!!.cursor.contains("fresh"))
        assertTrue(auth.isConnected)
        assertTrue(auth.isSyncEnabled)
        assertEquals(0, auth.signOutCount)
        assertEquals(0, auth.pauseCount)
        assertEquals("event-1", events.single().id)
        assertTrue(events.single().meetingUrl!!.startsWith("https://teams.microsoft.com/"))
        val recoveryUrl = requests[1].url.toString()
        assertTrue(recoveryUrl.contains("startDateTime="))
        assertTrue(recoveryUrl.contains("endDateTime="))
        assertFalse(recoveryUrl.contains("%24select", ignoreCase = true))
        assertFalse(recoveryUrl.contains("%24top", ignoreCase = true))
    }

    @Test
    fun fullDelta400PreservesSafeMetadataAndMapsToBadRequest() = runBlocking {
        val privateEmail = "private@example.com"
        val privateTeamsUrl = "https://teams.microsoft.com/l/meetup-join/private"
        val client = clientReturning(
            400,
            """{"error":{"code":"BadRequest","message":"Invalid $privateEmail $privateTeamsUrl"}}"""
        )
        val gateway = MicrosoftGraphCalendarGateway(
            authController = RecordingAuthController(),
            client = client,
            requestExecutor = CalendarHttpRequestExecutor(client)
        )

        val error = runCatching {
            gateway.listEvents(Instant.EPOCH, Instant.EPOCH.plusSeconds(3_600))
        }.exceptionOrNull() as MicrosoftGraphApiException

        assertEquals(
            MicrosoftCalendarErrorCode.GRAPH_400_BAD_REQUEST,
            MicrosoftCalendarErrorCode.fromFailure(error)
        )
        assertEquals("BadRequest", error.graphErrorCode)
        assertFalse(error.sanitizedMessage.orEmpty().contains(privateEmail))
        assertFalse(error.sanitizedMessage.orEmpty().contains(privateTeamsUrl))
        assertTrue(error.sanitizedMessage.orEmpty().contains("<redacted-email>"))
        assertTrue(error.sanitizedMessage.orEmpty().contains("<redacted-url>"))
    }

    private fun clientReturning(code: Int, body: String): OkHttpClient {
        return OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(code.toString())
                .body(body.toResponseBody())
                .build()
        }).build()
    }
}

private data class ResponseSpec(val code: Int, val body: String)

private class RecordingCursorStore(
    var cursor: CalendarIncrementalCursor?
) : CalendarIncrementalSyncStore {
    var clearCount = 0

    override fun get(
        provider: CalendarProvider,
        accountKey: String,
        calendarKey: String
    ): CalendarIncrementalCursor? = cursor

    override fun save(
        provider: CalendarProvider,
        accountKey: String,
        calendarKey: String,
        cursor: CalendarIncrementalCursor
    ) {
        this.cursor = cursor
    }

    override fun clear(provider: CalendarProvider, accountKey: String, calendarKey: String) {
        clearCount++
        cursor = null
    }
}

private class RecordingAuthController : MicrosoftCalendarAuthController {
    override val isAuthConfigured = true
    override val isConnected = true
    override val hasSession = true
    override val isSyncEnabled = true
    override val lastErrorCode: MicrosoftCalendarErrorCode? = null
    override val syncAccountKey = "account-key"
    var signOutCount = 0
    var pauseCount = 0

    override fun refreshConnectionState(onComplete: (Boolean) -> Unit) = onComplete(true)
    override fun signIn(activity: Activity, onResult: (MicrosoftCalendarAuthResult) -> Unit) = Unit
    override fun signOut(onComplete: (Result<Unit>) -> Unit) {
        signOutCount++
        onComplete(Result.success(Unit))
    }
    override fun pauseSync() {
        pauseCount++
    }
    override fun activateExistingSession(onResult: (MicrosoftCalendarAuthResult) -> Unit) = Unit
    override fun markConnectionError(code: MicrosoftCalendarErrorCode) = Unit
    override fun clearConnectionError() = Unit
    override suspend fun acquireAccessToken(): String = "access-token-not-logged"
}
