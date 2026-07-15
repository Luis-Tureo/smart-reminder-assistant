package com.luistureo.voicereminderapp.presentation.calendar

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarRefactorResourceTest {

    @Test
    fun homeContainsOnlyActiveModuleCardsAndNoMovedReminderSections() {
        val layout = sourceFile("app/src/main/res/layout/activity_main.xml").readText()

        assertTrue(layout.contains("@+id/cardCalendar"))
        assertTrue(layout.contains("@+id/cardAssistantReminder"))
        assertFalse(layout.contains("cardLoan"))
        assertFalse(layout.contains("cardDailyRoutines"))
        assertFalse(layout.contains("cardCalendarNextDaySummary"))
        assertFalse(layout.contains("calendarReminderEmptyStateCard"))
        assertEquals(2, layout.split("@+id/card").size - 1)
    }

    @Test
    fun calendarUsesRequestedSectionOrderAndMovedActions() {
        val layout = sourceFile("app/src/main/res/layout/activity_calendar.xml").readText()
        val emptyState = sourceFile(
            "app/src/main/res/layout/view_calendar_reminder_empty_state.xml"
        ).readText()
        val orderedMarkers = listOf(
            "@+id/toolbarCalendar",
            "@+id/containerCalendarControls",
            "@+id/gridCalendarDays",
            "@+id/cardCalendarSelectedDate",
            "@+id/btnCalendarCreateReminder",
            "@+id/tvCalendarRemindersHeading",
            "@+id/cardCalendarNextDaySummary",
            "@+id/calendarReminderEmptyStateCard",
            "@+id/recyclerCalendarUpcomingReminders"
        )

        assertTrue(orderedMarkers.zipWithNext().all { (first, second) ->
            layout.indexOf(first) in 0 until layout.indexOf(second)
        })
        assertTrue(layout.contains("@string/calendar_next_day_summary_change_time"))
        assertTrue(emptyState.contains("@string/calendar_upcoming_empty_action"))
    }

    @Test
    fun movedActionsKeepBehaviorWithoutDuplicateSummaryScheduling() {
        val calendarActivity = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/calendar/CalendarActivity.kt"
        ).readText()
        val mainActivity = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/MainActivity.kt"
        ).readText()

        assertTrue(calendarActivity.contains("nextDaySummaryTimeButton.setOnClickListener"))
        assertTrue(calendarActivity.contains("upcomingEmptyStateButton.setOnClickListener"))
        assertTrue(calendarActivity.contains("summaryPreferenceStore.setSummaryTime"))
        assertTrue(calendarActivity.contains("showCreateReminderChoice()"))
        assertEquals(1, "scheduleNextDaySummary()".toRegex().findAll(calendarActivity).count())
        assertFalse(mainActivity.contains("scheduleNextDaySummary"))
    }

    @Test
    fun removedScreensAreReplacedOnlyBySafeHomeAliases() {
        val manifest = sourceFile("app/src/main/AndroidManifest.xml").readText()
        val removedActivityFolders = listOf(
            sourceFile("app/src/main/java/com/luistureo/voicereminderapp/presentation/loan"),
            sourceFile("app/src/main/java/com/luistureo/voicereminderapp/presentation/routine")
        )

        assertTrue(removedActivityFolders.none(File::exists))
        assertEquals(11, "<activity-alias".toRegex().findAll(manifest).count())
        assertEquals(
            11,
            "android:targetActivity=\".MainActivity\"".toRegex().findAll(manifest).count()
        )
        assertFalse(manifest.contains("<receiver\n            android:name=\".core.loan"))
        assertFalse(manifest.contains("<receiver\n            android:name=\".core.routine"))
    }

    @Test
    fun retiredSchedulesAreCleanedWithoutChangingGeneralReminderScheduling() {
        val cleanup = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/data/migration/" +
                "RemovedModuleCleanup.kt"
        ).readText()
        val notificationHelper = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/core/notification/" +
                "NotificationHelper.kt"
        ).readText()
        val scheduleCoordinator = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/core/alarm/" +
                "AppScheduleCoordinator.kt"
        ).readText()

        assertTrue(cleanup.contains("cancelLoanArtifacts"))
        assertTrue(cleanup.contains("cancelRoutineArtifacts"))
        assertTrue(cleanup.contains("deleteNotificationChannel"))
        assertFalse(notificationHelper.contains("routineChannelId"))
        assertFalse(notificationHelper.contains("loan_reminder_channel"))
        assertTrue(scheduleCoordinator.contains("syncReminders"))
        assertFalse(scheduleCoordinator.contains("syncLoans"))
        assertFalse(scheduleCoordinator.contains("syncRoutines"))
    }

    @Test
    fun upcomingReminderStateHasSingleCalendarOwner() {
        val calendarState = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/calendar/" +
                "CalendarUiState.kt"
        ).readText()
        val homeState = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/state/" +
                "ReminderUiState.kt"
        ).readText()

        assertTrue(calendarState.contains("upcomingReminderItems"))
        assertFalse(homeState.contains("homeTimelineItems"))
    }

    @Test
    fun calendarKeepsAccessibleTouchTargetsAndLogicalFocusOrder() {
        val layout = sourceFile("app/src/main/res/layout/activity_calendar.xml").readText()
        val emptyState = sourceFile(
            "app/src/main/res/layout/view_calendar_reminder_empty_state.xml"
        ).readText()
        val reminderItem = sourceFile("app/src/main/res/layout/item_reminder.xml").readText()

        assertTrue(layout.contains("app:navigationContentDescription=\"@string/calendar_back\""))
        assertTrue(
            sourceFile(
                "app/src/main/java/com/luistureo/voicereminderapp/presentation/calendar/" +
                    "CalendarActivity.kt"
            ).readText().contains("ViewCompat.setAccessibilityHeading")
        )
        assertTrue(layout.contains("android:layout_width=\"48dp\""))
        assertTrue(layout.contains("android:layout_height=\"48dp\""))
        assertTrue(layout.contains("android:fillViewport=\"true\""))
        assertTrue(emptyState.contains("android:minHeight=\"48dp\""))
        assertTrue(reminderItem.contains("android:contentDescription=\"@string/delete_reminder\""))
        assertTrue(reminderItem.contains("android:minHeight=\"48dp\""))
    }

    private fun sourceFile(projectPath: String): File {
        val fromRoot = File(projectPath)
        if (fromRoot.exists()) return fromRoot
        return File(projectPath.removePrefix("app/"))
    }
}
