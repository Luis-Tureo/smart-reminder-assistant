package com.luistureo.voicereminderapp.domain.model

data class AssistantResponse(
    val reply: String,
    val intent: AssistantIntent,
    val reminderText: String? = null,
    val reminderDate: String? = null,
    val reminderTime: String? = null,
    val shouldSaveReminder: Boolean = false
)
