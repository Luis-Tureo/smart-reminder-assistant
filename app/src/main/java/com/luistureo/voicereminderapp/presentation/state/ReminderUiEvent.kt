package com.luistureo.voicereminderapp.presentation.state

sealed class ReminderUiEvent {
    data class ShowMessage(val message: String) : ReminderUiEvent()

    data class SpeakAssistantReply(val message: String) : ReminderUiEvent()

    data class AssistantReminderSaved(val scheduledAtEpochMillis: Long) : ReminderUiEvent()

    data object StopAssistantConversation : ReminderUiEvent()
}
