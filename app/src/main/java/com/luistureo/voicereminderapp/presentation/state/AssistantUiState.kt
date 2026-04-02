package com.luistureo.voicereminderapp.presentation.state

import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.model.ReminderDraft

data class AssistantUiState(
    val reminders: List<Reminder> = emptyList(),

    val isLoading: Boolean = false,
    val error: String? = null,

    // 🔊 Texto reconocido desde voz
    val recognizedText: String = "",

    // 🤖 Respuesta de la asistente
    val assistantReply: String = "",

    // 📝 Borrador del recordatorio en curso
    val pendingDraft: ReminderDraft? = null,

    // 🎯 Indica si la sesión del asistente está activa
    val isConversationActive: Boolean = false
)