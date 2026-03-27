package com.luistureo.voicereminderapp.presentation.state

sealed class ReminderUiEvent {
    data class ShowMessage(val message: String) : ReminderUiEvent()

    data class ScheduleReminder(
        val reminderText: String,
        val reminderDate: String,
        val reminderTime: String,
        val triggerTimeMillis: Long
    ) : ReminderUiEvent()

    data object ClearForm : ReminderUiEvent()
}