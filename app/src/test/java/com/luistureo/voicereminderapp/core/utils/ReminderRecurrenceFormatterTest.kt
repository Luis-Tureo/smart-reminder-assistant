package com.luistureo.voicereminderapp.core.utils

import com.luistureo.voicereminderapp.domain.model.ReminderRecurrence
import com.luistureo.voicereminderapp.domain.model.ReminderRecurrenceUnit
import com.luistureo.voicereminderapp.domain.model.ReminderWeekday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderRecurrenceFormatterTest {

    @Test
    fun nullRecurrenceHasNoLabel() {
        assertNull(ReminderRecurrenceFormatter.format(null))
    }

    @Test
    fun formatsIntervalLabelsWithReadableSpanish() {
        assertEquals(
            "Cada 2 días",
            ReminderRecurrenceFormatter.format(
                ReminderRecurrence(ReminderRecurrenceUnit.DAY, interval = 2)
            )
        )
        assertEquals(
            "Cada 2 años",
            ReminderRecurrenceFormatter.format(
                ReminderRecurrence(ReminderRecurrenceUnit.YEAR, interval = 2)
            )
        )
    }

    @Test
    fun formatsSpecificWeekdaysInCalendarOrder() {
        assertEquals(
            "Lun • Mie",
            ReminderRecurrenceFormatter.format(
                ReminderRecurrence(
                    unit = ReminderRecurrenceUnit.WEEK,
                    weekdays = setOf(ReminderWeekday.WEDNESDAY, ReminderWeekday.MONDAY)
                )
            )
        )
    }
}
