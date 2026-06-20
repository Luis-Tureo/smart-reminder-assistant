package com.luistureo.voicereminderapp.core.calendar.unified

import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import com.luistureo.voicereminderapp.domain.model.CalendarProviderSyncState
import com.luistureo.voicereminderapp.domain.model.GoogleCalendarSyncState
import com.luistureo.voicereminderapp.domain.model.Reminder

object UnifiedCalendarSyncPolicy {

    private val externalProviders = setOf(
        CalendarProvider.GOOGLE_CALENDAR,
        CalendarProvider.MICROSOFT_CALENDAR
    )

    fun prepareAppUpsert(reminder: Reminder): Reminder {
        val providersToCreate = externalProviders.filterTo(mutableSetOf()) { provider ->
            reminder.externalIdsByProvider[provider].isNullOrBlank()
        }
        val providersToUpdate = externalProviders - providersToCreate

        return reminder.copy(
            pendingCreateProviders = reminder.pendingCreateProviders + providersToCreate,
            pendingUpdateProviders = reminder.pendingUpdateProviders + providersToUpdate,
            lastEditedSource = CalendarProvider.APP,
            externalEditNote = null,
            hiddenFromApp = false
        )
    }

    fun prepareAppDelete(reminder: Reminder): Reminder {
        val linkedProviders = reminder.externalIdsByProvider
            .filterValues { it.isNotBlank() }
            .keys
            .filterTo(mutableSetOf()) { it in externalProviders }
        val pendingStates = linkedProviders.associateWith {
            CalendarProviderSyncState.PENDING_DELETE
        }

        return reminder.copy(
            providerSyncStates = reminder.providerSyncStates + pendingStates,
            pendingCreateProviders = reminder.pendingCreateProviders - linkedProviders,
            pendingUpdateProviders = reminder.pendingUpdateProviders - linkedProviders,
            pendingDeleteProviders = reminder.pendingDeleteProviders + linkedProviders,
            hiddenFromApp = true,
            lastEditedSource = CalendarProvider.APP
        )
    }

    fun markProviderSynced(
        reminder: Reminder,
        provider: CalendarProvider,
        externalId: String,
        syncedAtEpochMillis: Long
    ): Reminder {
        val updatedExternalIds = reminder.externalIdsByProvider + (provider to externalId)
        val updatedStates = reminder.providerSyncStates + (provider to CalendarProviderSyncState.SYNCED)

        return reminder.copy(
            googleCalendarEventId = if (provider == CalendarProvider.GOOGLE_CALENDAR) {
                externalId
            } else {
                reminder.googleCalendarEventId
            },
            googleCalendarSyncState = if (provider == CalendarProvider.GOOGLE_CALENDAR) {
                GoogleCalendarSyncState.SYNCED
            } else {
                reminder.googleCalendarSyncState
            },
            googleCalendarLastSyncAtEpochMillis = if (provider == CalendarProvider.GOOGLE_CALENDAR) {
                syncedAtEpochMillis
            } else {
                reminder.googleCalendarLastSyncAtEpochMillis
            },
            microsoftCalendarEventId = if (provider == CalendarProvider.MICROSOFT_CALENDAR) {
                externalId
            } else {
                reminder.microsoftCalendarEventId
            },
            externalIdsByProvider = updatedExternalIds,
            syncedProviders = reminder.syncedProviders + provider + CalendarProvider.APP,
            providerSyncStates = updatedStates,
            syncedFingerprintsByProvider = reminder.syncedFingerprintsByProvider +
                    (provider to CalendarEventFingerprint.fromReminder(reminder)),
            pendingCreateProviders = reminder.pendingCreateProviders - provider,
            pendingUpdateProviders = reminder.pendingUpdateProviders - provider,
            pendingDeleteProviders = reminder.pendingDeleteProviders - provider
        ).also { syncedReminder ->
            CalendarSyncLogger.meetingMetadata(
                provider = provider,
                originalProvider = syncedReminder.originProvider,
                persisted = syncedReminder.meetingUrl == reminder.meetingUrl,
                synchronizedProvidersCount = syncedReminder.syncedProviders.size,
                pendingProvidersCount = pendingProviders(syncedReminder).size
            )
        }
    }

    fun markProviderSyncFailed(
        reminder: Reminder,
        provider: CalendarProvider,
        state: CalendarProviderSyncState
    ): Reminder {
        val hasExternalId = !reminder.externalIdsByProvider[provider].isNullOrBlank()
        val pendingCreateProviders = if (hasExternalId) {
            reminder.pendingCreateProviders - provider
        } else {
            reminder.pendingCreateProviders + provider
        }
        val pendingUpdateProviders = if (hasExternalId) {
            reminder.pendingUpdateProviders + provider
        } else {
            reminder.pendingUpdateProviders
        }

        return reminder.copy(
            googleCalendarSyncState = if (provider == CalendarProvider.GOOGLE_CALENDAR) {
                when (state) {
                    CalendarProviderSyncState.NOT_CONNECTED -> GoogleCalendarSyncState.NOT_CONNECTED
                    else -> GoogleCalendarSyncState.FAILED
                }
            } else {
                reminder.googleCalendarSyncState
            },
            providerSyncStates = reminder.providerSyncStates + (provider to state),
            pendingCreateProviders = pendingCreateProviders,
            pendingUpdateProviders = pendingUpdateProviders
        )
    }

    fun markProviderPendingDelete(
        reminder: Reminder,
        provider: CalendarProvider
    ): Reminder {
        return reminder.copy(
            providerSyncStates = reminder.providerSyncStates +
                    (provider to CalendarProviderSyncState.PENDING_DELETE),
            pendingDeleteProviders = reminder.pendingDeleteProviders + provider,
            hiddenFromApp = true
        )
    }

    fun markProviderDeleteSynced(
        reminder: Reminder,
        provider: CalendarProvider
    ): Reminder {
        return reminder.copy(
            externalIdsByProvider = reminder.externalIdsByProvider - provider,
            microsoftCalendarEventId = if (provider == CalendarProvider.MICROSOFT_CALENDAR) {
                null
            } else {
                reminder.microsoftCalendarEventId
            },
            syncedProviders = reminder.syncedProviders - provider,
            providerSyncStates = reminder.providerSyncStates - provider,
            syncedFingerprintsByProvider = reminder.syncedFingerprintsByProvider - provider,
            pendingCreateProviders = reminder.pendingCreateProviders - provider,
            pendingUpdateProviders = reminder.pendingUpdateProviders - provider,
            pendingDeleteProviders = reminder.pendingDeleteProviders - provider
        )
    }

    fun pendingProviders(reminder: Reminder): Set<CalendarProvider> {
        return reminder.pendingCreateProviders +
                reminder.pendingUpdateProviders +
                reminder.pendingDeleteProviders
    }
}
