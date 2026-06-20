package com.luistureo.voicereminderapp.core.calendar.google

import com.google.android.gms.auth.GoogleAuthException
import java.io.IOException
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleCalendarConnectionPolicyTest {

    @Test
    fun validExistingSessionCanBeReusedAfterReactivation() {
        assertEquals(
            GoogleCalendarReconnectDecision.REUSE_VALID_SESSION,
            GoogleCalendarConnectionPolicy.reconnectDecision(
                hasAccount = true,
                hasCalendarPermission = true,
                hasStableAccountId = true
            )
        )
    }

    @Test
    fun staleOrIncompleteSessionAlwaysNeedsLogin() {
        listOf(
            Triple(false, false, false),
            Triple(true, false, true),
            Triple(true, true, false)
        ).forEach { (hasAccount, hasPermission, hasStableId) ->
            assertEquals(
                GoogleCalendarReconnectDecision.NEEDS_LOGIN,
                GoogleCalendarConnectionPolicy.reconnectDecision(
                    hasAccount = hasAccount,
                    hasCalendarPermission = hasPermission,
                    hasStableAccountId = hasStableId
                )
            )
        }
    }

    @Test
    fun knownGoogleAuthFailuresMapToSafeReasons() {
        assertEquals(
            GoogleCalendarAuthFailureReason.INTERNAL_ERROR,
            GoogleCalendarAuthFailureReason.from(GoogleAuthException("INTERNAL_ERROR"))
        )
        assertEquals(
            GoogleCalendarAuthFailureReason.BAD_AUTHENTICATION,
            GoogleCalendarAuthFailureReason.from(GoogleAuthException("BAD_AUTHENTICATION"))
        )
        assertEquals(
            GoogleCalendarAuthFailureReason.MISSING_GAIA_ID,
            GoogleCalendarAuthFailureReason.from(GoogleAuthException("Missing gaiaId"))
        )
        assertEquals(
            GoogleCalendarAuthFailureReason.TOKEN_FAILURE,
            GoogleCalendarAuthFailureReason.from(
                GoogleAuthException("Failed to get auth token")
            )
        )
        assertEquals(
            GoogleCalendarAuthFailureReason.AUTO_MANAGE_ERROR,
            GoogleCalendarAuthFailureReason.from(
                GoogleAuthException("AutoManageHelper unresolved error")
            )
        )
        assertEquals(
            GoogleCalendarAuthFailureReason.NETWORK,
            GoogleCalendarAuthFailureReason.from(IOException("network unavailable"))
        )
    }

    @Test
    fun connectionStatePersistsEnabledPendingAndHashedAccountReference() {
        val source = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/core/calendar/google/" +
                    "GoogleCalendarConnectionStateStore.kt"
        ).readText()

        assertTrue(source.contains("google_calendar_connection_state"))
        assertTrue(source.contains("KEY_SYNC_ENABLED"))
        assertTrue(source.contains("KEY_CONNECTED"))
        assertTrue(source.contains("KEY_HAS_ERROR"))
        assertTrue(source.contains("KEY_ERROR_CODE"))
        assertTrue(source.contains("KEY_AUTH_PENDING"))
        assertTrue(source.contains("KEY_ACCOUNT_KEY"))
        assertFalse(source.contains("KEY_EMAIL"))
    }

    private fun sourceFile(projectPath: String): File {
        val fromRoot = File(projectPath)
        if (fromRoot.exists()) return fromRoot
        return File(projectPath.removePrefix("app/"))
    }
}
