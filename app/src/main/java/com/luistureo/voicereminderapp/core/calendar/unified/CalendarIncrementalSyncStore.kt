package com.luistureo.voicereminderapp.core.calendar.unified

import android.content.Context
import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import java.security.MessageDigest

data class CalendarIncrementalCursor(
    val cursor: String,
    val rangeStartEpochMillis: Long,
    val rangeEndEpochMillis: Long,
    val fullSyncAtEpochMillis: Long
)

interface CalendarIncrementalSyncStore {
    fun get(
        provider: CalendarProvider,
        accountKey: String,
        calendarKey: String
    ): CalendarIncrementalCursor?

    fun save(
        provider: CalendarProvider,
        accountKey: String,
        calendarKey: String,
        cursor: CalendarIncrementalCursor
    )

    fun clear(provider: CalendarProvider, accountKey: String, calendarKey: String)
}

class SharedPreferencesCalendarIncrementalSyncStore(context: Context) :
    CalendarIncrementalSyncStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        "calendar_incremental_sync",
        Context.MODE_PRIVATE
    )

    override fun get(
        provider: CalendarProvider,
        accountKey: String,
        calendarKey: String
    ): CalendarIncrementalCursor? {
        val key = key(provider, accountKey, calendarKey)
        val cursor = preferences.getString("${key}_cursor", null)?.takeIf { it.isNotBlank() }
            ?: return null
        return CalendarIncrementalCursor(
            cursor = cursor,
            rangeStartEpochMillis = preferences.getLong("${key}_start", 0L),
            rangeEndEpochMillis = preferences.getLong("${key}_end", 0L),
            fullSyncAtEpochMillis = preferences.getLong("${key}_full_at", 0L)
        )
    }

    override fun save(
        provider: CalendarProvider,
        accountKey: String,
        calendarKey: String,
        cursor: CalendarIncrementalCursor
    ) {
        val key = key(provider, accountKey, calendarKey)
        preferences.edit()
            .putString("${key}_cursor", cursor.cursor)
            .putLong("${key}_start", cursor.rangeStartEpochMillis)
            .putLong("${key}_end", cursor.rangeEndEpochMillis)
            .putLong("${key}_full_at", cursor.fullSyncAtEpochMillis)
            .apply()
    }

    override fun clear(provider: CalendarProvider, accountKey: String, calendarKey: String) {
        val key = key(provider, accountKey, calendarKey)
        preferences.edit()
            .remove("${key}_cursor")
            .remove("${key}_start")
            .remove("${key}_end")
            .remove("${key}_full_at")
            .apply()
    }

    fun clearProviderAccount(provider: CalendarProvider, accountKey: String) {
        val prefix = "${provider.name}_${accountKey}_"
        preferences.edit().apply {
            preferences.all.keys
                .filter { key -> key.startsWith(prefix) }
                .forEach(::remove)
        }.apply()
    }

    private fun key(
        provider: CalendarProvider,
        accountKey: String,
        calendarKey: String
    ): String {
        return "${provider.name}_${accountKey}_$calendarKey"
    }
}

object NoOpCalendarIncrementalSyncStore : CalendarIncrementalSyncStore {
    override fun get(
        provider: CalendarProvider,
        accountKey: String,
        calendarKey: String
    ): CalendarIncrementalCursor? = null

    override fun save(
        provider: CalendarProvider,
        accountKey: String,
        calendarKey: String,
        cursor: CalendarIncrementalCursor
    ) = Unit

    override fun clear(provider: CalendarProvider, accountKey: String, calendarKey: String) = Unit
}

object CalendarIncrementalSyncPolicy {
    const val MIN_REMAINING_WINDOW_MILLIS = 30L * 24L * 60L * 60L * 1_000L

    fun canReuse(cursor: CalendarIncrementalCursor?, nowEpochMillis: Long): Boolean {
        if (cursor == null || cursor.cursor.isBlank()) return false
        if (cursor.rangeEndEpochMillis - nowEpochMillis <= MIN_REMAINING_WINDOW_MILLIS) {
            return false
        }
        return true
    }
}

object CalendarAccountKey {
    fun fromStableId(stableId: String?): String? {
        val value = stableId?.takeIf { it.isNotBlank() } ?: return null
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
