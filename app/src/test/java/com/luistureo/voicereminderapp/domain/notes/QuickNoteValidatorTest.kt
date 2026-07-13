package com.luistureo.voicereminderapp.domain.notes

import com.luistureo.voicereminderapp.domain.notes.model.QuickNoteDraft
import com.luistureo.voicereminderapp.domain.notes.validation.QuickNoteValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickNoteValidatorTest {
    @Test
    fun acceptsTitleOnlyAndNormalizesEmptyContent() {
        val normalized = QuickNoteValidator.normalizeOrNull(
            QuickNoteDraft(title = "  Comprar leche  ", content = " \n\t ")
        )

        assertEquals("Comprar leche", normalized?.title)
        assertEquals("", normalized?.content)
        assertTrue(QuickNoteValidator.isMeaningful(requireNotNull(normalized)))
    }

    @Test
    fun acceptsContentOnlyAndNormalizesBlankTitleToNull() {
        val normalized = QuickNoteValidator.normalizeOrNull(
            QuickNoteDraft(title = " \t ", content = "  Idea para mañana  ")
        )

        assertNull(normalized?.title)
        assertEquals("Idea para mañana", normalized?.content)
    }

    @Test
    fun rejectsDraftWhenTitleAndContentAreBlank() {
        val draft = QuickNoteDraft(title = " \n ", content = "\t\r\n ")

        assertNull(QuickNoteValidator.normalizeOrNull(draft))
        assertFalse(QuickNoteValidator.isMeaningful(draft))
    }

    @Test
    fun trimsOnlyOuterWhitespaceAndPreservesInternalLineBreaks() {
        val normalized = QuickNoteValidator.normalizeOrNull(
            QuickNoteDraft(
                title = "  Lista semanal  ",
                content = "\n  Primera línea  \n\n  Segunda línea  \n"
            )
        )

        assertEquals("Lista semanal", normalized?.title)
        assertEquals("Primera línea  \n\n  Segunda línea", normalized?.content)
    }
}
