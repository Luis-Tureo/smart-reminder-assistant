package com.luistureo.voicereminderapp.core.reminder

import com.luistureo.voicereminderapp.domain.model.Reminder
import java.time.LocalDate

object ReminderTemporalValidationPolicy {
    const val PAST_SCHEDULE_MESSAGE =
        "No puedes crear un recordatorio en una fecha u hora pasada."

    fun canCreateOnDate(
        selectedDate: LocalDate,
        today: LocalDate = LocalDate.now()
    ): Boolean {
        return !selectedDate.isBefore(today)
    }

    fun validateNewSchedule(
        scheduledAtEpochMillis: Long,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): String? {
        return PAST_SCHEDULE_MESSAGE.takeIf {
            scheduledAtEpochMillis <= nowEpochMillis
        }
    }

    fun validateUpdateSchedule(
        updatedReminder: Reminder,
        existingReminder: Reminder?,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): String? {
        if (updatedReminder.isCompleted || existingReminder?.isCompleted == true) {
            return null
        }

        if (existingReminder?.scheduledAtEpochMillis == updatedReminder.scheduledAtEpochMillis) {
            return null
        }

        return validateNewSchedule(
            scheduledAtEpochMillis = updatedReminder.scheduledAtEpochMillis,
            nowEpochMillis = nowEpochMillis
        )
    }
}
