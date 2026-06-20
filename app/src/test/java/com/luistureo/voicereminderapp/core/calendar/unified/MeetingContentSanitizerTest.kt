package com.luistureo.voicereminderapp.core.calendar.unified

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeetingContentSanitizerTest {
    @Test
    fun removesHtmlStylesHiddenContentAndInvitationBoilerplate() {
        val html = """
            <html><head><style>.x { color:red }</style></head><body>
            <div>Revisión del lanzamiento</div><span style="display:none">decoración oculta</span>
            <div>Microsoft Teams Meeting</div><a href="https://teams.microsoft.com/l/meetup-join/private">Join</a>
            </body></html>
        """.trimIndent()

        val clean = MeetingContentSanitizer.cleanDescription(html)

        assertEquals("Revisión del lanzamiento", clean)
        assertFalse(clean.contains("<html>"))
        assertFalse(clean.contains("color:red"))
        assertFalse(clean.contains("teams.microsoft.com"))
    }

    @Test
    fun extractsTeamsUrlFromHtmlAndRejectsUnsafeSchemes() {
        assertEquals(
            "https://teams.microsoft.com/l/meetup-join/abc",
            MeetingContentSanitizer.extractSupportedMeetingUrl(
                "<a href='https://teams.microsoft.com/l/meetup-join/abc'>Join</a>"
            )
        )
        assertNull(MeetingContentSanitizer.extractSupportedMeetingUrl("<a href='javascript:alert(1)'>x</a>"))
        assertNull(MeetingContentSanitizer.extractSupportedMeetingUrl("file:///private"))
    }

    @Test
    fun normalReminderTextIsPreserved() {
        assertEquals(
            "Comprar leche y pan",
            MeetingContentSanitizer.cleanDescription("Comprar leche y pan")
        )
        assertTrue(MeetingContentSanitizer.cleanDescription("a".repeat(600)).endsWith("…"))
    }
}
