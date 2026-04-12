package com.luistureo.voicereminderapp.presentation.state

import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.model.ReminderDraft

data class AssistantUiState(
    val reminders: List<Reminder> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val recognizedText: String = "",
    val assistantReply: String = "",
    val pendingDraft: ReminderDraft? = null,
    val isConversationActive: Boolean = false
)
