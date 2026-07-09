package com.luistureo.voicereminderapp.core.calendar.unified

import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import com.luistureo.voicereminderapp.domain.model.CalendarProviderSyncState
import com.luistureo.voicereminderapp.domain.model.GoogleCalendarSyncState
import com.luistureo.voicereminderapp.domain.model.Reminder

object PerReminderCalendarSyncPolicy {
    val externalProviders: Set<CalendarProvider> = setOf(
        CalendarProvider.GOOGLE_CALENDAR,
        CalendarProvider.MICROSOFT_CALENDAR
    )

    fun sanitizeTargets(providers: Set<CalendarProvider>): Set<CalendarProvider> {
        return providers.filterTo(mutableSetOf()) { it in externalProviders }
    }

    fun selectedTargets(reminder: Reminder): Set<CalendarProvider> {
        return (
                reminder.externalIdsByProvider.keys +
                        reminder.syncedProviders +
                        reminder.pendingCreateProviders +
                        reminder.pendingUpdateProviders
                )
            .filterTo(mutableSetOf()) { provider ->
                provider in externalProviders &&
                        provider !in reminder.pendingDeleteProviders
            }
    }

    fun applyTargets(
        reminder: Reminder,
        targetProviders: Set<CalendarProvider>,
        markExistingForUpdate: Boolean
    ): Reminder {
        val selectedTargets = sanitizeTargets(targetProviders)
        val linkedProviders = reminder.externalIdsByProvider
            .filterValues { it.isNotBlank() }
            .keys
            .filterTo(mutableSetOf()) { it in externalProviders }
        val providersToCreate = selectedTargets - linkedProviders
        val providersToDelete = linkedProviders - selectedTargets
        val providersToUpdate = if (markExistingForUpdate) {
            linkedProviders.intersect(selectedTargets)
        } else {
            emptySet()
        }

        val pendingStates = buildMap {
            providersToCreate.forEach { put(it, CalendarProviderSyncState.PENDING_CREATE) }
            providersToUpdate.forEach { put(it, CalendarProviderSyncState.PENDING_UPDATE) }
            providersToDelete.forEach { put(it, CalendarProviderSyncState.PENDING_DELETE) }
        }
        val retainedStates = reminder.providerSyncStates
            .filterKeys { it !in externalProviders || it in selectedTargets }

        return reminder.copy(
            googleCalendarSyncState = resolveGoogleSyncState(
                reminder = reminder,
                selectedTargets = selectedTargets,
                pendingStates = pendingStates
            ),
            pendingCreateProviders = (reminder.pendingCreateProviders - externalProviders) +
                    providersToCreate,
            pendingUpdateProviders = (reminder.pendingUpdateProviders - externalProviders) +
                    providersToUpdate,
            pendingDeleteProviders = (reminder.pendingDeleteProviders - externalProviders) +
                    providersToDelete,
            providerSyncStates = retainedStates + pendingStates,
            lastEditedSource = CalendarProvider.APP,
            externalEditNote = null,
            hiddenFromApp = false
        )
    }

    fun canSyncImportedReminderTo(
        reminder: Reminder,
        provider: CalendarProvider,
        isProviderConnected: Boolean
    ): Boolean {
        if (!isProviderConnected) return false
        if (provider !in externalProviders) return false
        if (reminder.isOnlineMeeting || MeetingUrlPolicy.isSupportedMeetingUrl(reminder.meetingUrl)) {
            return false
        }
        if (reminder.originProvider == provider) return false
        if (reminder.originProvider !in externalProviders) return false
        if (!reminder.externalIdsByProvider[provider].isNullOrBlank()) return false
        if (provider in reminder.syncedProviders) return false
        if (provider in reminder.pendingCreateProviders) return false
        if (provider in reminder.pendingUpdateProviders) return false
        return provider !in reminder.pendingDeleteProviders
    }

    private fun resolveGoogleSyncState(
        reminder: Reminder,
        selectedTargets: Set<CalendarProvider>,
        pendingStates: Map<CalendarProvider, CalendarProviderSyncState>
    ): GoogleCalendarSyncState {
        val googleProvider = CalendarProvider.GOOGLE_CALENDAR
        if (googleProvider !in selectedTargets) {
            return GoogleCalendarSyncState.SYNCED
        }
        return when (pendingStates[googleProvider]) {
            CalendarProviderSyncState.PENDING_CREATE,
            CalendarProviderSyncState.PENDING_UPDATE,
            CalendarProviderSyncState.PENDING_DELETE -> GoogleCalendarSyncState.PENDING
            else -> reminder.googleCalendarSyncState
        }
    }
}
