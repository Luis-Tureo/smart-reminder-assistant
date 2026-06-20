package com.luistureo.voicereminderapp.core.calendar.unified

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarApiUsagePolicyTest {

    @Test
    fun manualSyncCooldownBlocksRepeatedTap() {
        val remaining = CalendarSyncCooldownPolicy.remainingMillis(
            previousSyncAtEpochMillis = 10_000L,
            nowEpochMillis = 15_000L,
            cooldownMillis = 30_000L
        )

        assertEquals(25_000L, remaining)
    }

    @Test
    fun manualSyncCooldownAllowsTapAfterWindow() {
        assertEquals(
            0L,
            CalendarSyncCooldownPolicy.remainingMillis(10_000L, 40_000L, 30_000L)
        )
    }

    @Test
    fun requestCounterStopsAtSafeSessionAndDailyLimits() {
        assertTrue(CalendarRequestCounterPolicy.canRequest(0, 0))
        assertFalse(
            CalendarRequestCounterPolicy.canRequest(
                CalendarApiUsageGuard.MAX_REQUESTS_PER_SESSION,
                0
            )
        )
        assertFalse(
            CalendarRequestCounterPolicy.canRequest(
                0,
                CalendarApiUsageGuard.MAX_REQUESTS_PER_DAY
            )
        )
    }

    @Test
    fun rateLimitAndServerErrorsUseBoundedBackoff() {
        assertTrue(CalendarBackoffPolicy.shouldRetry(429))
        assertTrue(CalendarBackoffPolicy.shouldRetry(403))
        assertTrue(CalendarBackoffPolicy.shouldRetry(503))
        assertFalse(CalendarBackoffPolicy.shouldRetry(400))
        assertEquals(
            2_000L,
            CalendarBackoffPolicy.delayMillis(
                attempt = 0,
                retryAfterSeconds = 2L,
                jitterMillis = 500L
            )
        )
    }

    @Test
    fun malformedRetryAfterDoesNotCrash() {
        assertEquals(
            null,
            CalendarBackoffPolicy.parseRetryAfterSeconds("invalid", 0L)
        )
    }

    @Test
    fun longRetryAfterIsNotShortenedByBackoffPolicy() {
        assertEquals(
            120_000L,
            CalendarBackoffPolicy.delayMillis(
                attempt = 0,
                retryAfterSeconds = 120L,
                jitterMillis = 0L
            )
        )
    }
}
