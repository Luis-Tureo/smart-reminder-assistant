package com.luistureo.voicereminderapp.data.mapper

import com.luistureo.voicereminderapp.data.local.entity.ReminderEntity
import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import com.luistureo.voicereminderapp.domain.model.CalendarProviderSyncState
import com.luistureo.voicereminderapp.domain.model.GoogleCalendarSyncState
import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.model.ReminderRecurrence
import com.luistureo.voicereminderapp.domain.model.ReminderRecurrenceUnit
import com.luistureo.voicereminderapp.domain.model.ReminderScheduleState
import com.luistureo.voicereminderapp.domain.model.ReminderSource
import com.luistureo.voicereminderapp.domain.model.ReminderType
import com.luistureo.voicereminderapp.domain.model.ReminderWeekday
import com.luistureo.voicereminderapp.core.calendar.unified.MeetingUrlPolicy
import java.net.URLDecoder
import java.net.URLEncoder

// Convierte la entidad persistida al modelo de dominio.
fun ReminderEntity.toDomain(): Reminder {
    val resolvedType = ReminderType.entries.firstOrNull { it.name == type } ?: ReminderType.DEFAULT
    val resolvedSource = ReminderSource.entries.firstOrNull { it.name == source }
        ?: ReminderSource.MANUAL
    val resolvedGoogleSyncState =
        GoogleCalendarSyncState.entries.firstOrNull { it.name == googleCalendarSyncState }
            ?: GoogleCalendarSyncState.PENDING
    val resolvedOriginProvider = CalendarProvider.entries.firstOrNull { it.name == originProvider }
        ?: CalendarProvider.APP
    val resolvedExternalIds = parseProviderMap(externalIdsByProvider).toMutableMap().apply {
        if (!googleCalendarEventId.isNullOrBlank()) {
            put(CalendarProvider.GOOGLE_CALENDAR, googleCalendarEventId)
        }
        if (!microsoftCalendarEventId.isNullOrBlank()) {
            put(CalendarProvider.MICROSOFT_CALENDAR, microsoftCalendarEventId)
        }
    }
    val resolvedSyncedProviders = parseProviderSet(syncedProviders).toMutableSet().apply {
        if (resolvedGoogleSyncState == GoogleCalendarSyncState.SYNCED) {
            add(CalendarProvider.GOOGLE_CALENDAR)
            add(CalendarProvider.APP)
        }
    }
    val resolvedProviderSyncStates = parseProviderSyncStates(providerSyncStates).toMutableMap()
        .apply {
            if (!containsKey(CalendarProvider.GOOGLE_CALENDAR)) {
                put(CalendarProvider.GOOGLE_CALENDAR, resolvedGoogleSyncState.toProviderSyncState())
            }
        }
    val resolvedLastEditedSource = CalendarProvider.entries.firstOrNull { it.name == lastEditedSource }
        ?: CalendarProvider.APP
    val storedMeetingUrls = parseProviderMap(meetingUrlsByProvider)
    val resolvedMeetingUrls = meetingUrl
        ?.takeIf(MeetingUrlPolicy::isSupportedMeetingUrl)
        ?.let { url ->
            MeetingUrlPolicy.providerForUrl(url)?.let { provider ->
                storedMeetingUrls + (provider to url)
            }
        }
        ?: storedMeetingUrls
    val resolvedMeetingUrl = MeetingUrlPolicy.selectPreferredUrl(
        resolvedOriginProvider,
        resolvedMeetingUrls,
        meetingUrl
    )
    val resolvedMeetingProvider = CalendarProvider.entries
        .firstOrNull { it.name == meetingProvider }
        ?: MeetingUrlPolicy.providerForUrl(resolvedMeetingUrl)
    val resolvedRecurrenceUnit = recurrenceUnit?.let { storedValue ->
        ReminderRecurrenceUnit.entries.firstOrNull { it.name == storedValue }
    }

    val recurrence = resolvedRecurrenceUnit?.let { unit ->
        ReminderRecurrence(
            unit = unit,
            interval = recurrenceInterval,
            weekdays = recurrenceWeekdays
                .split(",")
                .mapNotNull { item ->
                    item.trim()
                        .takeIf { it.isNotBlank() }
                        ?.let { value -> ReminderWeekday.entries.firstOrNull { it.name == value } }
                }
                .toSet(),
            isActive = isRecurringActive
        )
    }

    return Reminder(
        id = id,
        title = title,
        detail = detail,
        scheduledAtEpochMillis = scheduledAtEpochMillis,
        isCompleted = isCompleted,
        type = resolvedType,
        isUrgent = isUrgent,
        source = resolvedSource,
        recurrence = recurrence,
        scheduleState = ReminderScheduleState(
            nextTriggerAtEpochMillis = nextTriggerAtEpochMillis,
            lastTriggeredAtEpochMillis = lastTriggeredAtEpochMillis,
            activeAlertAtEpochMillis = activeAlertAtEpochMillis,
            activeAlertRepeatCount = activeAlertRepeatCount,
            nextUrgentRepeatAtEpochMillis = nextUrgentRepeatAtEpochMillis
        ),
        googleCalendarEventId = googleCalendarEventId,
        googleCalendarSyncState = resolvedGoogleSyncState,
        googleCalendarLastSyncAtEpochMillis = googleCalendarLastSyncAtEpochMillis,
        microsoftCalendarLastSyncAtEpochMillis = microsoftCalendarLastSyncAtEpochMillis,
        microsoftCalendarEventId = microsoftCalendarEventId
            ?: resolvedExternalIds[CalendarProvider.MICROSOFT_CALENDAR],
        externalIdsByProvider = resolvedExternalIds,
        originProvider = resolvedOriginProvider,
        syncedProviders = resolvedSyncedProviders,
        providerSyncStates = resolvedProviderSyncStates,
        syncedFingerprintsByProvider = parseProviderMap(syncedFingerprintsByProvider),
        pendingCreateProviders = parseProviderSet(pendingCreateProviders),
        pendingUpdateProviders = parseProviderSet(pendingUpdateProviders),
        pendingDeleteProviders = parseProviderSet(pendingDeleteProviders),
        meetingUrl = resolvedMeetingUrl,
        meetingProvider = resolvedMeetingProvider,
        isOnlineMeeting = isOnlineMeeting || resolvedMeetingUrl != null,
        meetingUrlsByProvider = resolvedMeetingUrls,
        isSuspended = isSuspended,
        suspendedOccurrenceAtEpochMillis = suspendedOccurrenceAtEpochMillis,
        lastEditedSource = resolvedLastEditedSource,
        externalEditNote = externalEditNote,
        isAllDay = isAllDay,
        hiddenFromApp = hiddenFromApp
    )
}

