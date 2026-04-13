package com.luistureo.voicereminderapp.core.utils

import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.model.ReminderRecurrence
import com.luistureo.voicereminderapp.domain.model.ReminderRecurrenceUnit

// Centraliza el formateo visible del recordatorio fuera del modelo de dominio.
object ReminderDisplayFormatter {

    fun formatScheduledDate(reminder: Reminder): String {
        return DateTimeFormatterCore.formatDateFromEpoch(reminder.scheduledAtEpochMillis)
    }

    fun formatScheduledTime(reminder: Reminder): String {
        return DateTimeFormatterCore.formatTimeFromEpoch(reminder.scheduledAtEpochMillis)
    }

    fun formatNextTriggerDate(reminder: Reminder): String? {
        return reminder.scheduleState.nextTriggerAtEpochMillis
            ?.let(DateTimeFormatterCore::formatDateFromEpoch)
    }

    fun formatNextTriggerTime(reminder: Reminder): String? {
        return reminder.scheduleState.nextTriggerAtEpochMillis
            ?.let(DateTimeFormatterCore::formatTimeFromEpoch)
    }

    fun formatRecurrenceLabel(reminder: Reminder): String? {
        return formatRecurrenceLabel(reminder.recurrence)
    }

    fun formatRecurrenceLabel(recurrence: ReminderRecurrence?): String? {
        recurrence ?: return null

        if (!recurrence.isActive) {
            return "Inactiva"
        }

        val interval = recurrence.normalizedInterval

        if (recurrence.unit == ReminderRecurrenceUnit.WEEK && recurrence.weekdays.isNotEmpty()) {
            return recurrence.weekdays
                .sortedBy { it.isoDayNumber }
                .joinToString(separator = " - ") { it.shortLabel }
        }

        return when (recurrence.unit) {
            ReminderRecurrenceUnit.DAY -> if (interval == 1) "Diaria" else "Cada $interval dias"
            ReminderRecurrenceUnit.WEEK -> if (interval == 1) "Semanal" else "Cada $interval semanas"
            ReminderRecurrenceUnit.MONTH -> if (interval == 1) "Mensual" else "Cada $interval meses"
            ReminderRecurrenceUnit.YEAR -> if (interval == 1) "Anual" else "Cada $interval anos"
        }
    }
}
