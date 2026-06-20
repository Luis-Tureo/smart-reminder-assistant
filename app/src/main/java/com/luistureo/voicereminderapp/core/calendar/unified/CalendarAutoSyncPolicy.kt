package com.luistureo.voicereminderapp.core.calendar.unified

import android.content.Context
import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import kotlin.math.min

enum class CalendarAutoSyncDecision {
    RUN,
    SKIP_DISCONNECTED,
    SKIP_PAUSED,
    SKIP_COOLDOWN
}

data class CalendarAutoSyncProviderState(
    val lastAttemptAtEpochMillis: Long = 0L,
    val lastSuccessAtEpochMillis: Long = 0L,
    val blockedUntilEpochMillis: Long = 0L,
    val consecutiveFailureCount: Int = 0,
    val lastErrorCode: String? = null
)

object CalendarAutoSyncPolicy {
    const val PERIODIC_INTERVAL_MINUTES = 15L
    const val SUCCESS_COOLDOWN_MILLIS = 10L * 60L * 1_000L
    private const val BASE_FAILURE_BACKOFF_MILLIS = 15L * 60L * 1_000L
    private const val MAX_FAILURE_BACKOFF_MILLIS = 6L * 60L * 60L * 1_000L

    fun decision(
        hasSession: Boolean,
        syncEnabled: Boolean,
        state: CalendarAutoSyncProviderState,
        nowEpochMillis: Long
    ): CalendarAutoSyncDecision {
        if (hasSession && !syncEnabled) return CalendarAutoSyncDecision.SKIP_PAUSED
        if (!hasSession) return CalendarAutoSyncDecision.SKIP_DISCONNECTED
        if (state.blockedUntilEpochMillis > nowEpochMillis) {
            return CalendarAutoSyncDecision.SKIP_COOLDOWN
        }
        if (nowEpochMillis - state.lastAttemptAtEpochMillis < SUCCESS_COOLDOWN_MILLIS) {
            return CalendarAutoSyncDecision.SKIP_COOLDOWN
        }
        return CalendarAutoSyncDecision.RUN
    }

    fun failureBackoffMillis(
        consecutiveFailureCount: Int,
        retryAfterMillis: Long? = null
    ): Long {
        retryAfterMillis?.takeIf { it > 0L }?.let {
            return it.coerceAtMost(24L * 60L * 60L * 1_000L)
        }
        val exponent = (consecutiveFailureCount - 1).coerceIn(0, 5)
        return min(
            BASE_FAILURE_BACKOFF_MILLIS * (1L shl exponent),
            MAX_FAILURE_BACKOFF_MILLIS
        )
    }
}

class CalendarAutoSyncStateStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "calendar_auto_sync_state",
        Context.MODE_PRIVATE
    )

    fun get(provider: CalendarProvider): CalendarAutoSyncProviderState {
        val prefix = provider.name
        return CalendarAutoSyncProviderState(
            lastAttemptAtEpochMillis = preferences.getLong("${prefix}_attempt", 0L),
            lastSuccessAtEpochMillis = preferences.getLong("${prefix}_success", 0L),
            blockedUntilEpochMillis = preferences.getLong("${prefix}_blocked_until", 0L),
            consecutiveFailureCount = preferences.getInt("${prefix}_failures", 0),
            lastErrorCode = preferences.getString("${prefix}_error", null)
        )
    }

    fun recordAttempt(provider: CalendarProvider, nowEpochMillis: Long) {
        preferences.edit().putLong("${provider.name}_attempt", nowEpochMillis).apply()
    }

    fun recordSuccess(provider: CalendarProvider, nowEpochMillis: Long) {
        val prefix = provider.name
        preferences.edit()
            .putLong("${prefix}_success", nowEpochMillis)
            .putLong("${prefix}_blocked_until", 0L)
            .putInt("${prefix}_failures", 0)
            .remove("${prefix}_error")
            .apply()
    }

    fun recordFailure(
        provider: CalendarProvider,
        errorCode: String,
        nowEpochMillis: Long,
        retryAfterMillis: Long? = null
    ): Long {
        val prefix = provider.name
        val failures = (preferences.getInt("${prefix}_failures", 0) + 1).coerceAtMost(6)
        val blockedUntil = nowEpochMillis + CalendarAutoSyncPolicy.failureBackoffMillis(
            failures,
            retryAfterMillis
        )
        preferences.edit()
            .putInt("${prefix}_failures", failures)
            .putLong("${prefix}_blocked_until", blockedUntil)
            .putString("${prefix}_error", errorCode)
            .apply()
        return blockedUntil
    }

    fun clear(provider: CalendarProvider) {
        val prefix = provider.name
        preferences.edit().apply {
            preferences.all.keys
                .filter { it.startsWith("${prefix}_") }
                .forEach(::remove)
        }.apply()
    }
}
