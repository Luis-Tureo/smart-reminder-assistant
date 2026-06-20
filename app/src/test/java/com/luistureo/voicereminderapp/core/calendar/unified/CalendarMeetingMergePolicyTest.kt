package com.luistureo.voicereminderapp.core.calendar.unified

import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import com.luistureo.voicereminderapp.domain.model.Reminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarMeetingMergePolicyTest {
    @Test
    fun microsoftOriginCopyImportedFromGoogleMatchesOriginalCard() {
        val original = meeting(
            id = 7,
            origin = CalendarProvider.MICROSOFT_CALENDAR,
            ids = mapOf(CalendarProvider.MICROSOFT_CALENDAR to "m-1")
        )

        val match = CalendarMeetingMergePolicy.findExisting(
            listOf(original),
            CalendarProvider.GOOGLE_CALENDAR,
            "g-1",
            original.title,
            original.scheduledAtEpochMillis,
            isManagedCopy = true,
            originProviderHint = CalendarProvider.MICROSOFT_CALENDAR,
            localIdHint = 7
        )

        assertEquals(original, match)
    }

    @Test
    fun googleOriginCopyImportedFromMicrosoftMatchesOriginalCard() {
        val original = meeting(
            id = 9,
            origin = CalendarProvider.GOOGLE_CALENDAR,
            ids = mapOf(CalendarProvider.GOOGLE_CALENDAR to "g-1")
        )
        val match = CalendarMeetingMergePolicy.findExisting(
            listOf(original),
            CalendarProvider.MICROSOFT_CALENDAR,
            "m-1",
            original.title,
            original.scheduledAtEpochMillis,
            isManagedCopy = true,
            originProviderHint = CalendarProvider.GOOGLE_CALENDAR,
            localIdHint = 9
        )
        assertEquals(original, match)
    }

    @Test
    fun ordinaryCoincidentEventsAreNeverMerged() {
        val original = meeting(1, CalendarProvider.GOOGLE_CALENDAR, emptyMap())
        assertNull(
            CalendarMeetingMergePolicy.findExisting(
                listOf(original),
                CalendarProvider.MICROSOFT_CALENDAR,
                "m-1",
                original.title,
                original.scheduledAtEpochMillis,
                isManagedCopy = false
            )
        )
    }

    @Test
    fun nullIncomingLinkCannotReplaceValidOriginLink() {
        val url = "https://meet.google.com/abc-defg-hij"
        assertEquals(
            url,
            MeetingUrlPolicy.selectPreferredUrl(
                CalendarProvider.GOOGLE_CALENDAR,
                mapOf(CalendarProvider.GOOGLE_CALENDAR to url),
                fallbackUrl = null
            )
        )
    }

    private fun meeting(
        id: Int,
        origin: CalendarProvider,
        ids: Map<CalendarProvider, String>
    ) = Reminder(
        id = id,
        title = "Reunión",
        detail = "Agenda",
        scheduledAtEpochMillis = 1_900_000_000_000L,
        originProvider = origin,
        externalIdsByProvider = ids
    )
}