// Convierte el dominio a la entidad persistida.
fun Reminder.toEntity(): ReminderEntity {
    val persistedExternalIds = externalIdsByProvider.toMutableMap().apply {
        googleCalendarEventId?.takeIf { it.isNotBlank() }?.let {
            put(CalendarProvider.GOOGLE_CALENDAR, it)
        }
        microsoftCalendarEventId?.takeIf { it.isNotBlank() }?.let {
            put(CalendarProvider.MICROSOFT_CALENDAR, it)
        }
    }
    val persistedMeetingUrls = meetingUrlsByProvider.toMutableMap().apply {
        meetingUrl?.takeIf(MeetingUrlPolicy::isSupportedMeetingUrl)?.let { url ->
            MeetingUrlPolicy.providerForUrl(url)?.let { provider -> put(provider, url) }
        }
    }
    return ReminderEntity(
        id = id,
        title = title,
        detail = detail,
        scheduledAtEpochMillis = scheduledAtEpochMillis,
        isCompleted = isCompleted,
        type = type.name,
        isUrgent = isUrgent,
        source = source.name,
        recurrenceUnit = recurrence?.unit?.name,
        recurrenceInterval = recurrence?.normalizedInterval ?: 1,
        recurrenceWeekdays = recurrence?.weekdays
            ?.sortedBy { it.dayOfWeek.value }
            ?.joinToString(separator = ",") { it.name }
            .orEmpty(),
        isRecurringActive = recurrence?.isActive ?: false,
        nextTriggerAtEpochMillis = scheduleState.nextTriggerAtEpochMillis,
        lastTriggeredAtEpochMillis = scheduleState.lastTriggeredAtEpochMillis,
        activeAlertAtEpochMillis = scheduleState.activeAlertAtEpochMillis,
        activeAlertRepeatCount = scheduleState.activeAlertRepeatCount,
        nextUrgentRepeatAtEpochMillis = scheduleState.nextUrgentRepeatAtEpochMillis,
        googleCalendarEventId = googleCalendarEventId
            ?: externalIdsByProvider[CalendarProvider.GOOGLE_CALENDAR],
        googleCalendarSyncState = googleCalendarSyncState.name,
        googleCalendarLastSyncAtEpochMillis = googleCalendarLastSyncAtEpochMillis,
        microsoftCalendarLastSyncAtEpochMillis = microsoftCalendarLastSyncAtEpochMillis,
        microsoftCalendarEventId = microsoftCalendarEventId
            ?: externalIdsByProvider[CalendarProvider.MICROSOFT_CALENDAR],
        externalIdsByProvider = encodeProviderMap(persistedExternalIds),
        originProvider = originProvider.name,
        syncedProviders = encodeProviderSet(syncedProviders),
        providerSyncStates = encodeProviderSyncStates(providerSyncStates),
        syncedFingerprintsByProvider = encodeProviderMap(syncedFingerprintsByProvider),
        pendingCreateProviders = encodeProviderSet(pendingCreateProviders),
        pendingUpdateProviders = encodeProviderSet(pendingUpdateProviders),
        pendingDeleteProviders = encodeProviderSet(pendingDeleteProviders),
        meetingUrl = meetingUrl,
        meetingProvider = (meetingProvider ?: MeetingUrlPolicy.providerForUrl(meetingUrl))?.name,
        isOnlineMeeting = isOnlineMeeting || MeetingUrlPolicy.isSupportedMeetingUrl(meetingUrl),
        meetingUrlsByProvider = encodeProviderMap(persistedMeetingUrls),
        isSuspended = isSuspended,
        suspendedOccurrenceAtEpochMillis = suspendedOccurrenceAtEpochMillis,
        lastEditedSource = lastEditedSource.name,
        externalEditNote = externalEditNote,
        isAllDay = isAllDay,
        hiddenFromApp = hiddenFromApp
    )
}

