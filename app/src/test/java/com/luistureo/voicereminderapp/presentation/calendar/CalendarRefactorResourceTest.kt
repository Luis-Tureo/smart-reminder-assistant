package com.luistureo.voicereminderapp.presentation.calendar

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarRefactorResourceTest {

    @Test
    fun unifiedCalendarIsLauncherAndOldHomeOnlyRedirects() {
        val manifest = sourceFile("app/src/main/AndroidManifest.xml").readText()
        val mainActivity = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/MainActivity.kt"
        ).readText()
        val layout = sourceFile("app/src/main/res/layout/activity_calendar.xml").readText()
        val strings = sourceFile("app/src/main/res/values/strings.xml").readText()

        assertFalse(sourceFile("app/src/main/res/layout/activity_main.xml").exists())
        assertTrue(manifest.substringAfter(".presentation.calendar.CalendarActivity")
            .substringBefore(".presentation.assistant.AssistantActivity")
            .contains("android.intent.category.LAUNCHER"))
        assertFalse(mainActivity.contains("setContentView"))
        assertTrue(mainActivity.contains("CalendarActivity::class.java"))
        assertTrue(manifest.substringAfter("android:name=\".MainActivity\"")
            .substringBefore("<activity-alias")
            .contains("android:exported=\"true\""))
        assertTrue(layout.contains("@string/unified_screen_title"))
        assertTrue(layout.contains("@string/unified_screen_subtitle"))
        assertFalse(layout.contains("navigationIcon"))
        assertFalse(layout.contains("cardAssistantReminder"))
        assertFalse(layout.contains("panelHomeSupport"))
        assertFalse(strings.contains("home_virtual_assistant_title"))
        assertFalse(strings.contains("home_calendar_title"))
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
        assertTrue(
            layout.indexOf("@+id/cardCalendarSelectedDate") <
                layout.indexOf("@+id/tvCalendarConnectionsHeading")
        )
        assertFalse(layout.contains("cardCalendarAssistant"))
        assertFalse(layout.contains("btnCalendarAssistant"))
        assertFalse(layout.contains("cardCalendarNextDaySummary"))
        assertFalse(layout.contains("recyclerCalendarUpcomingReminders"))
        assertFalse(layout.contains("calendarReminderEmptyStateCard"))
        assertFalse(strings.contains("calendar_next_day_summary"))
        assertFalse(strings.contains("calendar_upcoming_empty"))
    }

    @Test
    fun centeredHeadingsAndPrimaryShowAllButtonShareAccessibleStyling() {
        val calendarLayout = sourceFile("app/src/main/res/layout/activity_calendar.xml").readText()
        val filterLayout = sourceFile(
            "app/src/main/res/layout/view_calendar_filters.xml"
        ).readText()
        val strings = sourceFile("app/src/main/res/values/strings.xml").readText()
        val colors = sourceFile("app/src/main/res/values/colors.xml").readText()
        val title = calendarLayout.substringAfter("@+id/tvUnifiedScreenTitle")
            .substringBefore("/>")
        val subtitle = calendarLayout.substringAfter("@+id/tvUnifiedScreenSubtitle")
            .substringBefore("/>")
        val addReminderButton = calendarLayout.substringAfter("@+id/btnCalendarCreateReminder")
            .substringBefore("/>")
        val filterHeading = filterLayout.substringAfter("@+id/tvCalendarFiltersHeading")
            .substringBefore("/>")
        val showAllButton = filterLayout.substringAfter("@+id/btnCalendarLegendShowAll")
            .substringBefore("/>")

        listOf(title, subtitle, filterHeading).forEach { heading ->
            assertTrue(heading.contains("android:gravity=\"center\""))
            assertTrue(heading.contains("android:textAlignment=\"center\""))
        }
        assertFalse(title.contains("android:maxLines"))
        assertFalse(subtitle.contains("android:maxLines"))
        assertTrue(filterLayout.indexOf("@+id/tvCalendarFiltersHeading") <
            filterLayout.indexOf("@+id/cardCalendarFilterReminder"))

        listOf(addReminderButton, showAllButton).forEach { button ->
            assertTrue(button.contains("app:backgroundTint=\"@color/primary_blue\""))
            assertTrue(button.contains("android:textColor=\"@color/white\""))
            assertTrue(button.contains("app:cornerRadius=\"24dp\""))
            assertTrue(button.contains("android:minHeight=\"48dp\""))
            assertTrue(button.contains("android:textStyle=\"bold\""))
        }
        assertTrue(addReminderButton.contains(
            "android:contentDescription=\"@string/calendar_add_reminder_accessibility\""
        ))
        assertTrue(showAllButton.contains("app:iconTint=\"@color/white\""))
        assertTrue(showAllButton.contains("app:icon=\"@drawable/ic_calendar_list\""))
        assertTrue(showAllButton.contains("app:iconGravity=\"textStart\""))
        assertTrue(showAllButton.contains("android:maxWidth=\"360dp\""))
        assertTrue(showAllButton.contains(
            "android:contentDescription=\"@string/calendar_legend_show_all_accessibility\""
        ))
        assertTrue(strings.contains(">Ver todas las actividades</string>"))
        assertTrue(sourceFile("app/src/main/res/drawable/ic_calendar_list.xml").exists())

        val primary = colorValue(colors, "primary_blue")
        val white = colorValue(colors, "white")
        assertTrue(contrastRatio(primary, white) >= 4.5)
    }

    @Test
    fun reminderCreationPanelOffersImplementedAccessibleOptionsOnly() {
        val calendarActivity = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/calendar/" +
                "CalendarActivity.kt"
        ).readText()
        val calendarLayout = sourceFile("app/src/main/res/layout/activity_calendar.xml").readText()
        val dialogLayout = sourceFile(
            "app/src/main/res/layout/dialog_reminder_creation_options.xml"
        ).readText()
        val strings = sourceFile("app/src/main/res/values/strings.xml").readText()
        val assistantActivity = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/assistant/" +
                "AssistantActivity.kt"
        ).readText()
        val viewModel = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/viewmodel/" +
                "ReminderViewModel.kt"
        ).readText()

        assertFalse(calendarLayout.contains("cardCalendarAssistant"))
        assertTrue(calendarLayout.contains("@string/calendar_add_reminder_accessibility"))
        assertTrue(calendarActivity.contains("showReminderCreationOptions()"))
        listOf(
            "cardReminderCreationVoice",
            "cardReminderCreationManual",
            "cardReminderCreationCamera",
            "cardReminderCreationPaste"
        ).forEach { optionId ->
            val option = dialogLayout.substringAfter("@+id/$optionId").substringBefore(">")
            assertTrue(option.contains("android:clickable=\"true\""))
            assertTrue(option.contains("android:focusable=\"true\""))
            assertTrue(option.contains("android:minHeight=\"76dp\""))
            assertTrue(option.contains("android:contentDescription="))
        }
        assertTrue(dialogLayout.startsWith("<?xml"))
        assertTrue(dialogLayout.contains("<ScrollView"))
        assertFalse(dialogLayout.contains("android:maxLines"))
        assertFalse(dialogLayout.contains("Google"))
        assertFalse(dialogLayout.contains("Microsoft"))
        assertTrue(strings.contains(">Agregar recordatorio</string>"))
        assertTrue(strings.contains(">Elige c&#243;mo quieres crearlo.</string>"))
        assertTrue(strings.contains(">Escribir recordatorio</string>"))
        assertFalse(strings.contains("Otras formas de agregar"))
        assertTrue(calendarActivity.contains("info.className = android.widget.Button::class.java.name"))
        assertTrue(calendarActivity.contains("voiceOption.requestFocus()"))
        assertTrue(calendarActivity.contains("createReminderButton.requestFocus()"))
        assertTrue(calendarActivity.contains("dialog.dismiss()"))
        assertTrue(calendarActivity.contains("AssistantActivity.EXTRA_SELECTED_DATE"))
        assertTrue(calendarActivity.contains("ManualReminderActivity.EXTRA_PREFILLED_DATE"))
        assertTrue(calendarActivity.contains("PasteTextReminderActivity.EXTRA_SELECTED_DATE"))
        assertTrue(calendarActivity.contains("calendarViewModel.onDaySelected(savedDate)"))
        assertTrue(calendarActivity.contains("STATE_ACTIVE_FILTER"))
        assertTrue(calendarActivity.contains("STATE_SELECTED_DATE"))
        assertTrue(assistantActivity.contains("setAssistantDefaultDate"))
        assertTrue(assistantActivity.contains("EXTRA_SAVED_DATE"))
        assertTrue(viewModel.contains("assistantDefaultDate"))
        assertTrue(viewModel.contains("AssistantReminderSaved"))
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
    fun removedScreensRemainSafeUnifiedAliasesAndCalendarConnectionsRemain() {
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
        assertTrue(sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/MainActivity.kt"
        ).readText().contains("CalendarActivity::class.java"))
        assertTrue(calendarActivity.contains("GoogleCalendarAuthManager"))
        assertTrue(calendarActivity.contains("MicrosoftCalendarAuthProvider"))
        assertTrue(calendarActivity.contains("UnifiedCalendarSynchronizer"))
    }

    private fun sourceFile(projectPath: String): File {
        val fromRoot = File(projectPath)
        if (fromRoot.exists()) return fromRoot
        return File(projectPath.removePrefix("app/"))
    }

    private fun colorValue(colors: String, name: String): String {
        return Regex("<color name=\"$name\">(#[0-9A-Fa-f]{6})</color>")
            .find(colors)
            ?.groupValues
            ?.get(1)
            ?: error("Color $name not found")
    }

    private fun contrastRatio(first: String, second: String): Double {
        val firstLuminance = relativeLuminance(first)
        val secondLuminance = relativeLuminance(second)
        val lighter = maxOf(firstLuminance, secondLuminance)
        val darker = minOf(firstLuminance, secondLuminance)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: String): Double {
        val channels = listOf(1, 3, 5).map { index ->
            val channel = color.substring(index, index + 2).toInt(16) / 255.0
            if (channel <= 0.03928) channel / 12.92 else Math.pow((channel + 0.055) / 1.055, 2.4)
        }
        return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722
    }
}
