package com.luistureo.voicereminderapp.core.calendar.unified

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarAutoSyncWiringTest {
    @Test
    fun appOpenAndPeriodicSyncUseWorkManagerWithNetworkConstraint() {
        val application = source("app/src/main/java/com/luistureo/voicereminderapp/SmartReminderApplication.kt")
        val worker = workerSource()

        assertTrue(application.contains("schedulePeriodic"))
        assertTrue(application.contains("enqueueNow"))
        assertTrue(application.contains("app_open"))
        assertTrue(worker.contains("PERIODIC_INTERVAL_MINUTES"))
        assertTrue(worker.contains("NetworkType.CONNECTED"))
        assertTrue(worker.contains("ExistingPeriodicWorkPolicy.KEEP"))
        assertTrue(worker.contains("syncGoogle"))
        assertTrue(worker.contains("syncMicrosoft"))
    }

    @Test
    fun workerIsInvisibleAndHandlesProviderFailuresWithoutCrashing() {
        val worker = workerSource()
        val calendarActivity = source(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/calendar/" +
                    "CalendarActivity.kt"
        )

        assertTrue(worker.contains("runCatching"))
        assertTrue(worker.contains("Result.success()"))
        assertFalse(worker.contains("MaterialButton"))
        assertFalse(calendarActivity.contains("calendar_sync_button_syncing"))
        assertFalse(calendarActivity.contains("Sincronizando"))
    }

    @Test
    fun workerChecksProviderStateBeforeCallingSynchronizers() {
        val worker = workerSource()
        val google = worker.substringAfter("private suspend fun syncGoogle")
            .substringBefore("private suspend fun syncMicrosoft")
        val microsoft = worker.substringAfter("private suspend fun syncMicrosoft")
            .substringBefore("private suspend fun MicrosoftCalendarAuthController")

        assertTrue(google.indexOf("CalendarAutoSyncPolicy.decision") < google.indexOf("importEvents"))
        assertTrue(microsoft.indexOf("CalendarAutoSyncPolicy.decision") < microsoft.indexOf("syncMicrosoftCalendar"))
    }

    @Test
    fun localCreateUpdateAndDeleteKeepImmediateProviderSync() {
        val viewModel = source(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/viewmodel/" +
                    "ReminderViewModel.kt"
        )

        assertTrue(viewModel.contains("unifiedCalendarSynchronizer.syncSavedReminder"))
        assertTrue(viewModel.contains("unifiedCalendarSynchronizer.deleteReminderEvent"))
    }

    private fun workerSource() = source(
        "app/src/main/java/com/luistureo/voicereminderapp/core/calendar/unified/" +
                "CalendarAutoSyncWorker.kt"
    )

    private fun source(path: String): String {
        val fromRoot = File(path)
        return if (fromRoot.exists()) fromRoot.readText() else File(path.removePrefix("app/")).readText()
    }
}
