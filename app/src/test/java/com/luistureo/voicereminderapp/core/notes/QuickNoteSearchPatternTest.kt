package com.luistureo.voicereminderapp.core.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class QuickNoteSearchPatternTest {
    @Test
    fun wrapsTrimmedTextForContainsSearch() {
        assertEquals("%idea local%", QuickNoteSearchPattern.contains("  idea local  "))
    }

    @Test
    fun escapesLikeWildcardsAndEscapeCharacterAsLiterals() {
        assertEquals("%50\\%\\_\\\\%", QuickNoteSearchPattern.contains("  50%_\\  "))
    }

    @Test
    fun emptyQueryMatchesEveryTextValue() {
        assertEquals("%%", QuickNoteSearchPattern.contains(" \t "))
    }
}
