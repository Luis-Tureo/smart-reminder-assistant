package com.luistureo.voicereminderapp.domain.usecase

import com.luistureo.voicereminderapp.core.reminder.ReminderTemporalValidationPolicy
import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.repository.ReminderRepository

class UpdateReminderUseCase(
    private val reminderRepository: ReminderRepository,
    private val currentTimeMillis: () -> Long = { System.currentTimeMillis() }
) {
    suspend operator fun invoke(reminder: Reminder) {
        ReminderTemporalValidationPolicy.validateUpdateSchedule(
            updatedReminder = reminder,
            existingReminder = reminderRepository.getReminderById(reminder.id),
            nowEpochMillis = currentTimeMillis()
        )?.let { message ->
            throw IllegalArgumentException(message)
        }
        reminderRepository.updateReminder(reminder)
    }
}
