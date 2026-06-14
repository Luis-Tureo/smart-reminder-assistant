package com.luistureo.voicereminderapp.core.calendar.google

import android.content.Context

class GoogleCalendarPendingOperationStore(
    context: Context
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "google_calendar_pending_operations",
        Context.MODE_PRIVATE
    )

    fun addPendingDelete(eventId: String) {
        if (eventId.isBlank()) return

        val updatedIds = getPendingDeleteIds().toMutableSet().apply {
            add(eventId)
        }
        preferences.edit().putStringSet(KEY_PENDING_DELETES, updatedIds).apply()
    }

    fun getPendingDeleteIds(): Set<String> {
        return preferences.getStringSet(KEY_PENDING_DELETES, emptySet()).orEmpty()
    }

    fun removePendingDelete(eventId: String) {
        val updatedIds = getPendingDeleteIds().toMutableSet().apply {
            remove(eventId)
        }
        preferences.edit().putStringSet(KEY_PENDING_DELETES, updatedIds).apply()
    }

    fun hasPendingDelete(eventId: String?): Boolean {
        return !eventId.isNullOrBlank() && eventId in getPendingDeleteIds()
    }

    private companion object {
        const val KEY_PENDING_DELETES = "pending_delete_event_ids"
    }
}
