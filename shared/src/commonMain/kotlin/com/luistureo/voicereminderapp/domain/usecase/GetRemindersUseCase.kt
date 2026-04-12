package com.luistureo.voicereminderapp.domain.usecase

import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.repository.ReminderRepository

class GetRemindersUseCase(
    private val reminderRepository: ReminderRepository
) {
    suspend operator fun invoke(): List<Reminder> {
        return reminderRepository.getAllReminders()
    }
}
