package com.luistureo.voicereminderapp.core.reminder

import com.luistureo.voicereminderapp.core.utils.DateTimeFormatter
import com.luistureo.voicereminderapp.presentation.calendar.CalendarActionRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReminderTemporalValidationPolicyTest {

    private val now = requireNotNull(
        DateTimeFormatter.parseDateTimeToEpochMillis("10/07/2026", "12:00")
    )

    @Test
    fun pastDateCreationBlocked() {
        val scheduledAt = requireNotNull(
            DateTimeFormatter.parseDateTimeToEpochMillis("09/07/2026", "23:59")
        )

        assertEquals(
            ReminderTemporalValidationPolicy.PAST_SCHEDULE_MESSAGE,
            ReminderTemporalValidationPolicy.validateNewSchedule(scheduledAt, now)
        )
    }

    @Test
    fun todayWithPastTimeBlocked() {
        val scheduledAt = requireNotNull(
            DateTimeFormatter.parseDateTimeToEpochMillis("10/07/2026", "11:59")
        )

        assertEquals(
            ReminderTemporalValidationPolicy.PAST_SCHEDULE_MESSAGE,
            ReminderTemporalValidationPolicy.validateNewSchedule(scheduledAt, now)
        )
    }

    @Test
    fun todayWithFutureTimeAllowed() {
        val scheduledAt = requireNotNull(
            DateTimeFormatter.parseDateTimeToEpochMillis("10/07/2026", "12:01")
        )

        assertNull(ReminderTemporalValidationPolicy.validateNewSchedule(scheduledAt, now))
    }

    @Test
    fun futureDateAllowed() {
        val scheduledAt = requireNotNull(
            DateTimeFormatter.parseDateTimeToEpochMillis("11/07/2026", "08:00")
        )

        assertNull(ReminderTemporalValidationPolicy.validateNewSchedule(scheduledAt, now))
    }

    @Test
    fun calendarPastDayHidesCreateReminderAction() {
        val today = LocalDate.of(2026, 7, 10)

        assertFalse(
            CalendarActionRules.canCreateReminderOnDate(
                selectedDate = LocalDate.of(2026, 7, 9),
                today = today
            )
        )
        assertTrue(
            CalendarActionRules.canCreateReminderOnDate(
                selectedDate = today,
                today = today
            )
        )
        assertTrue(
            CalendarActionRules.canCreateReminderOnDate(
                selectedDate = LocalDate.of(2026, 7, 11),
                today = today
            )
        )
    }
}
