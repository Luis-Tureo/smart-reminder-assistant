package com.luistureo.voicereminderapp.core.nlp

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ReminderContentCleanerTest {

    @Test
    fun explicitSpokenDayOverridesCurrentDayAndCleansDetail() {
        val result = VoiceReminderParser.parse(
            input = "el dia 15 tengo un examen en la universidad",
            referenceDateTime = LocalDateTime.of(2026, 6, 14, 10, 0)
        )

        assertEquals(LocalDate.of(2026, 6, 15), result.date)
        assertEquals("examen en la universidad", result.reminderText)
    }

    @Test
    fun buildsCleanTitleWithoutDateOrSchedulingWords() {
        val detail = ReminderContentCleaner.cleanDetail(
            "el dia 15 tengo un examen en la universidad"
        )

        assertEquals("examen en la universidad", detail)
        assertEquals("Examen en la universidad", ReminderContentCleaner.buildTitle(detail))
    }

    @Test
    fun removesTimeAndRelativeDateFromReminderText() {
        val detail = ReminderContentCleaner.cleanDetail(
            "recuerdame manana a las 15 comprar remedios"
        )

        assertEquals("comprar remedios", detail)
    }

    @Test
    fun removesTimeFromMedicalAppointmentTitle() {
        val detail = ReminderContentCleaner.cleanDetail(
            "esperando cita medica a las 15:30"
        )

        assertEquals("esperando cita medica", detail)
        assertEquals("Esperando cita medica", ReminderContentCleaner.buildTitle(detail))
    }

    @Test
    fun removesSpokenTimeFromReminderText() {
        val detail = ReminderContentCleaner.cleanDetail(
            "recuerdame control manana a las tres y media de la tarde"
        )

        assertEquals("control", detail)
    }
}
