package com.luistureo.voicereminderapp.core.calendar.google

import com.google.android.gms.auth.GoogleAuthException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleCalendarErrorCodeTest {

    @Test
    fun authFailuresKeepSpecificStableCodes() {
        assertEquals(
            GoogleCalendarErrorCode.AUTH_MISSING_GAIA_ID,
            GoogleCalendarErrorCode.fromSignInFailure(GoogleAuthException("Missing gaiaId"))
        )
        assertEquals(
            GoogleCalendarErrorCode.AUTH_BAD_AUTHENTICATION,
            GoogleCalendarErrorCode.fromSignInFailure(
                GoogleAuthException("BAD_AUTHENTICATION")
            )
        )
        assertEquals(
            GoogleCalendarErrorCode.AUTH_INTERNAL_ERROR,
            GoogleCalendarErrorCode.fromSignInFailure(GoogleAuthException("INTERNAL_ERROR"))
        )
        assertEquals(
            GoogleCalendarErrorCode.AUTH_NETWORK_IO,
            GoogleCalendarErrorCode.fromSignInFailure(IOException("offline"))
        )
    }

    @Test
    fun calendarApiStatusMapsWithoutExposingResponseBody() {
        assertEquals(
            GoogleCalendarErrorCode.CALENDAR_API_401,
            GoogleCalendarErrorCode.fromSyncFailure(GoogleCalendarApiException(401, "private"))
        )
        assertEquals(
            GoogleCalendarErrorCode.CALENDAR_API_403,
            GoogleCalendarErrorCode.fromSyncFailure(GoogleCalendarApiException(403, "private"))
        )
        assertEquals(
            GoogleCalendarErrorCode.CALENDAR_API_429,
            GoogleCalendarErrorCode.fromSyncFailure(GoogleCalendarApiException(429, "private"))
        )
        assertEquals(
            GoogleCalendarErrorCode.CALENDAR_API_5XX,
            GoogleCalendarErrorCode.fromSyncFailure(GoogleCalendarApiException(503, "private"))
        )
        val exception = GoogleCalendarApiException(403, "private event detail")
        assertEquals(false, exception.message.orEmpty().contains("private event detail"))
    }

    @Test
    fun syncIoUsesSyncSpecificCode() {
        assertEquals(
            GoogleCalendarErrorCode.SYNC_IO_EXCEPTION,
            GoogleCalendarErrorCode.fromSyncFailure(IOException("timeout"))
        )
    }
}
