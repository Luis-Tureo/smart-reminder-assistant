package com.luistureo.voicereminderapp.core.calendar.google

import java.time.LocalDate
import java.time.ZonedDateTime

data class GoogleCalendarEvent(
    val id: String,
    val title: String,
    val description: String,
    val startDateTime: ZonedDateTime?,
    val endDateTime: ZonedDateTime?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val isAllDay: Boolean,
    val isCompleted: Boolean,
    val isUrgent: Boolean
)
