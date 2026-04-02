package com.luistureo.voicereminderapp.domain.service

import com.luistureo.voicereminderapp.domain.model.AssistantResponse
import com.luistureo.voicereminderapp.domain.model.ReminderDraft

interface ChatAssistantService {
    suspend fun processMessage(
        userMessage: String,
        currentDraft: ReminderDraft?
    ): AssistantResponse
}