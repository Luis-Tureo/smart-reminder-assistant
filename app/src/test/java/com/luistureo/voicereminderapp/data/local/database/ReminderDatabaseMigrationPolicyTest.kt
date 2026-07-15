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
            listOf(
                1 to 2,
                2 to 3,
                3 to 6,
                6 to 7,
                7 to 8,
                8 to 9,
                9 to 10,
                10 to 11,
                11 to 12,
                12 to 13,
                13 to 14,
                14 to 15,
                15 to 16,
                16 to 17
            ),
            ReminderDatabaseMigrationPolicy.supportedMigrationRanges
        )
    }

    @Test
    fun legacyReminderMigrationsPreserveRowsWithoutDestructiveFallback() {
        val source = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/data/local/database/ReminderDatabase.kt"
        ).readText()

        assertTrue(source.contains("Migration(1, 2)"))
        assertTrue(source.contains("SELECT id, text, text, date, time, isCompleted"))
        assertTrue(source.contains("Migration(2, 3)"))
        assertTrue(source.contains("Migration(3, 6)"))
        assertTrue(source.contains("scheduledAtEpochMillis"))
        assertFalse(source.contains("fallbackToDestructiveMigration"))
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

    @Test
    fun migrationSixteenToSeventeenRemovesOnlyRetiredModuleTables() {
        val source = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/data/local/database/ReminderDatabase.kt"
        ).readText()

        assertTrue(source.contains("Migration(16, 17)"))
        assertTrue(source.contains("DROP TABLE IF EXISTS loan_records"))
        assertTrue(source.contains("DROP TABLE IF EXISTS routines"))
        assertFalse(source.contains("DROP TABLE IF EXISTS reminders"))
        assertFalse(source.contains("fallbackToDestructiveMigration"))
    }

    private fun sourceFile(projectPath: String): File {
        val fromRoot = File(projectPath)
        if (fromRoot.exists()) return fromRoot
        return File(projectPath.removePrefix("app/"))
    }
}
