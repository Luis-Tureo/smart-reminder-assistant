package com.luistureo.voicereminderapp.domain.model

import com.luistureo.voicereminderapp.core.utils.DateTimeFormatter

// Borrador reutilizable por flujos manuales, voz y camara.
data class ReminderDraft(
    val reminderId: Int = 0,
    val title: String? = null,
    val text: String? = null,
    val date: String? = null,
    val time: String? = null,
    val isUrgent: Boolean = false,
    val source: ReminderSource = ReminderSource.MANUAL,
    val recurrence: ReminderRecurrence? = null,
    val syncTargetProviders: Set<CalendarProvider> = emptySet()
) {
    fun isReadyToSave(): Boolean {
        return !text.isNullOrBlank() &&
                !date.isNullOrBlank() &&
                !time.isNullOrBlank()
    }

    fun buildScheduledAtEpochMillis(): Long? {
        val resolvedDate = date ?: return null
        val resolvedTime = time ?: return null
        return DateTimeFormatter.parseDateTimeToEpochMillis(resolvedDate, resolvedTime)
    }
}
