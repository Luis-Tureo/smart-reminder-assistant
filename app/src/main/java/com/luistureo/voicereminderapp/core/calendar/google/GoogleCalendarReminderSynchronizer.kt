package com.luistureo.voicereminderapp.core.calendar.google

import android.content.Context
import com.luistureo.voicereminderapp.domain.model.GoogleCalendarSyncState
import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.repository.ReminderRepository

class GoogleCalendarReminderSynchronizer(
    context: Context,
    private val reminderRepository: ReminderRepository,
    private val authManager: GoogleCalendarAuthManager = GoogleCalendarAuthManager(context),
    private val calendarClient: GoogleCalendarRestClient = GoogleCalendarRestClient(),
    private val pendingOperationStore: GoogleCalendarPendingOperationStore =
        GoogleCalendarPendingOperationStore(context)
) {

    suspend fun syncSavedReminder(reminder: Reminder): Reminder {
        if (
            GoogleCalendarSyncPolicy.shouldSkipUpsertForPendingDelete(
                eventId = reminder.googleCalendarEventId,
                pendingDeleteIds = pendingOperationStore.getPendingDeleteIds()
            )
        ) {
            return reminder.copy(googleCalendarSyncState = GoogleCalendarSyncState.PENDING)
        }

        return try {
            val accessToken = authManager.getAccessToken()
            val eventId = upsertEvent(accessToken, reminder)
            val syncedReminder = reminder.copy(
                googleCalendarEventId = eventId,
                googleCalendarSyncState = GoogleCalendarSyncState.SYNCED,
                googleCalendarLastSyncAtEpochMillis = System.currentTimeMillis()
            )
            reminderRepository.updateReminder(syncedReminder)
            syncedReminder
        } catch (exception: Exception) {
            val failedReminder = reminder.copy(
                googleCalendarSyncState = exception.toSyncState()
            )
            reminderRepository.updateReminder(failedReminder)
            failedReminder
        }
    }

    suspend fun deleteReminderEvent(reminder: Reminder) {
        val eventId = reminder.googleCalendarEventId?.takeIf { it.isNotBlank() } ?: return
        deleteEventById(eventId)
    }

    suspend fun deleteEventById(eventId: String) {
        if (eventId.isBlank()) return
        try {
            val accessToken = authManager.getAccessToken()
            calendarClient.deleteReminderEvent(accessToken, eventId)
            pendingOperationStore.removePendingDelete(eventId)
        } catch (_: Exception) {
            pendingOperationStore.addPendingDelete(eventId)
        }
    }

    suspend fun syncPendingReminders(): GoogleCalendarSyncSummary {
        val reminders = reminderRepository.getAllReminders()
        var syncedCount = 0
        var failedCount = 0

        syncPendingDeletesSafely()

        reminders
            .filter { reminder ->
                reminder.googleCalendarSyncState != GoogleCalendarSyncState.SYNCED ||
                        reminder.googleCalendarEventId.isNullOrBlank()
            }
            .forEach { reminder ->
                val syncedReminder = syncSavedReminder(reminder)
                if (syncedReminder.googleCalendarSyncState == GoogleCalendarSyncState.SYNCED) {
                    syncedCount++
                } else {
                    failedCount++
                }
            }

        return GoogleCalendarSyncSummary(
            syncedCount = syncedCount,
            failedCount = failedCount,
            pendingDeleteCount = pendingOperationStore.getPendingDeleteIds().size
        )
    }

    private suspend fun syncPendingDeletesSafely() {
        val accessToken = runCatching { authManager.getAccessToken() }.getOrNull() ?: return
        if (!GoogleCalendarSyncPolicy.shouldSyncPendingDeletes(hasAccessToken = true)) return
        pendingOperationStore.getPendingDeleteIds().toList().forEach { eventId ->
            runCatching {
                calendarClient.deleteReminderEvent(accessToken, eventId)
                pendingOperationStore.removePendingDelete(eventId)
            }
        }
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
}

data class GoogleCalendarSyncSummary(
    val syncedCount: Int,
    val failedCount: Int,
    val pendingDeleteCount: Int
)
