package com.luistureo.voicereminderapp.core.calendar.unified

import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import com.luistureo.voicereminderapp.domain.model.Reminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarEventFingerprintTest {

    @Test
    fun providerBoilerplateDoesNotCreateGoogleMicrosoftCopyLoop() {
        val base = reminder("Revision semanal")
        val googleCopy = reminder(
            "Revision semanal\n\nEstado: Pendiente\nUrgente: No\n\n" +
                    "Creado desde Smart Reminder Assistant."
        )
        val microsoftCopy = reminder(
            "Revision semanal\n\nCreado desde Smart Reminder Assistant."
        )

        assertEquals(
            CalendarEventFingerprint.fromReminder(base),
            CalendarEventFingerprint.fromReminder(googleCopy)
        )
        assertEquals(
            CalendarEventFingerprint.fromReminder(base),
            CalendarEventFingerprint.fromReminder(microsoftCopy)
        )
    }

    @Test
    fun successfulProviderSyncStoresFingerprintAndKeepsProviderId() {
        val result = UnifiedCalendarSyncPolicy.markProviderSynced(
            reminder = reminder("Revision semanal"),
            provider = CalendarProvider.GOOGLE_CALENDAR,
            externalId = "google-event-1",
            syncedAtEpochMillis = 1_800_000_000_000L
        )

        assertTrue(result.syncedFingerprintsByProvider[CalendarProvider.GOOGLE_CALENDAR]
            .orEmpty().isNotBlank())
        assertEquals(
            "google-event-1",
            result.externalIdsByProvider[CalendarProvider.GOOGLE_CALENDAR]
        )
    }

    @Test
    fun repeatedCreateUsesStableProviderIdempotencyKeys() {
        assertEquals(
            CalendarIdempotencyKey.googleEventId(42),
            CalendarIdempotencyKey.googleEventId(42)
        )
        assertEquals(
            CalendarIdempotencyKey.microsoftTransactionId(42),
            CalendarIdempotencyKey.microsoftTransactionId(42)
        )
    }

    private fun reminder(detail: String): Reminder {
        return Reminder(
            title = "Reunion",
            detail = detail,
            scheduledAtEpochMillis = 1_900_000_000_000L
        )
    }
}
