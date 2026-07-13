package com.luistureo.voicereminderapp.core.wellness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WellnessAssistantSpeechPolicyTest {
    @Test
    fun catalogContainsOnlyApprovedFixedNeutralPhrases() {
        val expectedCatalog = mapOf(
            WellnessAssistantPhrase.NUTRITION_REVIEW_BREAKFAST to
                "Es hora de revisar tu desayuno.",
            WellnessAssistantPhrase.NUTRITION_LUNCH_PLANNED to
                "Tu almuerzo está planificado para hoy.",
            WellnessAssistantPhrase.NUTRITION_DAY_PLANNING_COMPLETED to
                "Has completado tu planificación del día.",
            WellnessAssistantPhrase.NUTRITION_ADJUST_PLAN to
                "Puedes ajustar el plan para que sea más fácil de seguir.",
            WellnessAssistantPhrase.RECOVERY_REMEMBER_REASON to
                "Recuerda por qué comenzaste.",
            WellnessAssistantPhrase.RECOVERY_POSITIVE_DECISIONS to
                "Cada decisión positiva cuenta.",
            WellnessAssistantPhrase.RECOVERY_REVIEW_SUPPORT_TOOLS to
                "Puedes revisar tus herramientas de apoyo.",
            WellnessAssistantPhrase.RECOVERY_DIFFICULT_MOMENT to
                "Un momento difícil no elimina tu progreso."
        )

        assertEquals(expectedCatalog.keys, WellnessAssistantPhrase.entries.toSet())
        expectedCatalog.forEach { (phrase, expectedText) ->
            assertEquals(expectedText, WellnessAssistantSpeechPolicy.textFor(phrase))
            assertTrue(WellnessAssistantSpeechPolicy.isAllowedCatalogText(expectedText))
        }
    }

    @Test
    fun rejectsEveryTextOutsideTheExactCatalog() {
        val rejectedTexts = listOf(
            "",
            "Es hora de revisar tu desayuno",
            " Es hora de revisar tu desayuno. ",
            "Tu registro privado indica una dificultad.",
            "Contacta a Ana al 123456789.",
            "Cada decisión positiva cuenta. Nota personal"
        )

        rejectedTexts.forEach { text ->
            assertFalse(WellnessAssistantSpeechPolicy.isAllowedCatalogText(text))
        }
    }
}
