package com.luistureo.voicereminderapp.core.calendar.google

import java.time.LocalDate
import java.time.ZonedDateTime
import com.luistureo.voicereminderapp.domain.model.CalendarProvider

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
    val isUrgent: Boolean,
    val meetingUrl: String? = null,
    val meetingProvider: CalendarProvider? = null,
    val isOnlineMeeting: Boolean = false,
    val originProviderHint: CalendarProvider? = null,
    val isManagedCopy: Boolean = false,
    val localIdHint: Int? = null,
    val updatedAtEpochMillis: Long? = null
)
