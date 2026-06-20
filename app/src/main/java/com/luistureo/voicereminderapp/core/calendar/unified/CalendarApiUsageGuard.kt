package com.luistureo.voicereminderapp.core.calendar.unified

import android.content.Context
import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlin.random.Random

class CalendarApiUsageGuard private constructor(
    context: Context
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "calendar_api_usage",
        Context.MODE_PRIVATE
    )
    private val sessionCounts = ConcurrentHashMap<CalendarProvider, Int>()
    private val manualSyncAt = ConcurrentHashMap<CalendarProvider, Long>()
    private val automaticSyncAt = ConcurrentHashMap<CalendarProvider, Long>()

    fun tryAcquireRequest(provider: CalendarProvider, requestType: String): Boolean {
        val sessionCount = sessionCounts[provider] ?: 0
        val dayKey = dayCounterKey(provider, LocalDate.now().toString())
        val dayCount = preferences.getInt(dayKey, 0)
        if (!CalendarRequestCounterPolicy.canRequest(sessionCount, dayCount)) {
            CalendarSyncLogger.safeLimitReached(
                provider = provider,
                requestType = requestType,
                sessionCount = sessionCount,
                dayCount = dayCount
            )
            return false
        }

        sessionCounts[provider] = sessionCount + 1
        preferences.edit().putInt(dayKey, dayCount + 1).apply()
        CalendarSyncLogger.requestCount(
            provider = provider,
            requestType = requestType,
            sessionCount = sessionCount + 1,
            dayCount = dayCount + 1
        )
        return true
    }

    fun tryStartManualSync(
        provider: CalendarProvider,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val previousSyncAt = manualSyncAt[provider] ?: 0L
        val remainingMillis = CalendarSyncCooldownPolicy.remainingMillis(
            previousSyncAt,
            nowEpochMillis,
            MANUAL_SYNC_COOLDOWN_MILLIS
        )
        if (remainingMillis > 0L) {
            CalendarSyncLogger.cooldown(provider, "manual_sync", remainingMillis)
            return false
        }
        manualSyncAt[provider] = nowEpochMillis
        return true
    }

    fun tryStartAutomaticSync(
        provider: CalendarProvider,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val previousSyncAt = automaticSyncAt[provider] ?: 0L
        val remainingMillis = AUTOMATIC_SYNC_COOLDOWN_MILLIS - (nowEpochMillis - previousSyncAt)
        if (remainingMillis > 0L) {
            CalendarSyncLogger.cooldown(provider, "automatic_sync", remainingMillis)
            return false
        }
        automaticSyncAt[provider] = nowEpochMillis
        return true
    }

    companion object {
        const val MAX_REQUESTS_PER_SESSION = 80
        const val MAX_REQUESTS_PER_DAY = 500
        const val MANUAL_SYNC_COOLDOWN_MILLIS = 30_000L
        const val AUTOMATIC_SYNC_COOLDOWN_MILLIS = 10 * 60_000L

        @Volatile
        private var instance: CalendarApiUsageGuard? = null

        fun get(context: Context): CalendarApiUsageGuard {
            return instance ?: synchronized(this) {
                instance ?: CalendarApiUsageGuard(context).also { instance = it }
            }
        }

        private fun dayCounterKey(provider: CalendarProvider, day: String): String {
            return "${provider.name}_$day"
        }
    }
}

object CalendarRequestCounterPolicy {
    fun canRequest(sessionCount: Int, dayCount: Int): Boolean {
        return sessionCount < CalendarApiUsageGuard.MAX_REQUESTS_PER_SESSION &&
                dayCount < CalendarApiUsageGuard.MAX_REQUESTS_PER_DAY
    }
}

object CalendarSyncCooldownPolicy {
    fun remainingMillis(
        previousSyncAtEpochMillis: Long,
        nowEpochMillis: Long,
        cooldownMillis: Long
    ): Long {
        return (cooldownMillis - (nowEpochMillis - previousSyncAtEpochMillis))
            .coerceAtLeast(0L)
    }
}

object CalendarBackoffPolicy {
    const val MAX_ATTEMPTS = 3
    const val MAX_INLINE_RETRY_AFTER_SECONDS = 60L
    private const val BASE_DELAY_MILLIS = 1_000L
    private const val MAX_DELAY_MILLIS = 60_000L

    fun shouldRetry(statusCode: Int): Boolean {
        return statusCode == 429 || statusCode == 403 || statusCode in 500..599
    }

    fun delayMillis(
        attempt: Int,
        retryAfterSeconds: Long? = null,
        jitterMillis: Long = Random.nextLong(0L, 501L)
    ): Long {
        val retryAfterMillis = retryAfterSeconds?.coerceAtLeast(0L)?.times(1_000L)
        if (retryAfterMillis != null) return retryAfterMillis
        val exponential = BASE_DELAY_MILLIS * (1L shl attempt.coerceIn(0, 6))
        return min(exponential + jitterMillis.coerceAtLeast(0L), MAX_DELAY_MILLIS)
    }

    fun parseRetryAfterSeconds(value: String?, nowEpochMillis: Long): Long? {
        value ?: return null
        value.toLongOrNull()?.let { return it.coerceAtLeast(0L) }
        return runCatching {
            val retryAt = java.time.ZonedDateTime.parse(
                value,
                java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
            ).toInstant().toEpochMilli()
            ((retryAt - nowEpochMillis).coerceAtLeast(0L) + 999L) / 1_000L
        }.getOrNull()
    }
}

class CalendarQuotaLimitException(provider: CalendarProvider) :
    IllegalStateException("Limite local temporal alcanzado para ${provider.displayName}.")
