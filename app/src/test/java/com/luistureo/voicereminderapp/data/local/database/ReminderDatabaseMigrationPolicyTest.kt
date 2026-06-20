package com.luistureo.voicereminderapp.data.local.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReminderDatabaseMigrationPolicyTest {

    @Test
    fun destructiveMigrationIsDisabledForUserReminderSafety() {
        assertFalse(ReminderDatabaseMigrationPolicy.USE_DESTRUCTIVE_MIGRATION)
    }

    @Test
    fun documentsOnlyKnownSafeMigrationRange() {
        assertEquals(
            listOf(6 to 7, 7 to 8, 8 to 9, 9 to 10, 10 to 11, 11 to 12),
            ReminderDatabaseMigrationPolicy.supportedMigrationRanges
        )
    }

    @Test
    fun migrationElevenToTwelveAddsMeetingMetadataWithoutDestructiveFallback() {
        val source = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/data/local/database/ReminderDatabase.kt"
        ).readText()

        assertTrue(source.contains("Migration(11, 12)"))
        assertTrue(source.contains("ADD COLUMN microsoftCalendarEventId TEXT"))
        assertTrue(source.contains("ADD COLUMN meetingProvider TEXT"))
        assertTrue(source.contains("ADD COLUMN isOnlineMeeting INTEGER NOT NULL DEFAULT 0"))
        assertFalse(source.contains("fallbackToDestructiveMigration"))
    }

    private fun sourceFile(projectPath: String): File {
        val fromRoot = File(projectPath)
        if (fromRoot.exists()) return fromRoot
        return File(projectPath.removePrefix("app/"))
    }
}
