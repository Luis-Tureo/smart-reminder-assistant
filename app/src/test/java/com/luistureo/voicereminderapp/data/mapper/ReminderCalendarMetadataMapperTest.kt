package com.luistureo.voicereminderapp.data.mapper

import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import com.luistureo.voicereminderapp.domain.model.Reminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderCalendarMetadataMapperTest {

    @Test
    fun roomRoundTripKeepsMeetingLinksAndSuspendedOccurrence() {
        val occurrenceAt = 1_900_000_000_000L
        val reminder = Reminder(
            title = "Reunion",
            detail = "Revision",
            scheduledAtEpochMillis = occurrenceAt,
            microsoftCalendarEventId = "microsoft-event",
            meetingUrl = "https://meet.google.com/abc-defg-hij",
            meetingProvider = CalendarProvider.GOOGLE_CALENDAR,
            isOnlineMeeting = true,
            meetingUrlsByProvider = mapOf(
                CalendarProvider.GOOGLE_CALENDAR to "https://meet.google.com/abc-defg-hij",
                CalendarProvider.MICROSOFT_CALENDAR to
                        "https://teams.microsoft.com/l/meetup-join/abc"
            ),
            syncedFingerprintsByProvider = mapOf(
                CalendarProvider.GOOGLE_CALENDAR to "fingerprint-google"
            ),
            isSuspended = true,
            suspendedOccurrenceAtEpochMillis = occurrenceAt
        )

        val restored = reminder.toEntity().toDomain()

        assertEquals(reminder.meetingUrlsByProvider, restored.meetingUrlsByProvider)
        assertEquals("microsoft-event", restored.microsoftCalendarEventId)
        assertEquals(CalendarProvider.GOOGLE_CALENDAR, restored.meetingProvider)
        assertTrue(restored.isOnlineMeeting)
        assertEquals(
            reminder.syncedFingerprintsByProvider,
            restored.syncedFingerprintsByProvider
        )
        assertTrue(restored.isSuspended)
        assertEquals(occurrenceAt, restored.suspendedOccurrenceAtEpochMillis)
    }
}
