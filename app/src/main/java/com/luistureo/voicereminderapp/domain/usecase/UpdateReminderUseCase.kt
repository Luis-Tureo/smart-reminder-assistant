package com.luistureo.voicereminderapp.domain.usecase

import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.repository.ReminderRepository

class UpdateReminderUseCase(
    private val reminderRepository: ReminderRepository
) {
    suspend operator fun invoke(reminder: Reminder) {
        reminderRepository.updateReminder(reminder)
    }
}