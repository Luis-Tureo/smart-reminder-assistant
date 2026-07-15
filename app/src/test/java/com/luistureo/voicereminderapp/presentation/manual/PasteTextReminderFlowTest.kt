package com.luistureo.voicereminderapp.presentation.manual

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasteTextReminderFlowTest {
    private val activity = source("presentation/manual/PasteTextReminderActivity.kt")
    private val calendarActivity = source("presentation/calendar/CalendarActivity.kt")
    private val layout = resource("layout/activity_paste_text_reminder.xml")
    private val strings = resource("values/strings.xml")

    @Test
    fun confirmationIsRequiredBeforeSaving() {
        assertTrue(activity.contains("if (!confirmationCard.isVisible || isSaving) return"))
        assertTrue(activity.contains("confirmationCard.isVisible = true"))
        assertTrue(activity.contains("ReminderTextParserLogger.confirmationShown()"))
    }

    @Test
    fun confirmationHasRequiredActionsAndFields() {
        assertTrue(strings.contains(">Guardar recordatorio<"))
        assertTrue(strings.contains(">Editar datos<"))
        assertTrue(strings.contains(">Cancelar<"))
        assertTrue(layout.contains("tvPasteReminderConfirmationSummary"))
        assertTrue(activity.contains("Título:"))
        assertTrue(activity.contains("Detalle:"))
        assertTrue(activity.contains("Recurrencia:"))
    }

    @Test
    fun savingSchedulesAlarmAfterRoomSave() {
        val saveIndex = activity.indexOf("saveReminderDraftUseCase(draft)")
        val alarmIndex = activity.indexOf("reminderScheduler.syncReminderSchedule")
        assertTrue(saveIndex >= 0)
        assertTrue(alarmIndex > saveIndex)
    }

    @Test
    fun calendarOffersPasteTextWithSelectedDay() {
        assertTrue(calendarActivity.contains("cardReminderCreationPaste"))
        assertTrue(calendarActivity.contains("PasteTextReminderActivity.EXTRA_SELECTED_DATE"))
        assertTrue(calendarActivity.contains("PasteTextReminderActivity::class.java"))
    }

    @Test
    fun parserLogsMetadataWithoutFullInput() {
        val parser = source("core/nlp/PastedReminderTextParser.kt")
        assertTrue(parser.contains("characterCount = input.length"))
        assertFalse(parser.contains("safeLog(input)"))
        assertFalse(parser.contains("message=\$input"))
    }

    @Test
    fun defensiveOperationsAvoidCrashes() {
        assertTrue(activity.contains("runCatching"))
        assertTrue(activity.contains("buildDraftOrShowErrors() ?: return"))
    }

    @Test
    fun pastScheduleValidationMessageIsShownWhenSaveUseCaseRejects() {
        assertTrue(activity.contains("ReminderTemporalValidationPolicy.PAST_SCHEDULE_MESSAGE"))
    }

    private fun source(relativePath: String): String = File(
        "src/main/java/com/luistureo/voicereminderapp/$relativePath"
    ).readText()

    private fun resource(relativePath: String): String = File("src/main/res/$relativePath").readText()
}
