package com.luistureo.voicereminderapp.domain.model

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// Modelo principal del recordatorio con soporte estructurado de agenda.
data class Reminder(
    val id: Int = 0,
    val title: String,
    val detail: String,
    val scheduledAtEpochMillis: Long,
    val isCompleted: Boolean = false,
    val type: ReminderType = ReminderType.DEFAULT,
    val isUrgent: Boolean = false,
    val source: ReminderSource = ReminderSource.MANUAL,
    val recurrence: ReminderRecurrence? = null,
    val scheduleState: ReminderScheduleState = ReminderScheduleState()
) {
    val date: String
        get() = formatDateFromEpoch(scheduledAtEpochMillis)

    val time: String
        get() = formatTimeFromEpoch(scheduledAtEpochMillis)

    val nextTriggerDate: String?
        get() = scheduleState.nextTriggerAtEpochMillis?.let(::formatDateFromEpoch)

    val nextTriggerTime: String?
        get() = scheduleState.nextTriggerAtEpochMillis?.let(::formatTimeFromEpoch)

    val recurrenceLabel: String?
        get() = recurrence?.toDisplayLabel()

    val isRecurring: Boolean
        get() = recurrence != null

    val isRecurringActive: Boolean
        get() = recurrence?.isActive == true
}

private fun formatDateFromEpoch(epochMillis: Long): String {
    val localDateTime = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return buildString {
        append(localDateTime.dayOfMonth.toTwoDigits())
        append('/')
        append(localDateTime.monthNumber.toTwoDigits())
        append('/')
        append(localDateTime.year.toString().padStart(length = 4, padChar = '0'))
    }
}

private fun formatTimeFromEpoch(epochMillis: Long): String {
    val localDateTime = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return buildString {
        append(localDateTime.hour.toTwoDigits())
        append(':')
        append(localDateTime.minute.toTwoDigits())
    }
}

private fun ReminderRecurrence.toDisplayLabel(): String {
    if (!isActive) {
        return "Inactiva"
    }

    val interval = normalizedInterval

    if (unit == ReminderRecurrenceUnit.WEEK && weekdays.isNotEmpty()) {
        return weekdays
            .sortedBy { it.isoDayNumber }
            .joinToString(separator = " - ") { it.shortLabel }
    }

    return when (unit) {
        ReminderRecurrenceUnit.DAY -> if (interval == 1) "Diaria" else "Cada $interval dias"
        ReminderRecurrenceUnit.WEEK -> if (interval == 1) "Semanal" else "Cada $interval semanas"
        ReminderRecurrenceUnit.MONTH -> if (interval == 1) "Mensual" else "Cada $interval meses"
        ReminderRecurrenceUnit.YEAR -> if (interval == 1) "Anual" else "Cada $interval anos"
    }
}

private fun Int.toTwoDigits(): String = toString().padStart(length = 2, padChar = '0')
