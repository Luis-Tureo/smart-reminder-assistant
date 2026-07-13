package com.luistureo.voicereminderapp.data.local.database

import android.content.ContentValues
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickNotesDatabaseMigrationTest {

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReminderDatabase::class.java
    )

    @Test
    fun migratesEighteenToNineteenWithQuickNotesSchemaAndExistingRowsPreserved() {
        migrationHelper.createDatabase(DATABASE_NAME, 18).use { database ->
            insertHydrationEntry(database)
            insertRecoveryGoal(database)
        }

        val migration = ReminderDatabase.ALL_MIGRATIONS.single {
            it.startVersion == 18 && it.endVersion == 19
        }

        migrationHelper.runMigrationsAndValidate(
            DATABASE_NAME,
            19,
            true,
            migration
        ).use { database ->
            assertQuickNotesSchema(database)
            assertQuickNotesIndex(database)
            assertExistingRowsWerePreserved(database)
            assertQuickNotesTableIsWritable(database)
        }
    }

    private fun insertHydrationEntry(database: SupportSQLiteDatabase) {
        val values = ContentValues().apply {
            put("id", HYDRATION_ID)
            put("dateEpochDay", 20_100L)
            put("amountMl", 450)
            put("loggedAtEpochMillis", 1_750_000_000_000L)
        }
        assertTrue(database.insert("nutrition_hydration_entries", 0, values) > 0)
    }

    private fun insertRecoveryGoal(database: SupportSQLiteDatabase) {
        val values = ContentValues().apply {
            put("id", RECOVERY_GOAL_ID)
            put("historyKey", "history-preserved-v18")
            put("title", "Meta conservada")
            put("category", "OTHER")
            putNull("customCategory")
            putNull("startDateEpochDay")
            putNull("targetDateEpochDay")
            putNull("personalReason")
            putNull("motivations")
            put("reductionTrackingEnabled", 0)
            put("status", "ACTIVE")
            put("createdAtEpochMillis", 1_740_000_000_000L)
            put("updatedAtEpochMillis", 1_740_000_000_000L)
        }
        assertTrue(database.insert("recovery_goals", 0, values) > 0)
    }

    private fun assertQuickNotesSchema(database: SupportSQLiteDatabase) {
        val columns = database.query("PRAGMA table_info(`quick_notes`)").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(nameColumn))
            }
        }

        assertEquals(
            setOf(
                "id",
                "title",
                "content",
                "isPinned",
                "colorTag",
                "createdAt",
                "updatedAt",
                "isArchived"
            ),
            columns
        )
    }

    private fun assertQuickNotesIndex(database: SupportSQLiteDatabase) {
        val indexNames = database.query("PRAGMA index_list(`quick_notes`)").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            buildSet {
                while (cursor.moveToNext()) add(cursor.getString(nameColumn))
            }
        }
        assertTrue(INDEX_NAME in indexNames)

        val indexedColumns = database.query("PRAGMA index_info(`$INDEX_NAME`)").use { cursor ->
            val sequenceColumn = cursor.getColumnIndexOrThrow("seqno")
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getInt(sequenceColumn) to cursor.getString(nameColumn))
                }
            }.sortedBy { it.first }.map { it.second }
        }
        assertEquals(listOf("isArchived", "isPinned", "updatedAt"), indexedColumns)
    }

    private fun assertExistingRowsWerePreserved(database: SupportSQLiteDatabase) {
        database.query(
            "SELECT amountMl FROM nutrition_hydration_entries WHERE id = $HYDRATION_ID"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(450, cursor.getInt(0))
        }

        database.query(
            "SELECT historyKey, title FROM recovery_goals WHERE id = $RECOVERY_GOAL_ID"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("history-preserved-v18", cursor.getString(0))
            assertEquals("Meta conservada", cursor.getString(1))
        }
    }

    private fun assertQuickNotesTableIsWritable(database: SupportSQLiteDatabase) {
        database.query("SELECT COUNT(*) FROM quick_notes").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }

        val values = ContentValues().apply {
            put("title", "Idea local")
            put("content", "Contenido conservado solo en Room")
            put("isPinned", 1)
            put("colorTag", "BLUE")
            put("createdAt", 1_760_000_000_000L)
            put("updatedAt", 1_760_000_000_100L)
            put("isArchived", 0)
        }
        val insertedId = database.insert("quick_notes", 0, values)
        assertTrue(insertedId > 0)

        database.query(
            "SELECT title, content, isPinned, colorTag, isArchived " +
                "FROM quick_notes WHERE id = $insertedId"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Idea local", cursor.getString(0))
            assertEquals("Contenido conservado solo en Room", cursor.getString(1))
            assertEquals(1, cursor.getInt(2))
            assertEquals("BLUE", cursor.getString(3))
            assertEquals(0, cursor.getInt(4))
        }
    }

    private companion object {
        const val DATABASE_NAME = "quick-notes-migration-18-19"
        const val HYDRATION_ID = 91
        const val RECOVERY_GOAL_ID = 92
        const val INDEX_NAME = "index_quick_notes_isArchived_isPinned_updatedAt"
    }
}
