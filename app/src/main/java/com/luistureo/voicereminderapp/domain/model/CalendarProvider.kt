package com.luistureo.voicereminderapp.domain.model

enum class CalendarProvider(
    val displayName: String
) {
    APP("Smart Reminder Assistant"),
    GOOGLE_CALENDAR("Google Calendar"),
    MICROSOFT_CALENDAR("Microsoft Calendar")
}
