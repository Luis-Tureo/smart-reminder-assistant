package com.luistureo.voicereminderapp.core.calendar.microsoft

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MicrosoftCalendarErrorCodeTest {
    @Test
    fun graphStatusesMapToExactCodes() {
        assertEquals(
            MicrosoftCalendarErrorCode.DELTA_LINK_INVALID,
            MicrosoftCalendarErrorCode.fromFailure(
                MicrosoftGraphApiException(400, isDeltaLinkInvalid = true)
            )
        )
        assertEquals(
            MicrosoftCalendarErrorCode.GRAPH_400_BAD_REQUEST,
            MicrosoftCalendarErrorCode.fromFailure(MicrosoftGraphApiException(400))
        )
        assertEquals(
            MicrosoftCalendarErrorCode.GRAPH_401,
            MicrosoftCalendarErrorCode.fromFailure(MicrosoftGraphApiException(401))
        )
        assertEquals(
            MicrosoftCalendarErrorCode.GRAPH_403,
            MicrosoftCalendarErrorCode.fromFailure(MicrosoftGraphApiException(403))
        )
        assertEquals(
            MicrosoftCalendarErrorCode.GRAPH_404,
            MicrosoftCalendarErrorCode.fromFailure(MicrosoftGraphApiException(404))
        )
        assertEquals(
            MicrosoftCalendarErrorCode.GRAPH_429,
            MicrosoftCalendarErrorCode.fromFailure(MicrosoftGraphApiException(429))
        )
        assertEquals(
            MicrosoftCalendarErrorCode.GRAPH_5XX,
            MicrosoftCalendarErrorCode.fromFailure(MicrosoftGraphApiException(503))
        )
        assertFalse(
            MicrosoftCalendarErrorCode.fromFailure(MicrosoftGraphApiException(400)) ==
                    MicrosoftCalendarErrorCode.SYNC_IO_EXCEPTION
        )
    }

    @Test
    fun networkAndMsalFailuresMapToExactCodes() {
        assertEquals(
            MicrosoftCalendarErrorCode.SYNC_IO_EXCEPTION,
            MicrosoftCalendarErrorCode.fromFailure(IOException("offline"))
        )
        assertEquals(
            MicrosoftCalendarErrorCode.AUTH_NETWORK_IO,
            MicrosoftCalendarErrorCode.fromAuthFailure(IOException("offline"))
        )
        assertEquals(
            MicrosoftCalendarErrorCode.AUTH_SCOPE_DENIED,
            MicrosoftCalendarErrorCode.fromFailure(
                IllegalStateException("authorization_declined scope")
            )
        )
        assertEquals(
            MicrosoftCalendarErrorCode.AUTH_MSAL_SERVICE_ERROR,
            MicrosoftCalendarErrorCode.fromFailure(
                IllegalStateException("MsalServiceException")
            )
        )
        assertEquals(
            MicrosoftCalendarErrorCode.AUTH_MSAL_CLIENT_ERROR,
            MicrosoftCalendarErrorCode.fromFailure(
                IllegalStateException("MsalClientException")
            )
        )
    }
}
