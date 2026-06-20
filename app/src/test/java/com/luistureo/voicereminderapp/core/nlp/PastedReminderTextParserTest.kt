package com.luistureo.voicereminderapp.core.nlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class PastedReminderTextParserTest {
    private val reference = LocalDateTime.of(2026, 6, 18, 10, 0)

    @Test
    fun parsesChileanSpanishDateAndTimeExamples() {
        val cases = listOf(
            Triple("reunión el lunes 22 de junio a las 11", LocalDate.of(2026, 6, 22), 11 to 0),
            Triple("control médico mañana a las 15:30", LocalDate.of(2026, 6, 19), 15 to 30),
            Triple("pagar cuenta el 25/06/2026 a las 9:00", LocalDate.of(2026, 6, 25), 9 to 0),
            Triple("dentista hoy 18:00", LocalDate.of(2026, 6, 18), 18 to 0)
        )

        cases.forEach { (text, expectedDate, expectedTime) ->
            val result = PastedReminderTextParser.parse(text, referenceDateTime = reference)

            assertEquals(text, expectedDate, result.date)
            assertEquals(text, expectedTime.first, result.time?.hour)
            assertEquals(text, expectedTime.second, result.time?.minute)
            assertEquals(text, PastedReminderDateOrigin.TEXT, result.dateOrigin)
            assertTrue(text, result.canConfirm)
        }
    }

    @Test
    fun missingDateAndTimeRemainRequired() {
        val result = PastedReminderTextParser.parse(
            "comprar remedios",
            referenceDateTime = reference
        )

        assertNull(result.date)
        assertNull(result.time)
        assertTrue(result.requiresDate)
        assertTrue(result.requiresTime)
        assertFalse(result.canConfirm)
    }

    @Test
    fun selectedCalendarDayIsDefaultOnlyWhenTextHasNoDate() {
        val selectedDay = LocalDate.of(2026, 7, 4)
        val result = PastedReminderTextParser.parse(
            "comprar remedios a las 12",
            selectedCalendarDay = selectedDay,
            referenceDateTime = reference
        )

        assertEquals(selectedDay, result.date)
        assertEquals(PastedReminderDateOrigin.SELECTED_CALENDAR_DAY, result.dateOrigin)
        assertEquals(12, result.time?.hour)
    }

    @Test
    fun dateFromTextOverridesSelectedCalendarDay() {
        val result = PastedReminderTextParser.parse(
            "control médico mañana a las 15:30",
            selectedCalendarDay = LocalDate.of(2026, 7, 4),
            referenceDateTime = reference
        )

        assertEquals(LocalDate.of(2026, 6, 19), result.date)
        assertEquals(PastedReminderDateOrigin.TEXT, result.dateOrigin)
    }

    @Test
    fun ambiguousTimeIsNotSilentlyAccepted() {
        val result = PastedReminderTextParser.parse(
            "dentista mañana 8",
            referenceDateTime = reference
        )

        assertNull(result.time)
        assertTrue(result.requiresTime)
        assertFalse(result.canConfirm)
    }
}