private fun parseProviderSet(rawValue: String): Set<CalendarProvider> {
    return rawValue
        .split(",")
        .mapNotNull { value ->
            CalendarProvider.entries.firstOrNull { it.name == value.trim() }
        }
        .toSet()
}

private fun encodeProviderSet(providers: Set<CalendarProvider>): String {
    return providers
        .sortedBy { it.name }
        .joinToString(separator = ",") { it.name }
}

private fun parseProviderMap(rawValue: String): Map<CalendarProvider, String> {
    if (rawValue.isBlank()) return emptyMap()

    return rawValue
        .split("|")
        .mapNotNull { entry ->
            val providerName = entry.substringBefore("=", missingDelimiterValue = "")
            val encodedValue = entry.substringAfter("=", missingDelimiterValue = "")
            val provider = CalendarProvider.entries.firstOrNull { it.name == providerName }
            if (provider == null || encodedValue.isBlank()) {
                null
            } else {
                provider to decodeValue(encodedValue)
            }
        }
        .toMap()
}

private fun encodeProviderMap(externalIds: Map<CalendarProvider, String>): String {
    return externalIds
        .filterValues { it.isNotBlank() }
        .toSortedMap(compareBy { it.name })
        .entries
        .joinToString(separator = "|") { (provider, externalId) ->
            "${provider.name}=${encodeValue(externalId)}"
        }
}

private fun parseProviderSyncStates(rawValue: String): Map<CalendarProvider, CalendarProviderSyncState> {
    if (rawValue.isBlank()) return emptyMap()

    return rawValue
        .split("|")
        .mapNotNull { entry ->
            val providerName = entry.substringBefore("=", missingDelimiterValue = "")
            val stateName = entry.substringAfter("=", missingDelimiterValue = "")
            val provider = CalendarProvider.entries.firstOrNull { it.name == providerName }
            val state = CalendarProviderSyncState.entries.firstOrNull { it.name == stateName }
            if (provider == null || state == null) null else provider to state
        }
        .toMap()
}

private fun encodeProviderSyncStates(
    states: Map<CalendarProvider, CalendarProviderSyncState>
): String {
    return states
        .toSortedMap(compareBy { it.name })
        .entries
        .joinToString(separator = "|") { (provider, state) ->
            "${provider.name}=${state.name}"
        }
}

private fun GoogleCalendarSyncState.toProviderSyncState(): CalendarProviderSyncState {
    return when (this) {
        GoogleCalendarSyncState.NOT_CONNECTED -> CalendarProviderSyncState.NOT_CONNECTED
        GoogleCalendarSyncState.PENDING -> CalendarProviderSyncState.PENDING_CREATE
        GoogleCalendarSyncState.SYNCED -> CalendarProviderSyncState.SYNCED
        GoogleCalendarSyncState.FAILED -> CalendarProviderSyncState.FAILED
    }
}

private fun encodeValue(value: String): String {
    return URLEncoder.encode(value, "UTF-8")
}

private fun decodeValue(value: String): String {
    return URLDecoder.decode(value, "UTF-8")
}
