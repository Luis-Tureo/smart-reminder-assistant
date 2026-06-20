package com.luistureo.voicereminderapp.core.calendar.unified

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarIncrementalSyncPolicyTest {

    @Test
    fun existingCursorIsReusedInsteadOfFullSyncOnEveryOpen() {
        val now = 1_800_000_000_000L
        val cursor = CalendarIncrementalCursor(
            cursor = "opaque-cursor",
            rangeStartEpochMillis = now,
            rangeEndEpochMillis = now + 180L * 24L * 60L * 60L * 1_000L,
            fullSyncAtEpochMillis = now - 60_000L
        )

        assertTrue(CalendarIncrementalSyncPolicy.canReuse(cursor, now))
    }

    @Test
    fun missingOrExpiredCursorRequiresControlledFullSync() {
        val now = 1_800_000_000_000L
        val expired = CalendarIncrementalCursor(
            cursor = "opaque-cursor",
            rangeStartEpochMillis = now,
            rangeEndEpochMillis = now,
            fullSyncAtEpochMillis = now - 365L * 24L * 60L * 60L * 1_000L
        )

        assertFalse(CalendarIncrementalSyncPolicy.canReuse(null, now))
        assertFalse(CalendarIncrementalSyncPolicy.canReuse(expired, now))
    }

    @Test
    fun validCursorIsReusedForAutomaticSyncWithoutAnotherFullSync() {
        val now = 1_900_000_000_000L
        val cursor = CalendarIncrementalCursor(
            cursor = "incremental-token",
            rangeStartEpochMillis = now - 10_000L,
            rangeEndEpochMillis = now +
                    CalendarIncrementalSyncPolicy.MIN_REMAINING_WINDOW_MILLIS + 100_000L,
            fullSyncAtEpochMillis = now
        )

        assertTrue(CalendarIncrementalSyncPolicy.canReuse(cursor, now))
    }
}
