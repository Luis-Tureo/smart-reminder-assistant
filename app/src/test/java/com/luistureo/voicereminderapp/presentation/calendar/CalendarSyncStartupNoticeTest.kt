package com.luistureo.voicereminderapp.presentation.calendar

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class CalendarSyncStartupNoticeTest {

    @Test
    fun calendarSyncAndAuthFlowsDoNotShowTransientNotices() {
        val source = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/calendar/" +
                    "CalendarActivity.kt"
        ).readText()
        val authBlock = source.substringAfter("private val googleCalendarSignInLauncher")
            .substringBefore("override fun onCreate")
        val syncBlock = source.substringAfter("private fun syncGoogleCalendarChanges")
            .substringBefore("private fun buildFilterTitle")

        assertFalse(authBlock.contains("Toast.makeText"))
        assertFalse(syncBlock.contains("Toast.makeText"))
        assertFalse(syncBlock.contains("Snackbar"))
        assertFalse(syncBlock.contains("AlertDialog"))
        assertFalse(source.contains("CalendarSyncSessionNoticePolicy"))
    }

    @Test
    fun homeDoesNotOwnCalendarSyncFlow() {
        val source = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/MainActivity.kt"
        ).readText()
        assertFalse(source.contains("syncPendingCalendarChanges"))
        assertFalse(source.contains("CalendarSyncCoordinator"))
        assertFalse(source.contains("CalendarSyncSessionNoticePolicy"))
    }

    private fun sourceFile(projectPath: String): File {
        val fromRoot = File(projectPath)
        if (fromRoot.exists()) return fromRoot
        return File(projectPath.removePrefix("app/"))
    }
}
