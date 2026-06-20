package com.luistureo.voicereminderapp.core.calendar.google

import android.content.Context

interface GoogleCalendarConnectionStateStore {
    var syncEnabled: Boolean
    var connected: Boolean
    var hasError: Boolean
    var errorCode: String?
    var authPending: Boolean
    var accountKey: String?
}

class SharedPreferencesGoogleCalendarConnectionStateStore(context: Context) :
    GoogleCalendarConnectionStateStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override var syncEnabled: Boolean
        get() = preferences.getBoolean(KEY_SYNC_ENABLED, true)
        set(value) {
            preferences.edit().putBoolean(KEY_SYNC_ENABLED, value).apply()
        }

    override var connected: Boolean
        get() = preferences.getBoolean(KEY_CONNECTED, false)
        set(value) {
            preferences.edit().putBoolean(KEY_CONNECTED, value).apply()
        }

    override var hasError: Boolean
        get() = preferences.getBoolean(KEY_HAS_ERROR, false)
        set(value) {
            preferences.edit().putBoolean(KEY_HAS_ERROR, value).apply()
        }

    override var errorCode: String?
        get() = preferences.getString(KEY_ERROR_CODE, null)
        set(value) {
            preferences.edit().apply {
                if (value == null) remove(KEY_ERROR_CODE) else putString(KEY_ERROR_CODE, value)
            }.apply()
        }

    override var authPending: Boolean
        get() = preferences.getBoolean(KEY_AUTH_PENDING, false)
        set(value) {
            preferences.edit().putBoolean(KEY_AUTH_PENDING, value).apply()
        }

    override var accountKey: String?
        get() = preferences.getString(KEY_ACCOUNT_KEY, null)
        set(value) {
            preferences.edit().apply {
                if (value == null) remove(KEY_ACCOUNT_KEY) else putString(KEY_ACCOUNT_KEY, value)
            }.apply()
        }

    private companion object {
        const val PREFERENCES_NAME = "google_calendar_connection_state"
        const val KEY_SYNC_ENABLED = "sync_enabled"
        const val KEY_CONNECTED = "connected"
        const val KEY_HAS_ERROR = "has_error"
        const val KEY_ERROR_CODE = "error_code"
        const val KEY_AUTH_PENDING = "auth_pending"
        const val KEY_ACCOUNT_KEY = "account_key"
    }
}

enum class GoogleCalendarReconnectDecision {
    REUSE_VALID_SESSION,
    NEEDS_LOGIN
}

object GoogleCalendarConnectionPolicy {
    fun reconnectDecision(
        hasAccount: Boolean,
        hasCalendarPermission: Boolean,
        hasStableAccountId: Boolean
    ): GoogleCalendarReconnectDecision {
        return if (hasAccount && hasCalendarPermission && hasStableAccountId) {
            GoogleCalendarReconnectDecision.REUSE_VALID_SESSION
        } else {
            GoogleCalendarReconnectDecision.NEEDS_LOGIN
        }
    }
}
