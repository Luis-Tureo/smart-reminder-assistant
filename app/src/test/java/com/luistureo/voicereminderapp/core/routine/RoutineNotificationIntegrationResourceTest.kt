package com.luistureo.voicereminderapp.core.routine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RoutineNotificationIntegrationResourceTest {
    @Test
    fun manifestRegistersOnlyRequiredRoutineComponentsAndPermission() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android.permission.RECEIVE_BOOT_COMPLETED"))
        assertTrue(manifest.contains(".core.routine.RoutineAlarmReceiver"))
        assertTrue(manifest.contains(".core.routine.RoutineActionReceiver"))
        assertTrue(manifest.contains(".core.routine.RoutineRescheduleReceiver"))
        assertTrue(manifest.contains("android.intent.action.BOOT_COMPLETED"))
        assertTrue(manifest.contains("android.intent.action.TIMEZONE_CHANGED"))
        assertTrue(manifest.contains(".presentation.routine.RoutinePostponeActivity"))
        assertTrue(manifest.contains("android:showWhenLocked=\"true\""))
        assertFalse(manifest.contains("android.permission.USE_FULL_SCREEN_INTENT"))
        assertFalse(manifest.contains("android.permission.WAKE_LOCK"))
    }

    @Test
    fun sharedNotificationHelperProvidesActionsAndPrivateLockScreenVersion() {
        val source = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/core/notification/NotificationHelper.kt"
        ).readText()

        assertTrue(source.contains("showRoutineNotification"))
        assertTrue(source.contains("VISIBILITY_PRIVATE"))
        assertTrue(source.contains("setPublicVersion"))
        assertTrue(source.contains("RoutineActionReceiver::class.java"))
        assertTrue(source.contains("RoutinePostponeActivity::class.java"))
        assertTrue(source.contains("RoutineDetailActivity::class.java"))
        assertTrue(source.contains("buildRoutineContentIntent"))
        assertTrue(source.contains("PendingIntent.getBroadcast"))
        assertTrue(source.contains("action == RoutineNotificationAction.START && startWithAssistant"))
        assertTrue(source.contains("AssistantActivity::class.java"))
        assertFalse(source.contains("RoutineDetailActivity.EXTRA_NOTIFICATION_ACTION"))
        assertTrue(source.contains("cancelRoutineNotifications"))
        assertTrue(source.contains("setDeleteIntent"))
        assertTrue(source.contains("RoutineExecutionAction.NOT_COMPLETED"))
    }

    @Test
    fun schedulerUsesAlarmManagerWithoutRoutineWorker() {
        val scheduler = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/core/routine/RoutineScheduler.kt"
        ).readText()
        val routineFiles = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/core/routine"
        ).walkTopDown().filter { it.isFile }.joinToString("\n") { it.readText() }

        assertTrue(scheduler.contains("setExactAndAllowWhileIdle"))
        assertTrue(scheduler.contains("setAndAllowWhileIdle"))
        assertTrue(scheduler.contains("FLAG_IMMUTABLE"))
        assertTrue(scheduler.contains("cancelRoutine"))
        assertFalse(routineFiles.contains("WorkManager"))
    }

    @Test
    fun savingAndCompletingRoutineResynchronizesAndCancelsNotifications() {
        val viewModel = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/routine/viewmodel/RoutineViewModel.kt"
        ).readText()
        val receiver = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/core/routine/RoutineActionReceiver.kt"
        ).readText()

        assertTrue(viewModel.contains("replaceRoutineSchedule"))
        assertTrue(viewModel.contains("cancelRoutineNotifications"))
        assertTrue(
            viewModel.indexOf("notifications.cancelRoutineNotifications(routineId)") <
                viewModel.indexOf("scheduler.replaceRoutineSchedule(savedRoutine, state)")
        )
        assertTrue(receiver.contains("ApplyRoutineExecutionActionUseCase"))
        assertTrue(receiver.contains("cancelRoutineNotifications"))
        assertTrue(receiver.contains("dateEpochDay != today.toEpochDay()"))
    }

    @Test
    fun reusedDetailActivityReloadsTheRoutineFromTheNewIntent() {
        val detail = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/routine/RoutineDetailActivity.kt"
        ).readText()

        assertTrue(detail.contains("routineId = newRoutineId"))
        assertTrue(detail.contains("viewModel.loadRoutine(routineId)"))
    }

    @Test
    fun postponeActivitySupportsShowingOverLockScreenBeforeApi27() {
        val postpone = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/routine/RoutinePostponeActivity.kt"
        ).readText()

        assertTrue(postpone.contains("setShowWhenLocked(true)"))
        assertTrue(postpone.contains("FLAG_SHOW_WHEN_LOCKED"))
    }

    private fun projectFile(path: String): File {
        val fromRoot = File(path)
        if (fromRoot.exists()) return fromRoot
        return File(path.removePrefix("app/"))
    }
}
