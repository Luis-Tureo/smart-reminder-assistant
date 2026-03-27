package com.luistureo.voicereminderapp.presentation.state

enum class VoiceReminderStep {
    IDLE,
    WAITING_FOR_REMINDER_TEXT,
    WAITING_FOR_TIME,
    WAITING_FOR_DAY,
    WAITING_FOR_CONFIRMATION
}