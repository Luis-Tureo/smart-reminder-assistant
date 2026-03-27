package com.luistureo.voicereminderapp.presentation.state

data class VoiceReminderState(
    val step: VoiceReminderStep = VoiceReminderStep.IDLE,
    val reminderText: String = "",
    val reminderHour: Int? = null,
    val reminderMinute: Int? = null,
    val reminderDay: Int? = null,
    val reminderMonth: Int? = null,
    val reminderYear: Int? = null,
    val isVoiceFlowActive: Boolean = false,
    val lastAssistantMessage: String = ""
)