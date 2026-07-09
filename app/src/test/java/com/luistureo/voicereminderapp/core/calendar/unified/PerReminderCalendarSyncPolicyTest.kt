package com.luistureo.voicereminderapp.core.calendar.unified

import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import com.luistureo.voicereminderapp.domain.model.CalendarProviderSyncState
import com.luistureo.voicereminderapp.domain.model.Reminder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerReminderCalendarSyncPolicyTest {

    @Test
    fun newReminderCreatesOnlySelectedProvider() {
        val result = PerReminderCalendarSyncPolicy.applyTargets(
            reminder = reminder(),
            targetProviders = setOf(CalendarProvider.GOOGLE_CALENDAR),
            markExistingForUpdate = false
        )

        assertTrue(CalendarProvider.GOOGLE_CALENDAR in result.pendingCreateProviders)
        assertFalse(CalendarProvider.MICROSOFT_CALENDAR in result.pendingCreateProviders)
    }

    @Test
    fun editMarksSelectedLinkedProviderForUpdateAndRemovedProviderForDelete() {
        val result = PerReminderCalendarSyncPolicy.applyTargets(
            reminder = reminder().copy(
                externalIdsByProvider = mapOf(
                    CalendarProvider.GOOGLE_CALENDAR to "g-1",
                    CalendarProvider.MICROSOFT_CALENDAR to "m-1"
                )
            ),
            targetProviders = setOf(CalendarProvider.GOOGLE_CALENDAR),
            markExistingForUpdate = true
        )

        assertTrue(CalendarProvider.GOOGLE_CALENDAR in result.pendingUpdateProviders)
        assertTrue(CalendarProvider.MICROSOFT_CALENDAR in result.pendingDeleteProviders)
        assertTrue(
            result.providerSyncStates[CalendarProvider.MICROSOFT_CALENDAR] ==
                    CalendarProviderSyncState.PENDING_DELETE
        )
    }

    @Test
    fun importedMeetingCannotBeCopiedToOtherProviderAction() {
        val result = PerReminderCalendarSyncPolicy.canSyncImportedReminderTo(
            reminder = reminder().copy(
                originProvider = CalendarProvider.GOOGLE_CALENDAR,
                isOnlineMeeting = true
            ),
            provider = CalendarProvider.MICROSOFT_CALENDAR,
            isProviderConnected = true
        )

        assertFalse(result)
    }

    @Test
    fun importedNormalEventCanBeCopiedToConnectedOtherProviderAction() {
        val result = PerReminderCalendarSyncPolicy.canSyncImportedReminderTo(
            reminder = reminder().copy(
                originProvider = CalendarProvider.GOOGLE_CALENDAR,
                externalIdsByProvider = mapOf(CalendarProvider.GOOGLE_CALENDAR to "g-1")
            ),
            provider = CalendarProvider.MICROSOFT_CALENDAR,
            isProviderConnected = true
        )

        assertTrue(result)
    }

    private fun reminder(): Reminder {
        return Reminder(
            title = "Reunion",
            detail = "Reunion de equipo",
            scheduledAtEpochMillis = 1_800_000_000_000L
        )
    }
}
