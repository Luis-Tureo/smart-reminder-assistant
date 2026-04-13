package com.luistureo.voicereminderapp.domain.usecase

import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.repository.ReminderRepository

class GetReminderByIdUseCase(
    private val reminderRepository: ReminderRepository
) {
    suspend operator fun invoke(reminderId: Int): Reminder? {
        return reminderRepository.getReminderById(reminderId)
    }
}
