package com.luistureo.voicereminderapp.core.utils

import com.luistureo.voicereminderapp.domain.model.ReminderRecurrence
import com.luistureo.voicereminderapp.domain.model.ReminderRecurrenceUnit

object ReminderRecurrenceFormatter {

    // Genera una etiqueta legible a partir de la recurrencia estructurada.
    fun format(recurrence: ReminderRecurrence?): String? {
        recurrence ?: return null

        if (!recurrence.isActive) {
            return "Inactiva"
        }

        val interval = recurrence.normalizedInterval

        if (recurrence.unit == ReminderRecurrenceUnit.WEEK && recurrence.weekdays.isNotEmpty()) {
            return recurrence.weekdays
                .sortedBy { it.dayOfWeek.value }
                .joinToString(separator = " • ") { it.shortLabel }
        }

        return when (recurrence.unit) {
            ReminderRecurrenceUnit.DAY -> {
                if (interval == 1) "Diaria" else "Cada $interval días"
            }

            ReminderRecurrenceUnit.WEEK -> {
                if (interval == 1) "Semanal" else "Cada $interval semanas"
            }

            ReminderRecurrenceUnit.MONTH -> {
                if (interval == 1) "Mensual" else "Cada $interval meses"
            }

            ReminderRecurrenceUnit.YEAR -> {
                if (interval == 1) "Anual" else "Cada $interval años"
            }
        }
    }
}
