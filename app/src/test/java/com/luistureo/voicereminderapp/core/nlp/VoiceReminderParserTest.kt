package com.luistureo.voicereminderapp.core.nlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class VoiceReminderParserTest {

    private val referenceDateTime = LocalDateTime.of(2026, 6, 10, 10, 0)

    @Test
    fun completeReminderGoesToConfirmationData() {
        val result = VoiceReminderParser.parse(
            input = "recuerdame tomar remedio manana a las 15",
            referenceDateTime = referenceDateTime
        )

        assertEquals("tomar remedio", result.reminderText)
        assertEquals(LocalDate.of(2026, 6, 11), result.date)
        assertEquals(15, result.time?.hour)
        assertEquals(0, result.time?.minute)
        assertFalse(result.time?.isAmbiguous ?: true)
        assertNull(result.invalidTimeMessage)
    }

    @Test
    fun completeTimeExpressionsAreNotAmbiguous() {
        val expectedTimes = mapOf(
            "15:00" to 15,
            "15 horas" to 15,
            "a las 15" to 15,
            "a las 15 pm" to 15,
            "3 pm" to 15,
            "3 de la tarde" to 15,
            "quince horas" to 15
        )

        expectedTimes.forEach { (input, expectedHour) ->
            val result = VoiceReminderParser.parse(input, referenceDateTime)

            assertEquals(input, expectedHour, result.time?.hour)
            assertEquals(input, 0, result.time?.minute)
            assertFalse(input, result.time?.isAmbiguous ?: true)
            assertNull(input, result.invalidTimeMessage)
        }
    }

    @Test
    fun missingDateKeepsOnlyTimeAndDetail() {
        val result = VoiceReminderParser.parse(
            input = "recuerdame tomar remedio a las 15",
            referenceDateTime = referenceDateTime
        )

        assertEquals("tomar remedio", result.reminderText)
        assertNull(result.date)
        assertEquals(15, result.time?.hour)
    }

    @Test
    fun timeOnlyAssistantInputDoesNotInferDate() {
        val result = VoiceReminderParser.parse(
            input = "esperando cita medica a las 15:30",
            referenceDateTime = referenceDateTime
        )

        assertEquals("esperando cita medica", result.reminderText)
        assertNull(result.date)
        assertEquals(15, result.time?.hour)
        assertEquals(30, result.time?.minute)
    }

    @Test
    fun missingTimeKeepsOnlyDateAndDetail() {
        val result = VoiceReminderParser.parse(
            input = "recuerdame tomar remedio manana",
            referenceDateTime = referenceDateTime
        )

        assertEquals("tomar remedio", result.reminderText)
        assertEquals(LocalDate.of(2026, 6, 11), result.date)
        assertNull(result.time)
    }

    @Test
    fun ambiguousTimeAsksForPeriodOnce() {
        val result = VoiceReminderParser.parse(
            input = "recuerdame tomar remedio manana a las 3",
            referenceDateTime = referenceDateTime
        )

        assertEquals(3, result.time?.hour)
        assertEquals(0, result.time?.minute)
        assertTrue(result.time?.isAmbiguous ?: false)
        assertNull(result.invalidTimeMessage)
    }

    @Test
    fun invalidTimesReturnFriendlyClarification() {
        val impossibleTime = VoiceReminderParser.parse("recuerdame llamar a las 28:00", referenceDateTime)
        val invalidMeridiem = VoiceReminderParser.parse("recuerdame llamar a las 13 pm", referenceDateTime)

        assertNull(impossibleTime.time)
        assertNotNull(impossibleTime.invalidTimeMessage)
        assertNull(invalidMeridiem.time)
        assertNotNull(invalidMeridiem.invalidTimeMessage)
    }

    @Test
    fun relativeAndNaturalDatesAreSupported() {
        val inThirtyMinutes = VoiceReminderParser.parse("recuerdame salir en 30 minutos", referenceDateTime)
        val inTwoHours = VoiceReminderParser.parse("recuerdame llamar en 2 horas", referenceDateTime)
        val explicitDate = VoiceReminderParser.parse("recuerdame control el 15 de junio a las 8 am", referenceDateTime)
        val numericDate = VoiceReminderParser.parse("recuerdame control 15/06 a las 20:30", referenceDateTime)
        val halfPast = VoiceReminderParser.parse("recuerdame control manana a las tres y media de la tarde", referenceDateTime)
        val midday = VoiceReminderParser.parse("recuerdame almorzar manana al mediodia", referenceDateTime)
        val midnight = VoiceReminderParser.parse("recuerdame revisar manana a medianoche", referenceDateTime)

        assertEquals(10, inThirtyMinutes.time?.hour)
        assertEquals(30, inThirtyMinutes.time?.minute)
        assertEquals(12, inTwoHours.time?.hour)
        assertEquals(LocalDate.of(2026, 6, 15), explicitDate.date)
        assertEquals(8, explicitDate.time?.hour)
        assertEquals(LocalDate.of(2026, 6, 15), numericDate.date)
        assertEquals(20, numericDate.time?.hour)
        assertEquals(30, numericDate.time?.minute)
        assertEquals(15, halfPast.time?.hour)
        assertEquals(30, halfPast.time?.minute)
        assertEquals(12, midday.time?.hour)
        assertEquals(0, midnight.time?.hour)
    }

    @Test
    fun requestedConversationExamplesAreParsedWithoutWrongDateAssumptions() {
        val tomorrow = VoiceReminderParser.parse("manana a las 15:30", referenceDateTime)
        val monday = VoiceReminderParser.parse("el lunes a las 8", referenceDateTime)
        val timeOnly = VoiceReminderParser.parse("a las 15:30", referenceDateTime)
        val titleOnly = VoiceReminderParser.parse("tengo cita medica", referenceDateTime)
        val dayOnly = VoiceReminderParser.parse("el dia 15 tengo un examen", referenceDateTime)
        val inTwoHours = VoiceReminderParser.parse("en 2 horas", referenceDateTime)

        assertEquals(LocalDate.of(2026, 6, 11), tomorrow.date)
        assertEquals(15, tomorrow.time?.hour)
        assertEquals(30, tomorrow.time?.minute)
        assertEquals(LocalDate.of(2026, 6, 15), monday.date)
        assertEquals(8, monday.time?.hour)
        assertTrue(monday.time?.isAmbiguous ?: false)
        assertNull(timeOnly.date)
        assertEquals(15, timeOnly.time?.hour)
        assertEquals("cita medica", titleOnly.reminderText)
        assertNull(titleOnly.date)
        assertNull(titleOnly.time)
        assertEquals(LocalDate.of(2026, 6, 15), dayOnly.date)
        assertEquals("examen", dayOnly.reminderText)
        assertEquals(LocalDate.of(2026, 6, 10), inTwoHours.date)
        assertEquals(12, inTwoHours.time?.hour)
    }

    @Test
    fun recurrenceFlowIsDetectedForManualConfiguration() {
        val result = VoiceReminderParser.parse(
            input = "recuerdame tomar remedio todos los dias a las 8 am",
            referenceDateTime = referenceDateTime
        )

        assertTrue(result.hasRecurringRequest)
        assertTrue(VoiceReminderLanguageHelper.containsRecurringRequest("todos los dias"))
    }
}
