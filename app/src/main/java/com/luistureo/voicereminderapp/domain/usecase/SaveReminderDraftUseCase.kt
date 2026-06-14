package com.luistureo.voicereminderapp.domain.usecase

import com.luistureo.voicereminderapp.core.reminder.ReminderOccurrenceCalculator
import com.luistureo.voicereminderapp.core.reminder.ReminderScheduleStateResolver
import com.luistureo.voicereminderapp.core.utils.ReminderTypeResolver
import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.model.ReminderDraft
import com.luistureo.voicereminderapp.domain.model.ReminderScheduleState
import com.luistureo.voicereminderapp.domain.repository.ReminderRepository

class SaveReminderDraftUseCase(
    private val reminderRepository: ReminderRepository,
    private val occurrenceCalculator: ReminderOccurrenceCalculator = ReminderOccurrenceCalculator()
) {
    private val scheduleStateResolver = ReminderScheduleStateResolver(occurrenceCalculator)

    suspend operator fun invoke(draft: ReminderDraft): Reminder {
        val reminderDetail = draft.text?.trim().orEmpty()
        val reminderTitle = draft.title?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: extractReminderTitle(reminderDetail)
        val existingReminder = draft.reminderId
            .takeIf { it != 0 }
            ?.let { reminderRepository.getReminderById(it) }

        val scheduledAtEpochMillis = draft.buildScheduledAtEpochMillis()
            ?: error("No fue posible construir la fecha del recordatorio.")

        val baseReminder = Reminder(
            id = draft.reminderId,
            title = reminderTitle,
            detail = reminderDetail,
            scheduledAtEpochMillis = scheduledAtEpochMillis,
            isCompleted = existingReminder?.isCompleted ?: false,
            type = ReminderTypeResolver.resolve(reminderTitle, reminderDetail),
            isUrgent = draft.isUrgent,
            source = draft.source,
            recurrence = draft.recurrence,
            scheduleState = existingReminder?.scheduleState
                ?: scheduleStateResolver.clearUrgentAlert(ReminderScheduleState()),
            googleCalendarEventId = existingReminder?.googleCalendarEventId,
            googleCalendarSyncState = existingReminder?.googleCalendarSyncState
                ?: com.luistureo.voicereminderapp.domain.model.GoogleCalendarSyncState.PENDING,
            googleCalendarLastSyncAtEpochMillis = existingReminder?.googleCalendarLastSyncAtEpochMillis
        )

        val resolvedReminder = baseReminder.copy(
            scheduleState = scheduleStateResolver.resolveOnSave(baseReminder)
        )

        return if (draft.reminderId == 0) {
            val reminderId = reminderRepository.insertReminder(resolvedReminder)
            resolvedReminder.copy(id = reminderId)
        } else {
            reminderRepository.updateReminder(resolvedReminder)
            resolvedReminder
        }
    }

    // Genera un titulo consistente si el usuario no lo entrega manualmente.
    private fun extractReminderTitle(detail: String): String {
        val cleanedText = detail.trim()
        if (cleanedText.isBlank()) {
            return "Recordatorio"
        }

        return cleanedText
            .substringBefore(".")
            .substringBefore(",")
            .take(48)
            .trim()
            .ifBlank { "Recordatorio" }
    }
}
