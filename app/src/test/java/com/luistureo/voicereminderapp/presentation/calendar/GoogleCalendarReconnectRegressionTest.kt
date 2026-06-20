package com.luistureo.voicereminderapp.presentation.calendar

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleCalendarReconnectRegressionTest {

    @Test
    fun connectLaunchesAuthWhilePausedSessionIsVerifiedBeforeReuse() {
        val source = activitySource()
        val start = source.substringAfter("private fun startGoogleCalendarAuth")
            .substringBefore("private fun startMicrosoftCalendarAuth")

        assertTrue(start.contains("CalendarProviderUiState.CONNECTING"))
        assertTrue(start.contains("launchGoogleCalendarAuth"))
        assertTrue(start.contains("verifyReusedGoogleSession"))
        assertTrue(start.contains("googleCalendarSignInLauncher.launch"))
        assertTrue(start.contains("authLaunched"))
    }

    @Test
    fun authResultSuccessPersistsConnectionUpdatesUiThenStartsSync() {
        val source = activitySource()
        val result = source.substringAfter("private fun completeGoogleSignIn")
            .substringBefore("private fun verifyReusedGoogleSession")

        val complete = result.indexOf("completeSignIn")
        val connected = result.indexOf("CalendarProviderUiState.CONNECTED")
        val refresh = result.indexOf("refreshGoogleCalendarStatus", connected)
        val sync = result.indexOf("syncGoogleCalendarChanges", refresh)
        assertTrue(complete >= 0)
        assertTrue(connected > complete)
        assertTrue(refresh > connected)
        assertTrue(sync > refresh)
    }

    @Test
    fun authCancelAndFailureShowInlineErrorsWithoutCrash() {
        val source = activitySource()
        val result = source.substringAfter("private val googleCalendarSignInLauncher")
            .substringBefore("override fun onCreate") +
                source.substringAfter("private fun completeGoogleSignIn")
                    .substringBefore("private fun verifyReusedGoogleSession")

        assertTrue(result.contains("AUTH_CANCELLED"))
        assertTrue(result.contains("showGoogleInlineError"))
        assertTrue(result.contains("runCatching"))
        assertTrue(result.contains("onFailure"))
        assertFalse(result.contains("Toast.makeText"))
        assertFalse(result.contains("Snackbar"))
    }

    @Test
    fun deactivatePreservesSessionAndReconnectClearsOnlyGoogleState() {
        val manager = managerSource()
        val pause = manager.substringAfter("fun pauseSync")
            .substringBefore("fun prepareReconnect")
        val reconnect = manager.substringAfter("fun prepareReconnect")
            .substringBefore("suspend fun completeSignIn")
        val stale = manager.substringAfter("private fun clearStaleLocalState")
            .substringBefore("sealed class GoogleCalendarAuthException")

        assertFalse(pause.contains("signOut"))
        assertFalse(pause.contains("connected = false"))
        assertFalse(pause.contains("incrementalSyncStore.clearProviderAccount"))
        assertFalse(reconnect.contains("syncEnabled = true"))
        assertTrue(reconnect.contains("REUSE_VALID_SESSION"))
        assertTrue(reconnect.contains("NEEDS_LOGIN"))
        assertTrue(stale.contains("clearProviderAccount"))
        assertTrue(stale.contains("accountKey = null"))
        assertFalse(stale.contains("delete"))
    }

    @Test
    fun disconnectedOrPausedStartupDoesNotRequestTokenAndTransientErrorStaysEnabled() {
        val source = activitySource()
        val onResume = source.substringAfter("override fun onResume")
            .substringBefore("private fun initViews")
        val manager = managerSource()
        val connected = manager.substringAfter("fun isConnected")
            .substringBefore("fun isPaused")

        assertFalse(onResume.contains("syncCalendarChanges()"))
        assertFalse(onResume.contains("startGoogleCalendarAuth"))
        assertFalse(onResume.contains("getAccessToken"))
        assertTrue(connected.contains("connectionState.syncEnabled"))
        assertTrue(connected.contains("connectionState.connected"))
        assertFalse(connected.contains("!connectionState.hasError"))
    }

    private fun activitySource(): String = sourceFile(
        "app/src/main/java/com/luistureo/voicereminderapp/presentation/calendar/CalendarActivity.kt"
    ).readText()

    private fun managerSource(): String = sourceFile(
        "app/src/main/java/com/luistureo/voicereminderapp/core/calendar/google/GoogleCalendarAuthManager.kt"
    ).readText()

    private fun sourceFile(projectPath: String): File {
        val fromRoot = File(projectPath)
        if (fromRoot.exists()) return fromRoot
        return File(projectPath.removePrefix("app/"))
    }
}
