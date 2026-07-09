package com.luistureo.voicereminderapp.core.calendar.unified

import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import com.luistureo.voicereminderapp.domain.model.Reminder

object UnifiedCalendarCardDisplayPolicy {

    fun buildProviderLines(reminder: Reminder): List<String> {
        val lines = mutableListOf<String>()
        val origin = reminder.originProvider
        val syncedProviders = reminder.syncedProviders + CalendarProvider.APP

        lines += "Origen: ${origin.displayName}"

        val syncedLabel = when (origin) {
            CalendarProvider.APP -> {
                val externalSynced = listOf(
                    CalendarProvider.GOOGLE_CALENDAR,
                    CalendarProvider.MICROSOFT_CALENDAR
                ).filter { it in syncedProviders }

                if (externalSynced.size == 2) {
                    "Sincronizado con: Google Calendar y Microsoft Calendar"
                } else if (externalSynced.isNotEmpty()) {
                    "Sincronizado con: ${externalSynced.toDisplayList()}"
                } else {
                    null
                }
            }

            CalendarProvider.GOOGLE_CALENDAR -> {
                val targets = listOf(
                    CalendarProvider.MICROSOFT_CALENDAR,
                    CalendarProvider.APP
                ).filter { it in syncedProviders }
                "Sincronizado con: ${targets.toDisplayList()}"
            }

            CalendarProvider.MICROSOFT_CALENDAR -> {
                val targets = listOf(
                    CalendarProvider.GOOGLE_CALENDAR,
                    CalendarProvider.APP
                ).filter { it in syncedProviders }
                "Sincronizado con: ${targets.toDisplayList()}"
            }
        }

        syncedLabel?.takeIf { !it.endsWith(": ") }?.let(lines::add)

        if (reminder.isSuspended) {
            lines += "Cita suspendida desde Smart Reminder Assistant"
        }

        return lines
    }

    private fun List<CalendarProvider>.toDisplayList(): String {
        return when (size) {
            0 -> ""
            1 -> first().displayName
            2 -> "${first().displayName} y ${last().displayName}"
            else -> dropLast(1).joinToString(separator = ", ") { it.displayName } +
                    " y ${last().displayName}"
        }
    }
}
