package com.luistureo.voicereminderapp.presentation.calendar

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarProviderDeactivationUiTest {

    @Test
    fun pauseDoesNotLogoutAndDisconnectRequiresConfirmation() {
        val source = activitySource()
        val confirmation = source.substringAfter("private fun showDisconnectConfirmation")
            .substringBefore("private fun pauseCalendarProvider")
        val pause = source.substringAfter("private fun pauseCalendarProvider")
            .substringBefore("private fun handleProviderPauseResult")

        assertTrue(confirmation.contains("AlertDialog.Builder"))
        assertTrue(confirmation.contains("calendar_sync_disconnect_confirm"))
        assertTrue(pause.contains("googleCalendarAuthManager.pauseSync"))
        assertTrue(pause.contains("microsoftCalendarAuthController.pauseSync"))
        assertFalse(pause.contains("signOut"))
        assertFalse(pause.contains("delete"))

        val result = source.substringAfter("private fun handleProviderPauseResult")
            .substringBefore("private fun disconnectCalendarProvider")
        assertTrue(result.contains("CalendarProviderUiState.PAUSED"))
        assertTrue(result.contains("calendarSyncInlineNoticeView.isVisible = true"))
        val disconnect = source.substringAfter("private fun disconnectCalendarProvider")
            .substringBefore("private fun activateMicrosoftCalendar")
        assertTrue(disconnect.contains("googleCalendarAuthManager.disconnect"))
        assertTrue(disconnect.contains("microsoftCalendarAuthController.signOut"))
        assertFalse(disconnect.contains("deleteReminder"))
    }

    @Test
    fun googleReconnectEnablesOnlyAfterVerifiedReuseOrLogin() {
        val activity = activitySource()
        val manager = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/core/calendar/google/" +
                    "GoogleCalendarAuthManager.kt"
        ).readText()
        val reconnect = manager.substringAfter("fun prepareReconnect")
            .substringBefore("fun completeSignIn")

        assertFalse(reconnect.contains("syncEnabled = true"))
        assertTrue(reconnect.contains("REUSE_VALID_SESSION"))
        assertTrue(reconnect.contains("NEEDS_LOGIN"))
        assertTrue(manager.substringAfter("private fun saveConnectedState")
            .contains("syncEnabled = true"))
        assertTrue(activity.contains("reactivateExistingSession"))
        assertTrue(activity.contains("googleCalendarAuthManager.prepareReconnect"))
        assertTrue(activity.contains("googleCalendarSignInLauncher.launch"))
    }

    @Test
    fun disconnectedStartupDoesNotLaunchAuthOrRequestToken() {
        val source = activitySource()
        val onResume = source.substringAfter("override fun onResume")
            .substringBefore("private fun initViews")

        assertFalse(onResume.contains("syncCalendarChanges()"))
        assertFalse(onResume.contains("startGoogleCalendarAuth"))
        assertFalse(onResume.contains("getAccessToken"))
    }

    @Test
    fun visibleMonthFailureUsesInlineErrorAndKeepsCachedLoad() {
        val source = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/calendar/" +
                    "CalendarViewModel.kt"
        ).readText()
        val reload = source.substringAfter("fun reloadCalendar")
            .substringBefore("fun goToPreviousMonth")

        assertTrue(reload.contains("externalSyncResult.exceptionOrNull"))
        assertTrue(reload.contains("loadCachedReminders"))
        assertTrue(reload.contains("CalendarSyncInlineError"))
        assertTrue(source.contains("GoogleCalendarErrorCode.fromSyncFailure"))
        val visibleSync = source.substringAfter("private suspend fun syncExternalCalendarsForVisibleMonth")
            .substringBefore("private fun List<Reminder>.toCachedCalendarEntries")
        assertTrue(visibleSync.contains("buildVisibleDateRange(visibleMonth)"))
        assertTrue(visibleSync.contains("timeMax = timeMax"))
    }

    @Test
    fun syncAndAuthFailuresUseInlineErrorWithoutTransientMessages() {
        val source = activitySource()
        val inlineError = source.substringAfter("private fun showInlineSyncError")
            .substringBefore("private fun renderInlineSyncError")
        val syncFlows = source.substringAfter("private fun syncGoogleCalendarChanges")
            .substringBefore("private fun buildFilterTitle")

        assertTrue(inlineError.contains("CalendarProviderUiState.ERROR"))
        assertTrue(inlineError.contains("inlineErrorShown"))
        assertTrue(syncFlows.contains("showGoogleInlineError"))
        assertFalse(syncFlows.contains("Toast.makeText"))
        assertFalse(syncFlows.contains("Snackbar"))
    }

    @Test
    fun errorBoxHasNoRetryAndMainButtonKeepsProviderAction() {
        val source = activitySource()
        assertFalse(source.contains("calendarSyncRetryButton"))
        assertFalse(source.contains("retryFailedCalendarProvider"))
        assertTrue(source.contains("calendar_sync_google_activate"))
        assertTrue(source.contains("calendar_sync_google_pause"))
        assertTrue(source.contains("calendar_sync_google_connect"))
    }

    private fun activitySource(): String {
        return sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/calendar/" +
                    "CalendarActivity.kt"
        ).readText()
    }

    private fun sourceFile(projectPath: String): File {
        val fromRoot = File(projectPath)
        if (fromRoot.exists()) return fromRoot
        return File(projectPath.removePrefix("app/"))
    }
}
