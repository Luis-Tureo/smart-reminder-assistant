package com.luistureo.voicereminderapp.presentation.calendar

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarConflictUiResourceTest {

    @Test
    fun duplicateWarningIsInlineAndNeverBlocking() {
        val activity = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/calendar/" +
                    "CalendarActivity.kt"
        ).readText()
        val state = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/calendar/" +
                    "CalendarUiState.kt"
        ).readText()
        val warningLayout = sourceFile(
            "app/src/main/res/layout/item_calendar_duplicate_warning.xml"
        ).readText()
        val strings = sourceFile("app/src/main/res/values/strings.xml").readText()
        val inlineRenderer = activity.substringAfter("private fun renderDayDetails")
            .substringBefore("private fun renderFilteredItems")

        assertTrue(activity.contains("R.layout.item_calendar_duplicate_warning"))
        assertTrue(state.contains("selectedDateDuplicateWarning"))
        assertTrue(warningLayout.contains("calendar_duplicate_inline_warning"))
        assertTrue(strings.contains("citas o recordatorios muy cercanos"))
        assertFalse(activity.contains("renderConflictDialog"))
        assertFalse(activity.contains("showRecurringSuspendConfirmation"))
        assertFalse(state.contains("pendingConflictDialog"))
        assertFalse(inlineRenderer.contains("AlertDialog"))
        assertFalse(inlineRenderer.contains("Toast"))
        assertFalse(inlineRenderer.contains("Snackbar"))
        assertFalse(strings.contains("No puedes asistir"))
        assertFalse(strings.contains("quedar&#225; suspendida"))
    }

    @Test
    fun nearbyItemsShowSubtleBadgeAndCalendarRemainsUsable() {
        val activity = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/calendar/" +
                    "CalendarActivity.kt"
        ).readText()
        val detailLayout = sourceFile("app/src/main/res/layout/item_calendar_detail.xml").readText()

        assertTrue(detailLayout.contains("@+id/tvCalendarDetailNearbySchedule"))
        assertTrue(detailLayout.contains("calendar_duplicate_nearby_badge"))
        assertTrue(activity.contains("nearbyScheduleView.isVisible = detail.hasNearbySchedule"))
        assertTrue(activity.contains("cardView.setOnClickListener"))
    }

    @Test
    fun duplicateDetectionNeverSuspendsOrDeletesItems() {
        val viewModel = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/calendar/" +
                    "CalendarViewModel.kt"
        ).readText()
        val duplicateFlow = viewModel.substringAfter("private fun buildDuplicateGroups")
            .substringBefore("private fun buildFilteredItems")

        assertTrue(duplicateFlow.contains("CalendarConflictPolicy.findConflicts"))
        assertFalse(viewModel.contains("fun resolveConflict"))
        assertFalse(viewModel.contains("suspendCalendarEntry"))
        assertFalse(duplicateFlow.contains("CalendarSuspensionPolicy.suspendOccurrence"))
        assertFalse(duplicateFlow.contains("deleteReminderUseCase"))
        assertFalse(duplicateFlow.contains("deleteReminderEvent"))
    }

    @Test
    fun calendarDetailExposesMeetingAndReactivationActions() {
        val detailLayout = sourceFile("app/src/main/res/layout/item_calendar_detail.xml").readText()
        val strings = sourceFile("app/src/main/res/values/strings.xml").readText()

        assertTrue(detailLayout.contains("@+id/btnCalendarOpenMeeting"))
        assertTrue(detailLayout.contains("@+id/btnCalendarReactivate"))
        assertTrue(strings.contains("Unirse a la reuni&#243;n"))
        assertTrue(strings.contains("Reactivar cita"))
    }

    @Test
    fun notificationRecommendationLivesOnHomeAndNotInsideCalendarFlow() {
        val homeLayout = sourceFile("app/src/main/res/layout/activity_main.xml").readText()
        val calendarLayout = sourceFile("app/src/main/res/layout/activity_calendar.xml").readText()

        assertTrue(homeLayout.contains("calendar_duplicate_alert_recommendation"))
        assertFalse(calendarLayout.contains("calendar_duplicate_alert_recommendation"))
    }

    @Test
    fun suspendedDetailUsesCrossedOutStyleAndExternalDescriptionNote() {
        val calendarActivity = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/calendar/CalendarActivity.kt"
        ).readText()
        val googleClient = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/core/calendar/google/GoogleCalendarRestClient.kt"
        ).readText()
        val microsoftGateway = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/core/calendar/microsoft/MicrosoftGraphCalendarGateway.kt"
        ).readText()

        assertTrue(calendarActivity.contains("Paint.STRIKE_THRU_TEXT_FLAG"))
        assertTrue(googleClient.contains("CalendarSuspensionPolicy.SUSPENDED_DETAIL_NOTE"))
        assertTrue(microsoftGateway.contains("CalendarSuspensionPolicy.SUSPENDED_DETAIL_NOTE"))
    }

    private fun sourceFile(projectPath: String): File {
        val fromRoot = File(projectPath)
        if (fromRoot.exists()) return fromRoot
        return File(projectPath.removePrefix("app/"))
    }
}
