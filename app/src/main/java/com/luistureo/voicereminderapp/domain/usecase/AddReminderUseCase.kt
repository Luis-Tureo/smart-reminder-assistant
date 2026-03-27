package com.luistureo.voicereminderapp.domain.usecase

import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.repository.ReminderRepository

class AddReminderUseCase(
    private val reminderRepository: ReminderRepository
) {
    suspend operator fun invoke(reminder: Reminder) {
        reminderRepository.insertReminder(reminder)
    }
}