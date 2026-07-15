package com.luistureo.voicereminderapp.presentation.calendar

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarRefactorResourceTest {

    @Test
    fun homeUsesAvailableHeightWithOnlyActiveModulesAndSupportingPanel() {
        val layout = sourceFile("app/src/main/res/layout/activity_main.xml").readText()

        assertTrue(layout.contains("@+id/cardCalendar"))
        assertTrue(layout.contains("@+id/cardAssistantReminder"))
        assertTrue(layout.contains("@+id/panelHomeSupport"))
        assertTrue(layout.contains("android:fillViewport=\"true\""))
        assertTrue(layout.contains("app:layout_constraintWidth_max=\"840dp\""))
        assertTrue(layout.contains("@string/home_calendar_action"))
        assertTrue(layout.contains("@string/home_virtual_assistant_action"))
        assertFalse(layout.contains("cardLoan"))
        assertFalse(layout.contains("cardDailyRoutines"))
        assertEquals(2, layout.split("@+id/card").size - 1)
    }

    @Test
    fun calendarFiltersAreBeforeNavigationAndRemovedSectionsAreAbsent() {
        val layout = sourceFile("app/src/main/res/layout/activity_calendar.xml").readText()
        val strings = sourceFile("app/src/main/res/values/strings.xml").readText()

        assertTrue(
            layout.indexOf("@layout/view_calendar_filters") <
                layout.indexOf("@+id/containerCalendarControls")
        )
        assertTrue(
            layout.indexOf("@+id/containerCalendarControls") <
                layout.indexOf("@+id/gridCalendarDays")
        )
        assertTrue(
            layout.indexOf("@+id/gridCalendarDays") <
                layout.indexOf("@+id/cardCalendarSelectedDate")
        )
        assertFalse(layout.contains("cardCalendarNextDaySummary"))
        assertFalse(layout.contains("recyclerCalendarUpcomingReminders"))
        assertFalse(layout.contains("calendarReminderEmptyStateCard"))
        assertFalse(strings.contains("calendar_next_day_summary"))
        assertFalse(strings.contains("calendar_upcoming_empty"))
    }

    @Test
    fun assistantHasNoCalendarProviderUiOrProviderInitialization() {
        val layout = sourceFile("app/src/main/res/layout/activity_assistant.xml").readText()
        val activity = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/assistant/" +
                "AssistantActivity.kt"
        ).readText()
        val viewModel = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/viewmodel/" +
                "ReminderViewModel.kt"
        ).readText()
        val assistantSave = viewModel.substringAfter("private suspend fun saveAssistantDraft")
            .substringBefore("private fun Reminder.toFormState")

        assertFalse(layout.contains("containerAssistantCalendarSyncOptions"))
        assertFalse(layout.contains("checkAssistantSyncGoogle"))
        assertFalse(layout.contains("checkAssistantSyncMicrosoft"))
        assertFalse(activity.contains("GoogleCalendar"))
        assertFalse(activity.contains("MicrosoftCalendar"))
        assertFalse(activity.contains("UnifiedCalendarSynchronizer"))
        assertTrue(assistantSave.contains("syncTargetProviders = emptySet()"))
        assertTrue(assistantSave.contains("syncReminderSchedule(savedReminder)"))
        assertFalse(assistantSave.contains("syncSavedReminder"))
    }

    @Test
    fun retiredSummaryAndNearbyImplementationsAreRemovedWithSafeCleanup() {
        val cleanup = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/data/migration/" +
                "RemovedModuleCleanup.kt"
        ).readText()
        val manifest = sourceFile("app/src/main/AndroidManifest.xml").readText()
        val calendarState = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/calendar/" +
                "CalendarUiState.kt"
        ).readText()

        assertFalse(sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/core/alarm/" +
                "NextDaySummaryReceiver.kt"
        ).exists())
        assertFalse(sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/state/" +
                "UpcomingReminderListItem.kt"
        ).exists())
        assertFalse(manifest.contains(".core.alarm.NextDaySummaryReceiver"))
        assertFalse(calendarState.contains("upcomingReminderItems"))
        assertTrue(cleanup.contains("cancelNextDaySummaryArtifacts"))
        assertTrue(cleanup.contains("next_day_summary_channel"))
        assertTrue(cleanup.contains("next_day_summary_preferences"))
    }

    @Test
    fun sundayTreatmentKeepsSemanticAndVisualPriority() {
        val layout = sourceFile("app/src/main/res/layout/activity_calendar.xml").readText()
        val activity = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/calendar/" +
                "CalendarActivity.kt"
        ).readText()
        val viewModel = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/calendar/" +
                "CalendarViewModel.kt"
        ).readText()

        assertTrue(layout.contains("@+id/tvCalendarWeekdaySunday"))
        assertTrue(layout.contains("@color/calendar_sunday_text"))
        assertTrue(viewModel.contains("currentDate.dayOfWeek == DayOfWeek.SUNDAY"))
        assertTrue(activity.contains("R.color.calendar_sunday_outline"))
        assertTrue(activity.contains("R.dimen.calendar_sunday_stroke"))
        assertTrue(activity.contains("R.string.calendar_day_sunday"))
        assertTrue(activity.indexOf("day.isSelected -> R.color.calendar_toolbar_text") <
            activity.indexOf("day.isSunday -> R.color.calendar_sunday_text"))
    }

    @Test
    fun removedScreensRemainSafeHomeAliasesAndCalendarConnectionsRemain() {
        val manifest = sourceFile("app/src/main/AndroidManifest.xml").readText()
        val calendarActivity = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/calendar/" +
                "CalendarActivity.kt"
        ).readText()

        assertEquals(11, "<activity-alias".toRegex().findAll(manifest).count())
        assertEquals(
            11,
            "android:targetActivity=\".MainActivity\"".toRegex().findAll(manifest).count()
        )
        assertTrue(calendarActivity.contains("GoogleCalendarAuthManager"))
        assertTrue(calendarActivity.contains("MicrosoftCalendarAuthProvider"))
        assertTrue(calendarActivity.contains("UnifiedCalendarSynchronizer"))
    }

    private fun sourceFile(projectPath: String): File {
        val fromRoot = File(projectPath)
        if (fromRoot.exists()) return fromRoot
        return File(projectPath.removePrefix("app/"))
    }
}
