package com.luistureo.voicereminderapp.presentation.state

sealed class ReminderUiEvent {
    data class ShowMessage(val message: String) : ReminderUiEvent()

    data class ScheduleReminder(
        val reminderTitle: String,
        val reminderDetail: String,
        val reminderDate: String,
        val reminderTime: String,
        val triggerTimeMillis: Long
    ) : ReminderUiEvent()

    data class SpeakAssistantReply(val message: String) : ReminderUiEvent()

    data object StopAssistantConversation : ReminderUiEvent()

    data object ClearForm : ReminderUiEvent()
}