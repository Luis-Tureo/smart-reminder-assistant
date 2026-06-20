package com.luistureo.voicereminderapp.core.calendar.unified

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarAutoSyncPolicyTest {
    private val now = 2_000_000_000_000L

    @Test
    fun automaticSyncSkipsPausedProviderBeforeAnyApiCall() {
        assertEquals(
            CalendarAutoSyncDecision.SKIP_PAUSED,
            CalendarAutoSyncPolicy.decision(true, false, CalendarAutoSyncProviderState(), now)
        )
    }

    @Test
    fun automaticSyncSkipsDisconnectedProvider() {
        assertEquals(
            CalendarAutoSyncDecision.SKIP_DISCONNECTED,
            CalendarAutoSyncPolicy.decision(false, true, CalendarAutoSyncProviderState(), now)
        )
    }

    @Test
    fun activeProviderRunsOutsideCooldown() {
        assertEquals(
            CalendarAutoSyncDecision.RUN,
            CalendarAutoSyncPolicy.decision(true, true, CalendarAutoSyncProviderState(), now)
        )
    }

    @Test
    fun automaticSyncSkipsPersistedCooldown() {
        assertEquals(
            CalendarAutoSyncDecision.SKIP_COOLDOWN,
            CalendarAutoSyncPolicy.decision(
                true,
                true,
                CalendarAutoSyncProviderState(blockedUntilEpochMillis = now + 60_000L),
                now
            )
        )
    }

    @Test
    fun failureBackoffGrowsAndRetryAfterTakesPrecedence() {
        val first = CalendarAutoSyncPolicy.failureBackoffMillis(1)
        val third = CalendarAutoSyncPolicy.failureBackoffMillis(3)
        val retryAfter = CalendarAutoSyncPolicy.failureBackoffMillis(1, 125_000L)

        assertTrue(third > first)
        assertEquals(125_000L, retryAfter)
    }
}
