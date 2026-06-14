package com.luistureo.voicereminderapp.data.local.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReminderDatabaseMigrationPolicyTest {

    @Test
    fun destructiveMigrationIsDisabledForUserReminderSafety() {
        assertFalse(ReminderDatabaseMigrationPolicy.USE_DESTRUCTIVE_MIGRATION)
    }

    @Test
    fun documentsOnlyKnownSafeMigrationRange() {
        assertEquals(listOf(6 to 7), ReminderDatabaseMigrationPolicy.supportedMigrationRanges)
    }
}
