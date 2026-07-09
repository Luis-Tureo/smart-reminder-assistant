package com.luistureo.voicereminderapp.core.calendar.google

import android.content.Context
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarSyncLogger
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarEventFingerprint
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarMeetingMergePolicy
import com.luistureo.voicereminderapp.core.calendar.unified.MeetingUrlPolicy
import com.luistureo.voicereminderapp.core.calendar.unified.UnifiedCalendarSyncPolicy
import com.luistureo.voicereminderapp.core.reminder.ReminderOccurrenceCalculator
import com.luistureo.voicereminderapp.core.reminder.ReminderScheduleStateResolver
import com.luistureo.voicereminderapp.core.utils.ReminderTypeResolver
import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import com.luistureo.voicereminderapp.domain.model.CalendarProviderSyncState
import com.luistureo.voicereminderapp.domain.model.GoogleCalendarSyncState
import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.model.ReminderScheduleState
import com.luistureo.voicereminderapp.domain.model.ReminderSource
import com.luistureo.voicereminderapp.domain.repository.ReminderRepository
import java.time.Instant
import java.time.ZoneId

class GoogleCalendarReminderSynchronizer(
    context: Context,
    private val reminderRepository: ReminderRepository,
    private val authManager: GoogleCalendarAuthManager = GoogleCalendarAuthManager(context),
    private val calendarClient: GoogleCalendarRestClient = GoogleCalendarRestClient.create(context),
    private val pendingOperationStore: GoogleCalendarPendingOperationStore =
        GoogleCalendarPendingOperationStore(context)
) {
    private val zoneId = ZoneId.systemDefault()
    private val scheduleStateResolver = ReminderScheduleStateResolver(ReminderOccurrenceCalculator())
    private var lastSyncFailureCode: GoogleCalendarErrorCode? = null

    val isConnected: Boolean
        get() = authManager.isConnected()

    suspend fun syncSavedReminder(reminder: Reminder): Reminder {
        CalendarSyncLogger.syncStarted(
            provider = CalendarProvider.GOOGLE_CALENDAR,
            action = "sync_saved_reminder",
            pendingCreateCount = reminder.pendingCreateProviders.size,
            pendingUpdateCount = reminder.pendingUpdateProviders.size,
            pendingDeleteCount = reminder.pendingDeleteProviders.size
        )
        if (
            GoogleCalendarSyncPolicy.shouldSkipUpsertForPendingDelete(
                eventId = reminder.googleCalendarEventId,
                pendingDeleteIds = pendingOperationStore.getPendingDeleteIds()
            )
        ) {
            CalendarSyncLogger.fallback(
                provider = CalendarProvider.GOOGLE_CALENDAR,
                action = "sync_saved_reminder",
                fallbackReason = "pending_delete_blocks_upsert"
            )
            return reminder.copy(googleCalendarSyncState = GoogleCalendarSyncState.PENDING)
        }

        return try {
            val accessToken = authManager.getAccessToken()
            val eventId = upsertEvent(accessToken, reminder)
            val syncedReminder = UnifiedCalendarSyncPolicy.markProviderSynced(
                reminder = reminder,
                provider = CalendarProvider.GOOGLE_CALENDAR,
                externalId = eventId,
                syncedAtEpochMillis = System.currentTimeMillis()
            )
            reminderRepository.updateReminder(syncedReminder)
            CalendarSyncLogger.syncFinished(
                provider = CalendarProvider.GOOGLE_CALENDAR,
                action = "sync_saved_reminder",
                syncedCount = 1
            )
            syncedReminder
        } catch (exception: Exception) {
            lastSyncFailureCode = GoogleCalendarErrorCode.fromSyncFailure(exception)
            val failedReminder = UnifiedCalendarSyncPolicy.markProviderSyncFailed(
                reminder = reminder,
                provider = CalendarProvider.GOOGLE_CALENDAR,
                state = exception.toProviderSyncState()
            ).copy(
                googleCalendarSyncState = exception.toSyncState()
            )
            reminderRepository.updateReminder(failedReminder)
            CalendarSyncLogger.error(
                provider = CalendarProvider.GOOGLE_CALENDAR,
                action = "sync_saved_reminder",
                fallbackReason = exception.javaClass.simpleName
            )
            failedReminder
        }
    }

    suspend fun deleteReminderEvent(reminder: Reminder): Reminder {
        val eventId = reminder.externalIdsByProvider[CalendarProvider.GOOGLE_CALENDAR]
            ?.takeIf { it.isNotBlank() }
            ?: reminder.googleCalendarEventId?.takeIf { it.isNotBlank() }
            ?: return UnifiedCalendarSyncPolicy.markProviderDeleteSynced(
                reminder,
                CalendarProvider.GOOGLE_CALENDAR
            )
        CalendarSyncLogger.syncStarted(
            provider = CalendarProvider.GOOGLE_CALENDAR,
            action = "delete_event",
            pendingDeleteCount = reminder.pendingDeleteProviders.size
        )

        return try {
            val accessToken = authManager.getAccessToken()
            calendarClient.deleteReminderEvent(accessToken, eventId)
            pendingOperationStore.removePendingDelete(eventId)
            CalendarSyncLogger.syncFinished(
                provider = CalendarProvider.GOOGLE_CALENDAR,
                action = "delete_event",
                completedDeleteCount = 1
            )
            UnifiedCalendarSyncPolicy.markProviderDeleteSynced(
                reminder,
                CalendarProvider.GOOGLE_CALENDAR
            ).copy(
                googleCalendarEventId = null,
                googleCalendarSyncState = GoogleCalendarSyncState.PENDING,
                googleCalendarLastSyncAtEpochMillis = null
            )
        } catch (exception: Exception) {
            lastSyncFailureCode = GoogleCalendarErrorCode.fromSyncFailure(exception)
            pendingOperationStore.addPendingDelete(eventId)
            CalendarSyncLogger.fallback(
                provider = CalendarProvider.GOOGLE_CALENDAR,
                action = "delete_event",
                fallbackReason = "delete_failed_pending_retry"
            )
            UnifiedCalendarSyncPolicy.markProviderPendingDelete(
                reminder,
                CalendarProvider.GOOGLE_CALENDAR
            )
        }
    }

    suspend fun deleteEventById(eventId: String) {
        if (eventId.isBlank()) return
        CalendarSyncLogger.syncStarted(
            provider = CalendarProvider.GOOGLE_CALENDAR,
            action = "delete_event",
            pendingDeleteCount = pendingOperationStore.getPendingDeleteIds().size
        )
        try {
            val accessToken = authManager.getAccessToken()
            calendarClient.deleteReminderEvent(accessToken, eventId)
            pendingOperationStore.removePendingDelete(eventId)
            CalendarSyncLogger.syncFinished(
                provider = CalendarProvider.GOOGLE_CALENDAR,
                action = "delete_event",
                completedDeleteCount = 1,
                pendingCount = pendingOperationStore.getPendingDeleteIds().size
            )
        } catch (_: Exception) {
            pendingOperationStore.addPendingDelete(eventId)
            CalendarSyncLogger.fallback(
                provider = CalendarProvider.GOOGLE_CALENDAR,
                action = "delete_event",
                fallbackReason = "delete_failed_pending_retry"
            )
        }
    }

    suspend fun syncPendingReminders(): GoogleCalendarSyncSummary {
        lastSyncFailureCode = null
        val reminders = reminderRepository.getAllRemindersIncludingHidden().toMutableList()
        val pendingReminders = reminders.filter { reminder ->
            !reminder.hiddenFromApp && (
                    CalendarProvider.GOOGLE_CALENDAR in reminder.pendingCreateProviders ||
                            CalendarProvider.GOOGLE_CALENDAR in reminder.pendingUpdateProviders
                    )
        }
        val pendingDeletedReminders = reminders.filter {
            CalendarProvider.GOOGLE_CALENDAR in it.pendingDeleteProviders
        }
        CalendarSyncLogger.syncStarted(
            provider = CalendarProvider.GOOGLE_CALENDAR,
            action = "sync_pending",
            pendingCreateCount = pendingReminders.count { it.googleCalendarEventId.isNullOrBlank() },
            pendingUpdateCount = pendingReminders.count { !it.googleCalendarEventId.isNullOrBlank() },
            pendingDeleteCount = pendingOperationStore.getPendingDeleteIds().size
        )
        var syncedCount = 0
        var failedCount = 0

        val modelPendingDeleteIds = pendingDeletedReminders.mapNotNull {
            it.externalIdsByProvider[CalendarProvider.GOOGLE_CALENDAR]
                ?: it.googleCalendarEventId
        }.toSet()
        var completedDeleteCount = syncPendingDeletesSafely(modelPendingDeleteIds)
        var remainingModelDeleteCount = 0

        pendingDeletedReminders.forEach { reminder ->
            val deletedReminder = deleteReminderEvent(reminder)
            if (CalendarProvider.GOOGLE_CALENDAR !in deletedReminder.pendingDeleteProviders) {
                completedDeleteCount++
            } else {
                remainingModelDeleteCount++
            }
            if (deletedReminder.hiddenFromApp && deletedReminder.pendingDeleteProviders.isEmpty()) {
                reminderRepository.deleteReminder(deletedReminder)
            } else if (deletedReminder != reminder) {
                reminderRepository.updateReminder(deletedReminder)
            }
        }

        pendingReminders.forEach { reminder ->
            val syncedReminder = syncSavedReminder(reminder)
            if (syncedReminder.googleCalendarSyncState == GoogleCalendarSyncState.SYNCED) {
                syncedCount++
            } else {
                failedCount++
            }
        }

        val summary = GoogleCalendarSyncSummary(
            syncedCount = syncedCount,
            failedCount = failedCount,
            pendingDeleteCount = pendingOperationStore.getPendingDeleteIds()
                .count { it !in modelPendingDeleteIds } +
                    remainingModelDeleteCount,
            completedDeleteCount = completedDeleteCount,
            failureCode = lastSyncFailureCode
        )
        CalendarSyncLogger.syncFinished(
            provider = CalendarProvider.GOOGLE_CALENDAR,
            action = "sync_pending",
            syncedCount = summary.syncedCount,
            failedCount = summary.failedCount,
            pendingCount = summary.pendingDeleteCount,
            completedDeleteCount = summary.completedDeleteCount
        )
        return summary
    }

    suspend fun importEvents(
        timeMin: Instant,
        timeMax: Instant
    ): Int {
        CalendarSyncLogger.syncStarted(provider = CalendarProvider.GOOGLE_CALENDAR, action = "import_events")
        val accessToken = authManager.getAccessToken()
        val googleEvents = calendarClient.listEvents(
            accessToken = accessToken,
            timeMin = timeMin,
            timeMax = timeMax,
            accountKey = authManager.syncAccountKey() ?: "single_account"
        )
        val reminders = reminderRepository.getAllRemindersIncludingHidden().toMutableList()
        var importedCount = 0
        var updatedCount = 0

        googleEvents.forEach { event ->
            val existingReminder = CalendarMeetingMergePolicy.findExisting(
                reminders = reminders,
                importingProvider = CalendarProvider.GOOGLE_CALENDAR,
                eventId = event.id,
                title = event.title,
                scheduledAtEpochMillis = event.resolveScheduledAtEpochMillis(),
                isManagedCopy = event.isManagedCopy,
                originProviderHint = event.originProviderHint,
                localIdHint = event.localIdHint
            )
            val importedReminder = event.toReminder(existingReminder)

            if (existingReminder == null) {
                val insertedId = reminderRepository.insertReminder(importedReminder)
                val insertedReminder = importedReminder.copy(id = insertedId)
                reminderRepository.updateReminder(insertedReminder)
                reminders += insertedReminder
                importedCount++
            } else if (
                !existingReminder.hiddenFromApp &&
                (existingReminder.externalIdsByProvider[CalendarProvider.GOOGLE_CALENDAR]
                    .isNullOrBlank() ||
                        shouldUpdateFromGoogle(existingReminder, event, importedReminder))
            ) {
                reminderRepository.updateReminder(importedReminder)
                val reminderIndex = reminders.indexOfFirst { it.id == importedReminder.id }
                if (reminderIndex >= 0) reminders[reminderIndex] = importedReminder
                importedCount++
                updatedCount++
                CalendarSyncLogger.meetingMetadata(
                    provider = CalendarProvider.GOOGLE_CALENDAR,
                    originalProvider = importedReminder.originProvider,
                    persisted = importedReminder.meetingUrl != null,
                    synchronizedProvidersCount = importedReminder.syncedProviders.size,
                    pendingProvidersCount = importedReminder.pendingProviders.size,
                    mergePreservedMeetingLink = existingReminder.meetingUrl == null ||
                            importedReminder.meetingUrl != null
                )
            }
        }

        CalendarSyncLogger.syncFinished(
            provider = CalendarProvider.GOOGLE_CALENDAR,
            action = "import_events",
            syncedCount = importedCount,
            importedCount = importedCount - updatedCount,
            updatedCount = updatedCount,
            skippedDuplicatesCount = googleEvents.size - importedCount
        )
        return importedCount
    }

    private suspend fun syncPendingDeletesSafely(excludedEventIds: Set<String> = emptySet()): Int {
        val tokenResult = runCatching { authManager.getAccessToken() }
        val accessToken = tokenResult.getOrNull() ?: run {
            tokenResult.exceptionOrNull()?.let { exception ->
                lastSyncFailureCode = GoogleCalendarErrorCode.fromSyncFailure(exception)
            }
            CalendarSyncLogger.fallback(
                provider = CalendarProvider.GOOGLE_CALENDAR,
                action = "sync_pending_deletes",
                fallbackReason = "access_token_unavailable"
            )
            return 0
        }
        if (!GoogleCalendarSyncPolicy.shouldSyncPendingDeletes(hasAccessToken = true)) return 0
        var completedDeleteCount = 0
        pendingOperationStore.getPendingDeleteIds()
            .filterNot(excludedEventIds::contains)
            .forEach { eventId ->
            runCatching {
                calendarClient.deleteReminderEvent(accessToken, eventId)
                pendingOperationStore.removePendingDelete(eventId)
                completedDeleteCount++
            }.onFailure { exception ->
                lastSyncFailureCode = GoogleCalendarErrorCode.fromSyncFailure(exception)
            }
        }
        return completedDeleteCount
    }

    private suspend fun upsertEvent(
        accessToken: String,
        reminder: Reminder
    ): String {
        val eventId = reminder.googleCalendarEventId

        if (eventId.isNullOrBlank()) {
            return calendarClient.createReminderEvent(accessToken, reminder)
        }

        return try {
            calendarClient.updateReminderEvent(accessToken, eventId, reminder)
        } catch (exception: GoogleCalendarApiException) {
            if (exception.code == 404 || exception.code == 410) {
                calendarClient.createReminderEvent(accessToken, reminder)
            } else {
                throw exception
            }
        }
    }

    private fun Exception.toSyncState(): GoogleCalendarSyncState {
        return when (this) {
            is GoogleCalendarAuthException -> GoogleCalendarSyncState.NOT_CONNECTED
            else -> GoogleCalendarSyncState.FAILED
        }
    }

    private fun Exception.toProviderSyncState(): CalendarProviderSyncState {
        return when (this) {
            is GoogleCalendarAuthException -> CalendarProviderSyncState.NOT_CONNECTED
            else -> CalendarProviderSyncState.FAILED
        }
    }

    private fun shouldUpdateFromGoogle(
        reminder: Reminder,
        event: GoogleCalendarEvent,
        importedReminder: Reminder
    ): Boolean {
        val eventUpdatedAt = event.updatedAtEpochMillis ?: return false
        val lastSyncedAt = reminder.googleCalendarLastSyncAtEpochMillis ?: 0L
        if (eventUpdatedAt <= lastSyncedAt) return false
        val storedFingerprint = reminder.syncedFingerprintsByProvider[
            CalendarProvider.GOOGLE_CALENDAR
        ]
        val incomingFingerprint = CalendarEventFingerprint.fromReminder(importedReminder)
        return storedFingerprint == null || storedFingerprint != incomingFingerprint
    }

    private fun GoogleCalendarEvent.toReminder(existingReminder: Reminder?): Reminder {
        val scheduledAt = resolveScheduledAtEpochMillis() ?: existingReminder?.scheduledAtEpochMillis
            ?: System.currentTimeMillis()
        val cleanedMeetingUrl = meetingUrl.takeIf(MeetingUrlPolicy::isSupportedMeetingUrl)
        if (cleanedMeetingUrl != null) {
            CalendarSyncLogger.meetingLinkDetected(CalendarProvider.GOOGLE_CALENDAR)
        }
        val resolvedOriginProvider = existingReminder?.originProvider
            ?: originProviderHint
            ?: CalendarProvider.GOOGLE_CALENDAR
        val detectedMeetingProvider = MeetingUrlPolicy.providerForUrl(cleanedMeetingUrl)
        val meetingUrlsByProvider = existingReminder?.meetingUrlsByProvider.orEmpty().let { urls ->
            if (cleanedMeetingUrl == null || detectedMeetingProvider == null) {
                urls
            } else {
                urls + (detectedMeetingProvider to cleanedMeetingUrl)
            }
        }
        val resolvedMeetingUrl = MeetingUrlPolicy.selectPreferredUrl(
            originProvider = resolvedOriginProvider,
            urlsByProvider = meetingUrlsByProvider,
            fallbackUrl = existingReminder?.meetingUrl
        )
        val resolvedTitle = title.ifBlank { "Evento de Google Calendar" }
        val resolvedDetail = CalendarEventFingerprint.normalizeDetail(description).ifBlank {
            if (cleanedMeetingUrl != null || isOnlineMeeting) {
                "Reunión de Google Meet"
            } else if (isAllDay) {
                "Evento de Google Calendar de dia completo."
            } else {
                "Evento de Google Calendar."
            }
        }
        val baseReminder = Reminder(
            id = existingReminder?.id ?: 0,
            title = resolvedTitle,
            detail = resolvedDetail,
            scheduledAtEpochMillis = scheduledAt,
            isCompleted = isCompleted,
            type = ReminderTypeResolver.resolve(resolvedTitle, resolvedDetail),
            isUrgent = isUrgent,
            source = existingReminder?.source ?: ReminderSource.MANUAL,
            recurrence = existingReminder?.recurrence,
            scheduleState = existingReminder?.scheduleState ?: ReminderScheduleState(),
            googleCalendarEventId = id,
            googleCalendarSyncState = GoogleCalendarSyncState.SYNCED,
            googleCalendarLastSyncAtEpochMillis = updatedAtEpochMillis ?: System.currentTimeMillis(),
            externalIdsByProvider = existingReminder?.externalIdsByProvider.orEmpty() +
                    (CalendarProvider.GOOGLE_CALENDAR to id),
            originProvider = resolvedOriginProvider,
            syncedProviders = existingReminder?.syncedProviders.orEmpty() +
                    CalendarProvider.APP +
                    CalendarProvider.GOOGLE_CALENDAR,
            providerSyncStates = existingReminder?.providerSyncStates.orEmpty() +
                    (CalendarProvider.GOOGLE_CALENDAR to CalendarProviderSyncState.SYNCED),
            syncedFingerprintsByProvider = existingReminder
                ?.syncedFingerprintsByProvider
                .orEmpty(),
            pendingCreateProviders = existingReminder?.pendingCreateProviders.orEmpty() -
                    CalendarProvider.GOOGLE_CALENDAR,
            pendingUpdateProviders = existingReminder?.pendingUpdateProviders.orEmpty() -
                    CalendarProvider.GOOGLE_CALENDAR,
            pendingDeleteProviders = existingReminder?.pendingDeleteProviders.orEmpty(),
            meetingUrl = resolvedMeetingUrl,
            meetingProvider = MeetingUrlPolicy.providerForUrl(resolvedMeetingUrl)
                ?: meetingProvider
                ?: existingReminder?.meetingProvider,
            isOnlineMeeting = existingReminder?.isOnlineMeeting == true ||
                    isOnlineMeeting || cleanedMeetingUrl != null,
            meetingUrlsByProvider = meetingUrlsByProvider,
            isSuspended = existingReminder?.isSuspended ?: false,
            suspendedOccurrenceAtEpochMillis = existingReminder?.suspendedOccurrenceAtEpochMillis,
            lastEditedSource = CalendarProvider.GOOGLE_CALENDAR,
            externalEditNote = if (existingReminder == null) {
                null
            } else {
                "Cita editada desde Google Calendar"
            },
            isAllDay = isAllDay,
            hiddenFromApp = false
        )

        val resolvedReminder = if (isAllDay) {
            baseReminder
        } else {
            baseReminder.copy(
                scheduleState = scheduleStateResolver.resolveOnSave(baseReminder)
            )
        }
        return resolvedReminder.copy(
            syncedFingerprintsByProvider = resolvedReminder.syncedFingerprintsByProvider +
                    (CalendarProvider.GOOGLE_CALENDAR to
                            CalendarEventFingerprint.fromReminder(resolvedReminder))
        )
    }

    private fun GoogleCalendarEvent.resolveScheduledAtEpochMillis(): Long? {
        if (isAllDay) {
            return startDate
                ?.atStartOfDay(zoneId)
                ?.toInstant()
                ?.toEpochMilli()
        }

        return startDateTime
            ?.withZoneSameInstant(zoneId)
            ?.toInstant()
            ?.toEpochMilli()
    }
}

data class GoogleCalendarSyncSummary(
    val syncedCount: Int,
    val failedCount: Int,
    val pendingDeleteCount: Int,
    val completedDeleteCount: Int,
    val failureCode: GoogleCalendarErrorCode? = null
)
