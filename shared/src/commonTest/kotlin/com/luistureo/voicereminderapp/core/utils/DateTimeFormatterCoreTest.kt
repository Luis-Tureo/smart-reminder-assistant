package com.luistureo.voicereminderapp.core.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DateTimeFormatterCoreTest {

    @Test
    fun parseDateParts_returnsExpectedParts_forValidStoredDate() {
        val parsedDate = DateTimeFormatterCore.parseDateParts("05/03/2026")

        assertEquals(
            DateInputParts(
                day = 5,
                month = 3,
                year = 2026
            ),
            parsedDate
        )
    }

    @Test
    fun parseDateParts_acceptsLeapDay_whenDateIsValid() {
        val parsedDate = DateTimeFormatterCore.parseDateParts("29/02/2024")

        assertEquals(
            DateInputParts(
                day = 29,
                month = 2,
                year = 2024
            ),
            parsedDate
        )
    }

    @Test
    fun parseDateParts_returnsNull_forMalformedOrInvalidDates() {
        val invalidDates = listOf(
            "5/03/2026",
            "05/3/2026",
            "05/03/26",
            "31/04/2026",
            "29/02/2023",
            "00/12/2026",
            "12/00/2026",
            "32/01/2026",
            "15-03-2026"
        )

        invalidDates.forEach { value ->
            assertNull(DateTimeFormatterCore.parseDateParts(value), value)
        }
    }

    @Test
    fun parseTimeParts_returnsExpectedParts_forValidStoredTime() {
        val parsedTime = DateTimeFormatterCore.parseTimeParts("09:07")

        assertEquals(
            TimeInputParts(
                hour = 9,
                minute = 7
            ),
            parsedTime
        )
    }

    @Test
    fun parseTimeParts_acceptsDayBoundaries() {
        assertEquals(
            TimeInputParts(hour = 0, minute = 0),
            DateTimeFormatterCore.parseTimeParts("00:00")
        )
        assertEquals(
            TimeInputParts(hour = 23, minute = 59),
            DateTimeFormatterCore.parseTimeParts("23:59")
        )
    }

    @Test
    fun parseTimeParts_returnsNull_forMalformedOrInvalidTimes() {
        val invalidTimes = listOf(
            "9:07",
            "09:7",
            "24:00",
            "23:60",
            "12-30"
        )

        invalidTimes.forEach { value ->
            assertNull(DateTimeFormatterCore.parseTimeParts(value), value)
        }
    }

    @Test
    fun parseDateTimeToEpochMillis_roundTripsCurrentAndroidFlowInput() {
        val epochMillis = DateTimeFormatterCore.parseDateTimeToEpochMillis(
            date = "05/03/2026",
            time = "09:07"
        )

        assertNotNull(epochMillis)
        assertEquals("05/03/2026", DateTimeFormatterCore.formatDateFromEpoch(epochMillis))
        assertEquals("09:07", DateTimeFormatterCore.formatTimeFromEpoch(epochMillis))
    }

    @Test
    fun parseDateTimeToEpochMillis_matchesBuildTriggerTimeMillis_forValidInput() {
        val parsedEpochMillis = DateTimeFormatterCore.parseDateTimeToEpochMillis(
            date = "29/02/2024",
            time = "23:59"
        )

        assertEquals(
            DateTimeFormatterCore.buildTriggerTimeMillis(
                year = 2024,
                month = 2,
                day = 29,
                hour = 23,
                minute = 59
            ),
            parsedEpochMillis
        )
    }

    @Test
    fun parseDateTimeToEpochMillis_returnsNull_whenDateOrTimeIsInvalid() {
        assertNull(
            DateTimeFormatterCore.parseDateTimeToEpochMillis(
                date = "31/04/2026",
                time = "09:07"
            )
        )
        assertNull(
            DateTimeFormatterCore.parseDateTimeToEpochMillis(
                date = "05/03/2026",
                time = "24:00"
            )
        )
    }
}
