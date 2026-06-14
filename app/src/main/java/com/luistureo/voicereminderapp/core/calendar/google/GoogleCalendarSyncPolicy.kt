package com.luistureo.voicereminderapp.core.calendar.google

object GoogleCalendarSyncPolicy {
    fun shouldSkipUpsertForPendingDelete(
        eventId: String?,
        pendingDeleteIds: Set<String>
    ): Boolean {
        return !eventId.isNullOrBlank() && eventId in pendingDeleteIds
    }

    fun shouldSyncPendingDeletes(hasAccessToken: Boolean): Boolean {
        return hasAccessToken
    }
}
