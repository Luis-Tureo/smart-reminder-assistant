package com.luistureo.voicereminderapp.core.calendar.unified

import android.content.Context
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarReminderSynchronizer
import com.luistureo.voicereminderapp.core.calendar.microsoft.MicrosoftCalendarSyncProvider
import com.luistureo.voicereminderapp.core.calendar.microsoft.MicrosoftCalendarSynchronizer
import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import com.luistureo.voicereminderapp.domain.model.CalendarProviderSyncState
import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.repository.ReminderRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class UnifiedCalendarSynchronizer(
    context: Context,
    private val reminderRepository: ReminderRepository,
    private val googleCalendarSynchronizer: GoogleCalendarReminderSynchronizer =
        GoogleCalendarReminderSynchronizer(context, reminderRepository),
    private val microsoftCalendarSynchronizer: MicrosoftCalendarSynchronizer =
        MicrosoftCalendarSyncProvider.create(context, reminderRepository)
) {
    val isMicrosoftConfigured: Boolean
        get() = microsoftCalendarSynchronizer.isConfigured

    val isMicrosoftConnected: Boolean
        get() = microsoftCalendarSynchronizer.isConnected

    suspend fun syncSavedReminder(reminder: Reminder): Reminder {
        CalendarSyncLogger.syncStarted(
            provider = null,
            action = "sync_saved_reminder",
            pendingCreateCount = reminder.pendingCreateProviders.size,
            pendingUpdateCount = reminder.pendingUpdateProviders.size,
            pendingDeleteCount = reminder.pendingDeleteProviders.size
        )
        var currentReminder = UnifiedCalendarSyncPolicy.prepareAppUpsert(reminder)
        if (currentReminder != reminder) {
            reminderRepository.updateReminder(currentReminder)
        }
        currentReminder = googleCalendarSynchronizer.syncSavedReminder(currentReminder)

        val microsoftResult = microsoftCalendarSynchronizer.syncSavedReminder(currentReminder)
        if (microsoftResult != currentReminder) {
            reminderRepository.updateReminder(microsoftResult)
            currentReminder = microsoftResult
        }

        CalendarSyncLogger.syncFinished(
            provider = null,
            action = "sync_saved_reminder",
            pendingCount = currentReminder.pendingCreateProviders.size +
                    currentReminder.pendingUpdateProviders.size +
                    currentReminder.pendingDeleteProviders.size
        )
        return currentReminder
    }

    suspend fun deleteReminderEvent(reminder: Reminder): Reminder {
        CalendarSyncLogger.syncStarted(
            provider = null,
            action = "delete_reminder_event",
            pendingDeleteCount = reminder.pendingDeleteProviders.size
        )
        var currentReminder = UnifiedCalendarSyncPolicy.prepareAppDelete(reminder)
        reminderRepository.updateReminder(currentReminder)

        if (CalendarProvider.GOOGLE_CALENDAR in currentReminder.pendingDeleteProviders) {
            currentReminder = googleCalendarSynchronizer.deleteReminderEvent(currentReminder)
        }
        if (CalendarProvider.MICROSOFT_CALENDAR in currentReminder.pendingDeleteProviders) {
            currentReminder = microsoftCalendarSynchronizer.deleteReminderEvent(currentReminder)
        }
        reminderRepository.updateReminder(currentReminder)
        CalendarSyncLogger.syncFinished(
            provider = null,
            action = "delete_reminder_event",
            pendingCount = currentReminder.pendingDeleteProviders.size,
            completedDeleteCount = reminder.linkedExternalProviders.size -
                    currentReminder.pendingDeleteProviders.size
        )
        return currentReminder
    }

    suspend fun syncPendingReminders(): UnifiedCalendarSyncSummary {
        microsoftCalendarSynchronizer.clearFailure()
        val remindersBeforeSync = reminderRepository.getAllRemindersIncludingHidden()
        CalendarSyncLogger.syncStarted(
            provider = null,
            action = "sync_pending_all",
            pendingCreateCount = remindersBeforeSync.sumOf { it.pendingCreateProviders.size },
            pendingUpdateCount = remindersBeforeSync.sumOf { it.pendingUpdateProviders.size },
            pendingDeleteCount = remindersBeforeSync.sumOf { it.pendingDeleteProviders.size }
        )
        val googleSummary = if (googleCalendarSynchronizer.isConnected) {
            googleCalendarSynchronizer.syncPendingReminders()
        } else {
            com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarSyncSummary(
                syncedCount = 0,
                failedCount = 0,
                pendingDeleteCount = 0,
                completedDeleteCount = 0
            )
        }
        var microsoftPendingCount = 0
        var microsoftFailedCount = 0
        var microsoftSyncedCount = 0
        var microsoftCompletedDeleteCount = 0

        if (microsoftCalendarSynchronizer.isConnected) {
            reminderRepository.getAllRemindersIncludingHidden()
                .filter { reminder -> reminder.needsMicrosoftSync() }
                .forEach { reminder ->
                val wasPendingDelete = CalendarProvider.MICROSOFT_CALENDAR in
                        reminder.pendingDeleteProviders
                val microsoftResult = if (
                    wasPendingDelete
                ) {
                    microsoftCalendarSynchronizer.deleteReminderEvent(reminder)
                } else {
                    microsoftCalendarSynchronizer.syncSavedReminder(reminder)
                }
                if (
                    wasPendingDelete &&
                    CalendarProvider.MICROSOFT_CALENDAR !in microsoftResult.pendingDeleteProviders
                ) {
                    microsoftCompletedDeleteCount++
                }
                if (microsoftResult.hiddenFromApp && microsoftResult.pendingDeleteProviders.isEmpty()) {
                    reminderRepository.deleteReminder(microsoftResult)
                    return@forEach
                }
                if (microsoftResult != reminder) {
                    reminderRepository.updateReminder(microsoftResult)
                }
                when (microsoftResult.providerSyncStates[CalendarProvider.MICROSOFT_CALENDAR]) {
                    CalendarProviderSyncState.NOT_CONNECTED -> microsoftPendingCount++
                    CalendarProviderSyncState.FAILED -> microsoftFailedCount++
                    CalendarProviderSyncState.SYNCED -> microsoftSyncedCount++
                    else -> Unit
                }
                }
        } else {
            microsoftPendingCount = reminderRepository.getAllRemindersIncludingHidden().count { reminder ->
                CalendarProvider.MICROSOFT_CALENDAR in reminder.pendingCreateProviders ||
                        CalendarProvider.MICROSOFT_CALENDAR in reminder.pendingUpdateProviders ||
                        CalendarProvider.MICROSOFT_CALENDAR in reminder.pendingDeleteProviders
            }
        }

        val summary = UnifiedCalendarSyncSummary(
            syncedCount = googleSummary.syncedCount + microsoftSyncedCount,
            failedCount = googleSummary.failedCount + microsoftFailedCount,
            pendingDeleteCount = googleSummary.pendingDeleteCount,
            completedDeleteCount = googleSummary.completedDeleteCount +
                    microsoftCompletedDeleteCount,
            importedCount = 0,
            microsoftPendingCount = microsoftPendingCount,
            isMicrosoftConfigured = microsoftCalendarSynchronizer.isConfigured,
            googleFailureCode = googleSummary.failureCode,
            microsoftFailureCode = microsoftCalendarSynchronizer.failureCode
        )
        CalendarSyncLogger.syncFinished(
            provider = null,
            action = "sync_pending_all",
            syncedCount = summary.syncedCount,
            failedCount = summary.failedCount,
            pendingCount = summary.microsoftPendingCount + summary.pendingDeleteCount,
            completedDeleteCount = summary.completedDeleteCount
        )
        return summary
    }

    suspend fun syncMicrosoftCalendar(
        timeMin: Instant = todayStartInstant(),
        timeMax: Instant = timeMin.plus(CALENDAR_SYNC_FUTURE_RANGE)
    ): UnifiedCalendarSyncSummary {
        microsoftCalendarSynchronizer.clearFailure()
        if (!microsoftCalendarSynchronizer.isConfigured) {
            CalendarSyncLogger.fallback(
                provider = CalendarProvider.MICROSOFT_CALENDAR,
                action = "sync_microsoft",
                fallbackReason = "microsoft_graph_oauth_not_configured"
            )
            return UnifiedCalendarSyncSummary(
                syncedCount = 0,
                failedCount = 0,
                pendingDeleteCount = 0,
                completedDeleteCount = 0,
                importedCount = 0,
                microsoftPendingCount = 0,
                isMicrosoftConfigured = false
            )
        }
        if (!microsoftCalendarSynchronizer.isConnected) {
            CalendarSyncLogger.fallback(
                provider = CalendarProvider.MICROSOFT_CALENDAR,
                action = "sync_microsoft",
                fallbackReason = "not_connected"
            )
            return UnifiedCalendarSyncSummary(
                syncedCount = 0,
                failedCount = 0,
                pendingDeleteCount = 0,
                completedDeleteCount = 0,
                importedCount = 0,
                microsoftPendingCount = 0,
                isMicrosoftConfigured = true
            )
        }

        val importSummary = microsoftCalendarSynchronizer.importEvents(timeMin, timeMax)

        val reminders = reminderRepository.getAllRemindersIncludingHidden()
        val remindersToSync = reminders.filter { reminder -> reminder.needsMicrosoftSync() }
        CalendarSyncLogger.syncStarted(
            provider = CalendarProvider.MICROSOFT_CALENDAR,
            action = "sync_microsoft",
            pendingCreateCount = reminders.count {
                CalendarProvider.MICROSOFT_CALENDAR in it.pendingCreateProviders
            },
            pendingUpdateCount = reminders.count {
                CalendarProvider.MICROSOFT_CALENDAR in it.pendingUpdateProviders
            },
            pendingDeleteCount = reminders.count {
                CalendarProvider.MICROSOFT_CALENDAR in it.pendingDeleteProviders
            }
        )

        var syncedCount = 0
        var failedCount = 0
        var pendingCount = 0
        var pendingDeleteCount = 0
        var completedDeleteCount = 0

        remindersToSync.forEach { reminder ->
            val wasPendingDelete = CalendarProvider.MICROSOFT_CALENDAR in
                    reminder.pendingDeleteProviders
            val microsoftResult = if (
                wasPendingDelete
            ) {
                microsoftCalendarSynchronizer.deleteReminderEvent(reminder)
            } else {
                microsoftCalendarSynchronizer.syncSavedReminder(reminder)
            }
            if (wasPendingDelete) {
                if (CalendarProvider.MICROSOFT_CALENDAR in microsoftResult.pendingDeleteProviders) {
                    pendingDeleteCount++
                } else {
                    completedDeleteCount++
                }
            }
            if (microsoftResult.hiddenFromApp && microsoftResult.pendingDeleteProviders.isEmpty()) {
                reminderRepository.deleteReminder(microsoftResult)
                return@forEach
            }
            if (microsoftResult != reminder) {
                reminderRepository.updateReminder(microsoftResult)
            }
            when (microsoftResult.providerSyncStates[CalendarProvider.MICROSOFT_CALENDAR]) {
                CalendarProviderSyncState.SYNCED -> syncedCount++
                CalendarProviderSyncState.NOT_CONNECTED,
                CalendarProviderSyncState.PENDING_CREATE,
                CalendarProviderSyncState.PENDING_UPDATE,
                CalendarProviderSyncState.PENDING_DELETE -> pendingCount++
                CalendarProviderSyncState.FAILED -> failedCount++
                null -> Unit
            }
        }

        val summary = UnifiedCalendarSyncSummary(
            syncedCount = syncedCount + importSummary.importedCount,
            failedCount = failedCount,
            pendingDeleteCount = pendingDeleteCount,
            completedDeleteCount = completedDeleteCount,
            importedCount = importSummary.importedCount,
            microsoftPendingCount = pendingCount,
            isMicrosoftConfigured = true,
            microsoftFailureCode = microsoftCalendarSynchronizer.failureCode,
            microsoftRetryAfterMillis = microsoftCalendarSynchronizer.retryAfterMillis
        )
        CalendarSyncLogger.syncFinished(
            provider = CalendarProvider.MICROSOFT_CALENDAR,
            action = "sync_microsoft",
            syncedCount = summary.syncedCount,
            failedCount = summary.failedCount,
            pendingCount = summary.microsoftPendingCount
        )
        return summary
    }

    suspend fun importExternalEvents(
        timeMin: Instant,
        timeMax: Instant
    ): UnifiedCalendarSyncSummary {
        CalendarSyncLogger.syncStarted(provider = null, action = "import_external_events")
        val googleImportedCount = if (googleCalendarSynchronizer.isConnected) {
            googleCalendarSynchronizer.importEvents(timeMin, timeMax)
        } else {
            0
        }
        val microsoftSummary = if (microsoftCalendarSynchronizer.isConnected) {
            microsoftCalendarSynchronizer.importEvents(timeMin, timeMax)
        } else {
            com.luistureo.voicereminderapp.core.calendar.microsoft.MicrosoftCalendarImportSummary(
                importedCount = 0,
                skippedCount = 0,
                isConfigured = microsoftCalendarSynchronizer.isConfigured
            )
        }
        val pendingSummary = syncPendingReminders()

        val summary = UnifiedCalendarSyncSummary(
            syncedCount = pendingSummary.syncedCount,
            failedCount = pendingSummary.failedCount,
            pendingDeleteCount = pendingSummary.pendingDeleteCount,
            completedDeleteCount = pendingSummary.completedDeleteCount,
            importedCount = googleImportedCount + microsoftSummary.importedCount,
            microsoftPendingCount = pendingSummary.microsoftPendingCount,
            isMicrosoftConfigured = microsoftSummary.isConfigured
        )
        CalendarSyncLogger.syncFinished(
            provider = null,
            action = "import_external_events",
            syncedCount = summary.syncedCount,
            failedCount = summary.failedCount,
            pendingCount = summary.microsoftPendingCount + summary.pendingDeleteCount
        )
        return summary
    }

    companion object {
        val CALENDAR_SYNC_FUTURE_RANGE: Duration = Duration.ofDays(180)

        private fun todayStartInstant(): Instant {
            return LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        }
    }

    private fun Reminder.needsMicrosoftSync(): Boolean {
        if (hiddenFromApp) {
            return CalendarProvider.MICROSOFT_CALENDAR in pendingDeleteProviders
        }
        val microsoftState = providerSyncStates[CalendarProvider.MICROSOFT_CALENDAR]
        val hasMicrosoftExternalId = externalIdsByProvider[CalendarProvider.MICROSOFT_CALENDAR]
            .isNullOrBlank()
            .not()
        return CalendarProvider.MICROSOFT_CALENDAR in pendingCreateProviders ||
                CalendarProvider.MICROSOFT_CALENDAR in pendingUpdateProviders ||
                CalendarProvider.MICROSOFT_CALENDAR in pendingDeleteProviders ||
                (!hasMicrosoftExternalId && originProvider != CalendarProvider.MICROSOFT_CALENDAR) ||
                microsoftState == CalendarProviderSyncState.FAILED
    }
}

data class UnifiedCalendarSyncSummary(
    val syncedCount: Int,
    val failedCount: Int,
    val pendingDeleteCount: Int,
    val completedDeleteCount: Int,
    val importedCount: Int,
    val microsoftPendingCount: Int,
    val isMicrosoftConfigured: Boolean,
    val microsoftFailureCode: com.luistureo.voicereminderapp.core.calendar.microsoft.MicrosoftCalendarErrorCode? = null,
    val googleFailureCode: com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarErrorCode? = null,
    val microsoftRetryAfterMillis: Long? = null
)
