package com.luistureo.voicereminderapp.domain.model

import com.luistureo.voicereminderapp.core.utils.DateTimeFormatter
import com.luistureo.voicereminderapp.core.utils.ReminderRecurrenceFormatter

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
    val scheduleState: ReminderScheduleState = ReminderScheduleState(),
    val googleCalendarEventId: String? = null,
    val googleCalendarSyncState: GoogleCalendarSyncState = GoogleCalendarSyncState.PENDING,
    val googleCalendarLastSyncAtEpochMillis: Long? = null
) {
    val date: String
        get() = DateTimeFormatter.formatDateFromEpoch(scheduledAtEpochMillis)

    val time: String
        get() = DateTimeFormatter.formatTimeFromEpoch(scheduledAtEpochMillis)

    val nextTriggerDate: String?
        get() = scheduleState.nextTriggerAtEpochMillis?.let(DateTimeFormatter::formatDateFromEpoch)

    val nextTriggerTime: String?
        get() = scheduleState.nextTriggerAtEpochMillis?.let(DateTimeFormatter::formatTimeFromEpoch)

    val recurrenceLabel: String?
        get() = ReminderRecurrenceFormatter.format(recurrence)

    val isRecurring: Boolean
        get() = recurrence != null

    val isRecurringActive: Boolean
        get() = recurrence?.isActive == true
}
