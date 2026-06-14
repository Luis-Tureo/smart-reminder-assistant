package com.luistureo.voicereminderapp.core.calendar.google

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleCalendarSyncPolicyTest {

    @Test
    fun pendingDeletePreventsRecreatingSameRemoteEvent() {
        assertTrue(
            GoogleCalendarSyncPolicy.shouldSkipUpsertForPendingDelete(
                eventId = "event-1",
                pendingDeleteIds = setOf("event-1")
            )
        )
    }

    @Test
    fun blankOrUnknownEventIdDoesNotBlockSync() {
        assertFalse(
            GoogleCalendarSyncPolicy.shouldSkipUpsertForPendingDelete(
                eventId = "",
                pendingDeleteIds = setOf("event-1")
            )
        )
        assertFalse(
            GoogleCalendarSyncPolicy.shouldSkipUpsertForPendingDelete(
                eventId = "event-2",
                pendingDeleteIds = setOf("event-1")
            )
        )
    }

    @Test
    fun pendingDeletesAreSyncedOnlyWhenAccessTokenExists() {
        assertTrue(GoogleCalendarSyncPolicy.shouldSyncPendingDeletes(hasAccessToken = true))
        assertFalse(GoogleCalendarSyncPolicy.shouldSyncPendingDeletes(hasAccessToken = false))
    }
}
