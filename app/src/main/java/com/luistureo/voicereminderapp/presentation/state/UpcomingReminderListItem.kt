package com.luistureo.voicereminderapp.presentation.state

import com.luistureo.voicereminderapp.domain.model.Reminder

sealed class UpcomingReminderListItem {
    data class DayHeader(
        val title: String,
        val subtitle: String
    ) : UpcomingReminderListItem()

    data class ReminderRow(
        val reminder: Reminder,
        val occurrenceAtEpochMillis: Long
    ) : UpcomingReminderListItem()
}
