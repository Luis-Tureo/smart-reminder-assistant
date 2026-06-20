package com.luistureo.voicereminderapp.domain.model

import com.luistureo.voicereminderapp.core.utils.DateTimeFormatter
import com.luistureo.voicereminderapp.core.utils.ReminderRecurrenceFormatter

// Modelo principal del recordatorio con soporte estructurado de agenda.
data class Reminder(
    val id: Int = 0,
    val title: String,
    val detail: String,
    val scheduledAtEpochMillis: Long,
    val isCompleted: Boolean = false,
    val type: ReminderType = ReminderType.DEFAULT,
    val isUrgent: Boolean = false,
    val source: ReminderSource = ReminderSource.MANUAL,
    val recurrence: ReminderRecurrence? = null,
    val scheduleState: ReminderScheduleState = ReminderScheduleState(),
    val googleCalendarEventId: String? = null,
    val googleCalendarSyncState: GoogleCalendarSyncState = GoogleCalendarSyncState.PENDING,
    val googleCalendarLastSyncAtEpochMillis: Long? = null,
    val microsoftCalendarLastSyncAtEpochMillis: Long? = null,
    val microsoftCalendarEventId: String? = null,
    val externalIdsByProvider: Map<CalendarProvider, String> = emptyMap(),
    val originProvider: CalendarProvider = CalendarProvider.APP,
    val syncedProviders: Set<CalendarProvider> = emptySet(),
    val providerSyncStates: Map<CalendarProvider, CalendarProviderSyncState> = emptyMap(),
    val syncedFingerprintsByProvider: Map<CalendarProvider, String> = emptyMap(),
    val pendingCreateProviders: Set<CalendarProvider> = emptySet(),
    val pendingUpdateProviders: Set<CalendarProvider> = emptySet(),
    val pendingDeleteProviders: Set<CalendarProvider> = emptySet(),
    val meetingUrl: String? = null,
    val meetingProvider: CalendarProvider? = null,
    val isOnlineMeeting: Boolean = false,
    val meetingUrlsByProvider: Map<CalendarProvider, String> = emptyMap(),
    val isSuspended: Boolean = false,
    val suspendedOccurrenceAtEpochMillis: Long? = null,
    val lastEditedSource: CalendarProvider = CalendarProvider.APP,
    val externalEditNote: String? = null,
    val isAllDay: Boolean = false,
    val hiddenFromApp: Boolean = false
) {
    val date: String
        get() = DateTimeFormatter.formatDateFromEpoch(scheduledAtEpochMillis)

    val time: String
        get() = DateTimeFormatter.formatTimeFromEpoch(scheduledAtEpochMillis)

    val nextTriggerDate: String?
        get() = scheduleState.nextTriggerAtEpochMillis?.let(DateTimeFormatter::formatDateFromEpoch)

    val nextTriggerTime: String?
        get() = scheduleState.nextTriggerAtEpochMillis?.let(DateTimeFormatter::formatTimeFromEpoch)

    val recurrenceLabel: String?
        get() = ReminderRecurrenceFormatter.format(recurrence)

    val isRecurring: Boolean
        get() = recurrence != null

    val isRecurringActive: Boolean
        get() = recurrence?.isActive == true

    val linkedExternalProviders: Set<CalendarProvider>
        get() = externalIdsByProvider.keys
            .filter { it != CalendarProvider.APP }
            .toSet()

    val meetingJoinUrl: String?
        get() = meetingUrl

    val synchronizedProviders: Set<CalendarProvider>
        get() = syncedProviders

    val pendingProviders: Set<CalendarProvider>
        get() = pendingCreateProviders + pendingUpdateProviders + pendingDeleteProviders
}
