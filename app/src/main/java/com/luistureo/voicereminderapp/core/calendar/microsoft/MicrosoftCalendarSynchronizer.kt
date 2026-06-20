package com.luistureo.voicereminderapp.core.calendar.microsoft

import android.content.Context
import com.luistureo.voicereminderapp.core.calendar.unified.MeetingUrlPolicy
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarMeetingMergePolicy
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarSyncLogger
import com.luistureo.voicereminderapp.core.calendar.unified.UnifiedCalendarSyncPolicy
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarEventFingerprint
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

class MicrosoftCalendarSynchronizer(
    private val gateway: MicrosoftCalendarGateway = NotConfiguredMicrosoftCalendarGateway,
    private val reminderRepository: ReminderRepository? = null
) {
    private val scheduleStateResolver = ReminderScheduleStateResolver(ReminderOccurrenceCalculator())
    private var lastSyncFailureCode: MicrosoftCalendarErrorCode? = null
    private var lastRetryAfterMillis: Long? = null

    val failureCode: MicrosoftCalendarErrorCode?
        get() = lastSyncFailureCode

    val retryAfterMillis: Long?
        get() = lastRetryAfterMillis

    fun clearFailure() {
        lastSyncFailureCode = null
        lastRetryAfterMillis = null
    }

    val isConfigured: Boolean
        get() = gateway.isConfigured

    val isConnected: Boolean
        get() = gateway.isConnected

    suspend fun syncSavedReminder(reminder: Reminder): Reminder {
        CalendarSyncLogger.syncStarted(
            provider = CalendarProvider.MICROSOFT_CALENDAR,
            action = "sync_saved_reminder",
            pendingCreateCount = reminder.pendingCreateProviders.size,
            pendingUpdateCount = reminder.pendingUpdateProviders.size,
            pendingDeleteCount = reminder.pendingDeleteProviders.size
        )
        if (!gateway.isConnected) {
            CalendarSyncLogger.fallback(
                provider = CalendarProvider.MICROSOFT_CALENDAR,
                action = "sync_saved_reminder",
                fallbackReason = if (gateway.isConfigured) "not_connected" else "missing_config"
            )
            return UnifiedCalendarSyncPolicy.markProviderSyncFailed(
                reminder = reminder,
                provider = CalendarProvider.MICROSOFT_CALENDAR,
                state = CalendarProviderSyncState.NOT_CONNECTED
            )
        }

        return runCatching {
            val eventId = gateway.upsertReminder(reminder)
            UnifiedCalendarSyncPolicy.markProviderSynced(
                reminder = reminder,
                provider = CalendarProvider.MICROSOFT_CALENDAR,
                externalId = eventId,
                syncedAtEpochMillis = System.currentTimeMillis()
            ).copy(
                microsoftCalendarLastSyncAtEpochMillis = System.currentTimeMillis()
            ).also {
                CalendarSyncLogger.syncFinished(
                    provider = CalendarProvider.MICROSOFT_CALENDAR,
                    action = "sync_saved_reminder",
                    syncedCount = 1
                )
            }
        }.getOrElse { exception ->
            lastSyncFailureCode = MicrosoftCalendarErrorCode.fromFailure(exception)
            lastRetryAfterMillis = (exception as? MicrosoftGraphApiException)
                ?.retryAfterSeconds?.times(1_000L)
            CalendarSyncLogger.error(
                provider = CalendarProvider.MICROSOFT_CALENDAR,
                action = "sync_saved_reminder",
                fallbackReason = exception.javaClass.simpleName
            )
            UnifiedCalendarSyncPolicy.markProviderSyncFailed(
                reminder = reminder,
                provider = CalendarProvider.MICROSOFT_CALENDAR,
                state = CalendarProviderSyncState.FAILED
            )
        }
    }

    suspend fun deleteReminderEvent(reminder: Reminder): Reminder {
        val eventId = reminder.externalIdsByProvider[CalendarProvider.MICROSOFT_CALENDAR]
            ?.takeIf { it.isNotBlank() }
            ?: return reminder
        CalendarSyncLogger.syncStarted(
            provider = CalendarProvider.MICROSOFT_CALENDAR,
            action = "delete_event",
            pendingDeleteCount = reminder.pendingDeleteProviders.size
        )
        if (!gateway.isConnected) {
            CalendarSyncLogger.fallback(
                provider = CalendarProvider.MICROSOFT_CALENDAR,
                action = "delete_event",
                fallbackReason = if (gateway.isConfigured) "not_connected" else "missing_config"
            )
            return UnifiedCalendarSyncPolicy.markProviderPendingDelete(
                reminder = reminder,
                provider = CalendarProvider.MICROSOFT_CALENDAR
            )
        }

        return runCatching {
            gateway.deleteEvent(eventId)
            CalendarSyncLogger.syncFinished(
                provider = CalendarProvider.MICROSOFT_CALENDAR,
                action = "delete_event",
                completedDeleteCount = 1
            )
            UnifiedCalendarSyncPolicy.markProviderDeleteSynced(
                reminder = reminder,
                provider = CalendarProvider.MICROSOFT_CALENDAR
            )
        }.getOrElse { exception ->
            lastSyncFailureCode = MicrosoftCalendarErrorCode.fromFailure(exception)
            lastRetryAfterMillis = (exception as? MicrosoftGraphApiException)
                ?.retryAfterSeconds?.times(1_000L)
            CalendarSyncLogger.error(
                provider = CalendarProvider.MICROSOFT_CALENDAR,
                action = "delete_event",
                fallbackReason = exception.javaClass.simpleName
            )
            UnifiedCalendarSyncPolicy.markProviderPendingDelete(
                reminder = reminder,
                provider = CalendarProvider.MICROSOFT_CALENDAR
            )
        }
    }

    suspend fun importEvents(
        timeMin: Instant,
        timeMax: Instant
    ): MicrosoftCalendarImportSummary {
        CalendarSyncLogger.syncStarted(
            provider = CalendarProvider.MICROSOFT_CALENDAR,
            action = "import_events"
        )
        if (!gateway.isConnected) {
            CalendarSyncLogger.fallback(
                provider = CalendarProvider.MICROSOFT_CALENDAR,
                action = "import_events",
                fallbackReason = if (gateway.isConfigured) "not_connected" else "missing_config"
            )
            return MicrosoftCalendarImportSummary(
                importedCount = 0,
                skippedCount = 0,
                isConfigured = gateway.isConfigured
            )
        }

        val events = gateway.listEvents(timeMin, timeMax)
        val repository = reminderRepository
        if (repository == null) {
            return MicrosoftCalendarImportSummary(
                importedCount = 0,
                skippedCount = events.size,
                isConfigured = gateway.isConfigured
            )
        }

        val allReminders = repository.getAllRemindersIncludingHidden().toMutableList()
        val existingByExternalId = allReminders
            .mapNotNull { reminder ->
                reminder.externalIdsByProvider[CalendarProvider.MICROSOFT_CALENDAR]
                    ?.let { externalId -> externalId to reminder }
            }
            .toMap()
            .toMutableMap()
        var importedCount = 0
        var updatedCount = 0
        var skippedCount = 0

        events.forEach { event ->
            val existingReminder = existingByExternalId[event.id]
                ?: CalendarMeetingMergePolicy.findExisting(
                    reminders = allReminders,
                    importingProvider = CalendarProvider.MICROSOFT_CALENDAR,
                    eventId = event.id,
                    title = event.title,
                    scheduledAtEpochMillis = event.startAtEpochMillis,
                    isManagedCopy = event.isManagedCopy,
                    originProviderHint = event.originProviderHint,
                    localIdHint = event.localIdHint
                )
            if (existingReminder?.hiddenFromApp == true) {
                skippedCount++
                return@forEach
            }
            if (
                existingReminder?.lastEditedSource == CalendarProvider.APP &&
                CalendarProvider.MICROSOFT_CALENDAR in existingReminder.pendingUpdateProviders
            ) {
                skippedCount++
                return@forEach
            }

            val importedReminder = event.toReminder(existingReminder)
            if (
                existingReminder != null &&
                !existingReminder.externalIdsByProvider[CalendarProvider.MICROSOFT_CALENDAR]
                    .isNullOrBlank() &&
                !shouldUpdateFromMicrosoft(existingReminder, event, importedReminder)
            ) {
                skippedCount++
                return@forEach
            }
            if (existingReminder == null) {
                val insertedId = repository.insertReminder(importedReminder)
                val insertedReminder = importedReminder.copy(id = insertedId)
                existingByExternalId[event.id] = insertedReminder
                allReminders += insertedReminder
                importedCount++
            } else if (importedReminder != existingReminder) {
                repository.updateReminder(importedReminder)
                existingByExternalId[event.id] = importedReminder
                val reminderIndex = allReminders.indexOfFirst { it.id == importedReminder.id }
                if (reminderIndex >= 0) allReminders[reminderIndex] = importedReminder
                importedCount++
                updatedCount++
                CalendarSyncLogger.meetingMetadata(
                    provider = CalendarProvider.MICROSOFT_CALENDAR,
                    originalProvider = importedReminder.originProvider,
                    persisted = importedReminder.meetingUrl != null,
                    synchronizedProvidersCount = importedReminder.syncedProviders.size,
                    pendingProvidersCount = importedReminder.pendingProviders.size,
                    mergePreservedMeetingLink = existingReminder.meetingUrl == null ||
                            importedReminder.meetingUrl != null
                )
            } else {
                skippedCount++
            }
        }

        CalendarSyncLogger.syncFinished(
            provider = CalendarProvider.MICROSOFT_CALENDAR,
            action = "import_events",
            syncedCount = importedCount,
            importedCount = importedCount - updatedCount,
            updatedCount = updatedCount,
            skippedDuplicatesCount = skippedCount
        )
        return MicrosoftCalendarImportSummary(
            importedCount = importedCount,
            skippedCount = skippedCount,
            isConfigured = gateway.isConfigured
        )
    }

    private fun shouldUpdateFromMicrosoft(
        reminder: Reminder,
        event: MicrosoftCalendarEvent,
        importedReminder: Reminder
    ): Boolean {
        val eventUpdatedAt = event.updatedAtEpochMillis ?: return false
        val lastSyncedAt = reminder.microsoftCalendarLastSyncAtEpochMillis ?: 0L
        if (eventUpdatedAt <= lastSyncedAt) return false
        val storedFingerprint = reminder.syncedFingerprintsByProvider[
            CalendarProvider.MICROSOFT_CALENDAR
        ]
        val incomingFingerprint = CalendarEventFingerprint.fromReminder(importedReminder)
        return storedFingerprint == null || storedFingerprint != incomingFingerprint
    }

    private fun MicrosoftCalendarEvent.toReminder(existingReminder: Reminder?): Reminder {
        val scheduledAt = startAtEpochMillis ?: existingReminder?.scheduledAtEpochMillis
            ?: System.currentTimeMillis()
        val resolvedTitle = title.ifBlank { "Evento de Microsoft Calendar" }
        val cleanedMeetingUrl = meetingUrl?.takeIf(MeetingUrlPolicy::isSupportedMeetingUrl)
        val resolvedDetail = CalendarEventFingerprint.normalizeDetail(detail).ifBlank {
            if (cleanedMeetingUrl != null || isOnlineMeeting) {
                "Reunión de Microsoft Teams"
            } else if (isAllDay) {
                "Evento de Microsoft Calendar de dia completo."
            } else {
                "Evento de Microsoft Calendar."
            }
        }
        val googleExternalId = existingReminder
            ?.externalIdsByProvider
            ?.get(CalendarProvider.GOOGLE_CALENDAR)
        if (cleanedMeetingUrl != null) {
            CalendarSyncLogger.meetingLinkDetected(CalendarProvider.MICROSOFT_CALENDAR)
        }
        val resolvedOriginProvider = existingReminder?.originProvider
            ?: originProviderHint
            ?: CalendarProvider.MICROSOFT_CALENDAR
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
            googleCalendarEventId = existingReminder?.googleCalendarEventId,
            googleCalendarSyncState = existingReminder?.googleCalendarSyncState
                ?: GoogleCalendarSyncState.PENDING,
            googleCalendarLastSyncAtEpochMillis = existingReminder
                ?.googleCalendarLastSyncAtEpochMillis,
            microsoftCalendarLastSyncAtEpochMillis = updatedAtEpochMillis
                ?: existingReminder?.microsoftCalendarLastSyncAtEpochMillis
                ?: System.currentTimeMillis(),
            microsoftCalendarEventId = id,
            externalIdsByProvider = existingReminder?.externalIdsByProvider.orEmpty() +
                    (CalendarProvider.MICROSOFT_CALENDAR to id),
            originProvider = resolvedOriginProvider,
            syncedProviders = existingReminder?.syncedProviders.orEmpty() +
                    CalendarProvider.APP +
                    CalendarProvider.MICROSOFT_CALENDAR,
            providerSyncStates = existingReminder?.providerSyncStates.orEmpty() +
                    (CalendarProvider.MICROSOFT_CALENDAR to CalendarProviderSyncState.SYNCED),
            syncedFingerprintsByProvider = existingReminder
                ?.syncedFingerprintsByProvider
                .orEmpty(),
            pendingCreateProviders = if (googleExternalId.isNullOrBlank()) {
                (existingReminder?.pendingCreateProviders.orEmpty() -
                        CalendarProvider.MICROSOFT_CALENDAR) + CalendarProvider.GOOGLE_CALENDAR
            } else {
                existingReminder?.pendingCreateProviders.orEmpty() -
                        CalendarProvider.MICROSOFT_CALENDAR
            },
            pendingUpdateProviders = if (googleExternalId.isNullOrBlank()) {
                existingReminder?.pendingUpdateProviders.orEmpty() -
                        CalendarProvider.MICROSOFT_CALENDAR
            } else {
                (existingReminder?.pendingUpdateProviders.orEmpty() -
                        CalendarProvider.MICROSOFT_CALENDAR) + CalendarProvider.GOOGLE_CALENDAR
            },
            pendingDeleteProviders = existingReminder?.pendingDeleteProviders.orEmpty(),
            meetingUrl = resolvedMeetingUrl,
            meetingProvider = MeetingUrlPolicy.providerForUrl(resolvedMeetingUrl)
                ?: meetingProvider
                ?: existingReminder?.meetingProvider,
            isOnlineMeeting = existingReminder?.isOnlineMeeting == true ||
                    isOnlineMeeting || cleanedMeetingUrl != null,
            meetingUrlsByProvider = meetingUrlsByProvider,
            isSuspended = existingReminder?.isSuspended ?: false,
            suspendedOccurrenceAtEpochMillis = existingReminder
                ?.suspendedOccurrenceAtEpochMillis,
            lastEditedSource = CalendarProvider.MICROSOFT_CALENDAR,
            externalEditNote = if (existingReminder == null) {
                null
            } else {
                "Cita editada desde Microsoft Calendar"
            },
            isAllDay = isAllDay,
            hiddenFromApp = false
        )

        val resolvedReminder = if (isAllDay) {
            baseReminder
        } else {
            baseReminder.copy(scheduleState = scheduleStateResolver.resolveOnSave(baseReminder))
        }
        return resolvedReminder.copy(
            syncedFingerprintsByProvider = resolvedReminder.syncedFingerprintsByProvider +
                    (CalendarProvider.MICROSOFT_CALENDAR to
                            CalendarEventFingerprint.fromReminder(resolvedReminder))
        )
    }
}

