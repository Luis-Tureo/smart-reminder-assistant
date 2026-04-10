package com.luistureo.voicereminderapp.presentation.state

import com.luistureo.voicereminderapp.domain.model.Reminder

sealed class HomeReminderListItem {
    data class DayHeader(
        val title: String,
        val subtitle: String
    ) : HomeReminderListItem()

    data class ReminderRow(
        val reminder: Reminder,
        val occurrenceAtEpochMillis: Long
    ) : HomeReminderListItem()
}
