package com.luistureo.voicereminderapp.presentation.state

import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.presentation.common.UiText

data class ReminderUiState(
    val reminders: List<Reminder> = emptyList(),
    val isLoading: Boolean = false,
    val error: UiText? = null,
    val formState: ReminderFormState = ReminderFormState()
)
