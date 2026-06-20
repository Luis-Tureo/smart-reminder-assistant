package com.luistureo.voicereminderapp.core.calendar.microsoft

import android.content.Context

interface MicrosoftCalendarConnectionStateStore {
    var syncEnabled: Boolean
    var hasSession: Boolean
    var errorCode: String?
    var accountKey: String?
}

class SharedPreferencesMicrosoftCalendarConnectionStateStore(context: Context) :
    MicrosoftCalendarConnectionStateStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override var syncEnabled: Boolean
        get() = preferences.getBoolean(KEY_SYNC_ENABLED, true)
        set(value) = preferences.edit().putBoolean(KEY_SYNC_ENABLED, value).apply()

    override var hasSession: Boolean
        get() = preferences.getBoolean(KEY_HAS_SESSION, false)
        set(value) = preferences.edit().putBoolean(KEY_HAS_SESSION, value).apply()

    override var errorCode: String?
        get() = preferences.getString(KEY_ERROR_CODE, null)
        set(value) {
            preferences.edit().apply {
                if (value == null) remove(KEY_ERROR_CODE) else putString(KEY_ERROR_CODE, value)
            }.apply()
        }

    override var accountKey: String?
        get() = preferences.getString(KEY_ACCOUNT_KEY, null)
        set(value) {
            preferences.edit().apply {
                if (value == null) remove(KEY_ACCOUNT_KEY) else putString(KEY_ACCOUNT_KEY, value)
            }.apply()
        }

    private companion object {
        const val PREFERENCES_NAME = "microsoft_calendar_connection_state"
        const val KEY_SYNC_ENABLED = "sync_enabled"
        const val KEY_HAS_SESSION = "has_session"
        const val KEY_ERROR_CODE = "error_code"
        const val KEY_ACCOUNT_KEY = "account_key"
    }
}
