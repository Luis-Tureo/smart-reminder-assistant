package com.luistureo.voicereminderapp.core.calendar.unified

import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import com.luistureo.voicereminderapp.domain.model.CalendarProviderSyncState
import com.luistureo.voicereminderapp.domain.model.Reminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedCalendarSyncPolicyTest {

    @Test
    fun failedProviderKeepsSuccessfulProviderAndMarksPendingUpdate() {
        val reminder = reminder().copy(
            externalIdsByProvider = mapOf(CalendarProvider.GOOGLE_CALENDAR to "g-1"),
            syncedProviders = setOf(CalendarProvider.APP, CalendarProvider.GOOGLE_CALENDAR)
        )

        val result = UnifiedCalendarSyncPolicy.markProviderSyncFailed(
            reminder = reminder,
            provider = CalendarProvider.GOOGLE_CALENDAR,
            state = CalendarProviderSyncState.FAILED
        )

        assertTrue(CalendarProvider.GOOGLE_CALENDAR in result.syncedProviders)
        assertTrue(CalendarProvider.GOOGLE_CALENDAR in result.pendingUpdateProviders)
        assertEquals(CalendarProviderSyncState.FAILED, result.providerSyncStates[CalendarProvider.GOOGLE_CALENDAR])
    }

    @Test
    fun providerDetachDoesNotHideAppointmentFromApp() {
        val result = UnifiedCalendarSyncPolicy.markProviderPendingDelete(
            reminder = reminder(),
            provider = CalendarProvider.MICROSOFT_CALENDAR
        )

        assertFalse(result.hiddenFromApp)
        assertTrue(CalendarProvider.MICROSOFT_CALENDAR in result.pendingDeleteProviders)
        assertEquals(
            CalendarProviderSyncState.PENDING_DELETE,
            result.providerSyncStates[CalendarProvider.MICROSOFT_CALENDAR]
        )
    }

    @Test
    fun appUpsertDoesNotAddProvidersWithoutExplicitTarget() {
        val result = UnifiedCalendarSyncPolicy.prepareAppUpsert(reminder())

        assertEquals(CalendarProvider.APP, result.originProvider)
        assertEquals(CalendarProvider.APP, result.lastEditedSource)
        assertTrue(result.pendingCreateProviders.isEmpty())
    }

    @Test
    fun appUpsertClearsRemoteEditNoteWithoutAddingLinkedProviderUpdates() {
        val result = UnifiedCalendarSyncPolicy.prepareAppUpsert(
            reminder().copy(
                externalIdsByProvider = mapOf(
                    CalendarProvider.GOOGLE_CALENDAR to "g-1",
                    CalendarProvider.MICROSOFT_CALENDAR to "m-1"
                ),
                externalEditNote = "Cita editada desde Google Calendar"
            )
        )

        assertTrue(result.pendingUpdateProviders.isEmpty())
        assertEquals(null, result.externalEditNote)
    }

    @Test
    fun localOnlyDeleteHidesCardWithoutExternalPendingDeletes() {
        val result = UnifiedCalendarSyncPolicy.prepareAppDelete(
            reminder().copy(
                externalIdsByProvider = mapOf(
                    CalendarProvider.GOOGLE_CALENDAR to "g-1"
                )
            ),
            deleteExternalCalendars = false
        )

        assertTrue(result.hiddenFromApp)
        assertTrue(result.pendingDeleteProviders.isEmpty())
    }

    @Test
    fun appDeleteHidesCardAndTracksBothProviderDeletes() {
        val result = UnifiedCalendarSyncPolicy.prepareAppDelete(
            reminder().copy(
                externalIdsByProvider = mapOf(
                    CalendarProvider.GOOGLE_CALENDAR to "g-1",
                    CalendarProvider.MICROSOFT_CALENDAR to "m-1"
                )
            )
        )

        assertTrue(result.hiddenFromApp)
        assertEquals(
            setOf(
                CalendarProvider.GOOGLE_CALENDAR,
                CalendarProvider.MICROSOFT_CALENDAR
            ),
            result.pendingDeleteProviders
        )
    }

    @Test
    fun successfulProviderDeleteKeepsOtherProviderPending() {
        val pending = UnifiedCalendarSyncPolicy.prepareAppDelete(
            reminder().copy(
                externalIdsByProvider = mapOf(
                    CalendarProvider.GOOGLE_CALENDAR to "g-1",
                    CalendarProvider.MICROSOFT_CALENDAR to "m-1"
                )
            )
        )

        val result = UnifiedCalendarSyncPolicy.markProviderDeleteSynced(
            pending,
            CalendarProvider.GOOGLE_CALENDAR
        )

        assertFalse(CalendarProvider.GOOGLE_CALENDAR in result.pendingDeleteProviders)
        assertTrue(CalendarProvider.MICROSOFT_CALENDAR in result.pendingDeleteProviders)
        assertTrue(result.hiddenFromApp)
    }

    @Test
    fun successfulSyncClearsOnlyRealPendingOperationsForProvider() {
        val result = UnifiedCalendarSyncPolicy.markProviderSynced(
            reminder().copy(
                pendingCreateProviders = setOf(CalendarProvider.MICROSOFT_CALENDAR),
                pendingUpdateProviders = setOf(CalendarProvider.MICROSOFT_CALENDAR),
                pendingDeleteProviders = setOf(CalendarProvider.GOOGLE_CALENDAR)
            ),
            CalendarProvider.MICROSOFT_CALENDAR,
            "m-1",
            100L
        )

        assertFalse(CalendarProvider.MICROSOFT_CALENDAR in result.pendingProviders)
        assertTrue(CalendarProvider.GOOGLE_CALENDAR in result.pendingProviders)
        assertEquals("m-1", result.microsoftCalendarEventId)
    }

    private fun reminder(): Reminder {
        return Reminder(
            title = "Reunion",
            detail = "Reunion de equipo",
            scheduledAtEpochMillis = 1_800_000_000_000L
        )
    }
}
