package com.luistureo.voicereminderapp.domain.model

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
    val isRecurring: Boolean
        get() = recurrence != null

    val isRecurringActive: Boolean
        get() = recurrence?.isActive == true
}
