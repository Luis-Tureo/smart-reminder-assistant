package com.luistureo.voicereminderapp.core.calendar.unified

import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarSyncLoggerTest {

    @Test
    fun buildMessageKeepsOperationalFieldsAndRedactsSensitiveFields() {
        val message = CalendarSyncLogger.buildMessage(
            provider = CalendarProvider.GOOGLE_CALENDAR,
            action = "sync_pending",
            fields = mapOf(
                "apiStatus" to 200,
                "pendingCreate" to 2,
                "accessToken" to "secret-token",
                "detail" to "private appointment detail"
            )
        )

        assertTrue(message.contains("provider=GOOGLE_CALENDAR"))
        assertTrue(message.contains("action=sync_pending"))
        assertTrue(message.contains("apiStatus=200"))
        assertTrue(message.contains("pendingCreate=2"))
        assertTrue(message.contains("accessToken=<redacted>"))
        assertTrue(message.contains("detail=<redacted>"))
        assertFalse(message.contains("secret-token"))
        assertFalse(message.contains("private appointment detail"))
    }

    @Test
    fun duplicateWarningSuspensionMeetingAndAlarmHelpersDoNotCrash() {
        CalendarSyncLogger.duplicateWarningShownInline(
            duplicateCount = 3,
            selectedDay = "2026-06-15"
        )
        CalendarSyncLogger.appointmentSuspended(CalendarProvider.GOOGLE_CALENDAR, true)
        CalendarSyncLogger.appointmentReactivated(CalendarProvider.MICROSOFT_CALENDAR)
        CalendarSyncLogger.meetingLinkDetected(CalendarProvider.GOOGLE_CALENDAR)
        CalendarSyncLogger.alarmSkippedForSuspendedAppointment(CalendarProvider.APP)
    }

    @Test
    fun quotaTagAndOperationalFieldsContainNoSensitiveValues() {
        val message = CalendarSyncLogger.buildMessage(
            provider = CalendarProvider.MICROSOFT_CALENDAR,
            action = "retry_backoff",
            fields = mapOf(
                "requestType" to "calendar_delta",
                "attempt" to 2,
                "delayMillis" to 4_000,
                "refreshToken" to "private-token"
            )
        )

        assertEquals("CalendarQuota", CalendarSyncLogger.TAG_CALENDAR_QUOTA)
        assertTrue(message.contains("requestType=calendar_delta"))
        assertTrue(message.contains("refreshToken=<redacted>"))
        assertFalse(message.contains("private-token"))
    }

    @Test
    fun googleFlowTagsAndStateFieldsAreSafeAndStable() {
        val message = CalendarSyncLogger.buildMessage(
            provider = CalendarProvider.GOOGLE_CALENDAR,
            action = "provider_state_changed",
            fields = mapOf(
                "stateBefore" to "PAUSED",
                "stateAfter" to "CONNECTED",
                "errorCode" to "GOOGLE_AUTH_BAD_AUTHENTICATION",
                "email" to "private@example.com"
            )
        )

        assertEquals("CalendarAuth", CalendarSyncLogger.TAG_CALENDAR_AUTH)
        assertEquals("GoogleCalendarSync", CalendarSyncLogger.TAG_GOOGLE_CALENDAR_SYNC)
        assertEquals("CalendarError", CalendarSyncLogger.TAG_CALENDAR_ERROR)
        assertEquals("CalendarUi", CalendarSyncLogger.TAG_CALENDAR_UI)
        assertEquals(
            "CalendarDuplicateCheck",
            CalendarSyncLogger.TAG_CALENDAR_DUPLICATE_CHECK
        )
        assertTrue(message.contains("provider=GOOGLE_CALENDAR"))
        assertTrue(message.contains("stateBefore=PAUSED"))
        assertTrue(message.contains("stateAfter=CONNECTED"))
        assertTrue(message.contains("errorCode=GOOGLE_AUTH_BAD_AUTHENTICATION"))
        assertFalse(message.contains("private@example.com"))
    }
}
