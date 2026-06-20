package com.luistureo.voicereminderapp.core.calendar.unified

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class CalendarConflictPolicyTest {

    @Test
    fun exactSameStartTimeNeedsInlineWarning() {
        val startAt = epochMillis(2026, 6, 15, 9, 0)

        assertTrue(
            CalendarConflictPolicy.areConflicting(
                candidate("google", startAt),
                candidate("microsoft", startAt)
            )
        )
    }

    @Test
    fun startTimesUnderFiveMinutesApartNeedInlineWarning() {
        assertTrue(
            CalendarConflictPolicy.areConflicting(
                candidate("google", epochMillis(2026, 6, 15, 9, 0)),
                candidate("microsoft", epochMillis(2026, 6, 15, 9, 4))
            )
        )
    }

    @Test
    fun startTimesExactlyFiveMinutesApartNeedNoWarning() {
        assertFalse(
            CalendarConflictPolicy.areConflicting(
                candidate("google", epochMillis(2026, 6, 15, 9, 0)),
                candidate("microsoft", epochMillis(2026, 6, 15, 9, 5))
            )
        )
    }

    @Test
    fun matchingTimesOnDifferentDatesNeedNoWarning() {
        assertFalse(
            CalendarConflictPolicy.areConflicting(
                candidate("google", epochMillis(2026, 6, 15, 9, 0)),
                candidate("microsoft", epochMillis(2026, 6, 16, 9, 0))
            )
        )
    }

    @Test
    fun allDayEventsNeedNoWarning() {
        val startAt = epochMillis(2026, 6, 15, 9, 0)

        assertFalse(
            CalendarConflictPolicy.areConflicting(
                candidate("google", startAt, isAllDay = true),
                candidate("microsoft", startAt)
            )
        )
    }

    @Test
    fun threeOrMoreNearbyItemsRemainOneInformationalGroup() {
        val conflicts = CalendarConflictPolicy.findConflicts(
            listOf(
                candidate("app", epochMillis(2026, 6, 15, 9, 0)),
                candidate("google", epochMillis(2026, 6, 15, 9, 2)),
                candidate("microsoft", epochMillis(2026, 6, 15, 9, 4))
            )
        )

        assertEquals(1, conflicts.size)
        assertEquals(3, conflicts.first().candidates.size)
    }

    @Test
    fun emptyInputDoesNotCrashAndProducesNoWarning() {
        assertTrue(CalendarConflictPolicy.findConflicts(emptyList()).isEmpty())
    }

    @Test
    fun recurringOccurrencesHaveDifferentConflictIdentifiers() {
        val firstId = CalendarConflictPolicy.occurrenceCandidateId(7, 1_800_000_000_000L)
        val secondId = CalendarConflictPolicy.occurrenceCandidateId(7, 1_800_086_400_000L)

        assertFalse(firstId == secondId)
    }

    private fun candidate(
        id: String,
        startAt: Long,
        isAllDay: Boolean = false
    ): CalendarConflictPolicy.Candidate {
        return CalendarConflictPolicy.Candidate(
            id = id,
            startAtEpochMillis = startAt,
            isAllDay = isAllDay
        )
    }

    private fun epochMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): Long {
        return LocalDateTime.of(year, month, day, hour, minute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
}
