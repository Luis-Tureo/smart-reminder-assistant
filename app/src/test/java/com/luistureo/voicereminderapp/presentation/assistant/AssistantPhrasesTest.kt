package com.luistureo.voicereminderapp.presentation.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantPhrasesTest {

    @Test
    fun assistantQuestionsUseSpanishQuestionMarksAndAccents() {
        val phrases = listOf(
            AssistantPhrases.START_LISTENING,
            AssistantPhrases.ASK_DATE,
            AssistantPhrases.ASK_TIME,
            AssistantPhrases.ambiguousTimeQuestion(8),
            AssistantPhrases.confirmation("13/06/2026", "09:00", isUrgent = false)
        )

        phrases.forEach { phrase ->
            assertTrue(phrase, phrase.contains("\u00bf"))
            assertTrue(phrase, phrase.contains("?"))
            assertFalse(phrase, phrase.contains("manana"))
        }
        assertTrue(AssistantPhrases.ambiguousTimeQuestion(8).contains("ma\u00f1ana"))
    }

    @Test
    fun saveSuccessMessageIsWarmer() {
        assertEquals("Perfecto, lo dej\u00e9 agendado.", AssistantPhrases.SAVE_SUCCESS)
    }

    @Test
    fun missingDateQuestionMatchesRequiredAssistantPhrase() {
        assertEquals("\u00bfPara qu\u00e9 d\u00eda?", AssistantPhrases.ASK_DATE)
    }
}
