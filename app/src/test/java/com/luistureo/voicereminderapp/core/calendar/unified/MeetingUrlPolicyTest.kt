package com.luistureo.voicereminderapp.core.calendar.unified

import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeetingUrlPolicyTest {

    @Test
    fun supportsGoogleMeetAndTeamsUrlsOnly() {
        assertTrue(MeetingUrlPolicy.isSupportedMeetingUrl("https://meet.google.com/abc-defg-hij"))
        assertTrue(MeetingUrlPolicy.isSupportedMeetingUrl("https://teams.microsoft.com/l/meetup-join/abc"))
        assertTrue(MeetingUrlPolicy.isSupportedMeetingUrl("https://teams.live.com/meet/abc"))
        assertFalse(MeetingUrlPolicy.isSupportedMeetingUrl("https://example.com/meeting"))
        assertFalse(MeetingUrlPolicy.isSupportedMeetingUrl("https://meet.google.com.evil.test/x"))
        assertFalse(MeetingUrlPolicy.isSupportedMeetingUrl("javascript:https://meet.google.com/x"))
    }

    @Test
    fun prefersMeetingLinkFromOriginProviderWhenBothExist() {
        val googleUrl = "https://meet.google.com/abc-defg-hij"
        val microsoftUrl = "https://teams.microsoft.com/l/meetup-join/abc"
        val urls = mapOf(
            CalendarProvider.GOOGLE_CALENDAR to googleUrl,
            CalendarProvider.MICROSOFT_CALENDAR to microsoftUrl
        )

        assertEquals(
            googleUrl,
            MeetingUrlPolicy.selectPreferredUrl(CalendarProvider.GOOGLE_CALENDAR, urls)
        )
        assertEquals(
            microsoftUrl,
            MeetingUrlPolicy.selectPreferredUrl(CalendarProvider.MICROSOFT_CALENDAR, urls)
        )
    }

    @Test
    fun invalidMeetingLinkReturnsNullWithoutCrashing() {
        assertEquals(
            null,
            MeetingUrlPolicy.selectPreferredUrl(
                CalendarProvider.APP,
                mapOf(CalendarProvider.GOOGLE_CALENDAR to "invalid")
            )
        )
    }

    @Test
    fun joinButtonPolicyIsVisibleOnlyForValidMeetingUrl() {
        assertTrue(MeetingUrlPolicy.isSupportedMeetingUrl("https://meet.google.com/abc-defg-hij"))
        assertFalse(MeetingUrlPolicy.isSupportedMeetingUrl(null))
        assertFalse(MeetingUrlPolicy.isSupportedMeetingUrl("https://example.com/no-meeting"))
    }

    @Test
    fun originFallbackBeatsLessReliableOtherProviderUrl() {
        val googleUrl = "https://meet.google.com/abc-defg-hij"
        val teamsUrl = "https://teams.microsoft.com/l/meetup-join/abc"

        assertEquals(
            googleUrl,
            MeetingUrlPolicy.selectPreferredUrl(
                CalendarProvider.GOOGLE_CALENDAR,
                mapOf(CalendarProvider.MICROSOFT_CALENDAR to teamsUrl),
                fallbackUrl = googleUrl
            )
        )
    }
}
