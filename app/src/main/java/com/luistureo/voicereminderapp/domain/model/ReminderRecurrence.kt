package com.luistureo.voicereminderapp.domain.model

// Patrón explícito de repetición del recordatorio.
data class ReminderRecurrence(
    val unit: ReminderRecurrenceUnit,
    val interval: Int = 1,
    val weekdays: Set<ReminderWeekday> = emptySet(),
    val isActive: Boolean = true
) {
    val normalizedInterval: Int
        get() = interval.coerceAtLeast(1)
}