interface MicrosoftCalendarGateway {
    val isConfigured: Boolean
    val isConnected: Boolean

    suspend fun upsertReminder(reminder: Reminder): String

    suspend fun deleteEvent(eventId: String)

    suspend fun listEvents(
        timeMin: Instant,
        timeMax: Instant
    ): List<MicrosoftCalendarEvent>
}

object NotConfiguredMicrosoftCalendarGateway : MicrosoftCalendarGateway {
    override val isConfigured: Boolean = false
    override val isConnected: Boolean = false

    override suspend fun upsertReminder(reminder: Reminder): String {
        throw MicrosoftCalendarNotConfiguredException
    }

    override suspend fun deleteEvent(eventId: String) {
        throw MicrosoftCalendarNotConfiguredException
    }

    override suspend fun listEvents(
        timeMin: Instant,
        timeMax: Instant
    ): List<MicrosoftCalendarEvent> {
        return emptyList()
    }
}

data class MicrosoftCalendarEvent(
    val id: String,
    val title: String,
    val detail: String,
    val startAtEpochMillis: Long?,
    val isAllDay: Boolean,
    val isCompleted: Boolean = false,
    val isUrgent: Boolean = false,
    val meetingUrl: String? = null,
    val meetingProvider: CalendarProvider? = null,
    val isOnlineMeeting: Boolean = false,
    val originProviderHint: CalendarProvider? = null,
    val isManagedCopy: Boolean = false,
    val localIdHint: Int? = null,
    val updatedAtEpochMillis: Long? = null
)

object MicrosoftCalendarNotConfiguredException :
    IllegalStateException("Microsoft Graph OAuth no esta configurado.")

data class MicrosoftCalendarImportSummary(
    val importedCount: Int,
    val skippedCount: Int,
    val isConfigured: Boolean
)

object MicrosoftCalendarSyncProvider {
    fun create(
        context: Context,
        reminderRepository: ReminderRepository
    ): MicrosoftCalendarSynchronizer {
        val authController = MicrosoftCalendarAuthProvider.get(context)
        return MicrosoftCalendarSynchronizer(
            gateway = MicrosoftGraphCalendarGateway.create(context, authController),
            reminderRepository = reminderRepository
        )
    }
}
