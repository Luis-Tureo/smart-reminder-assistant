package com.luistureo.voicereminderapp.data.local.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderDatabaseLegacyMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "legacy-reminder-migration-test"
    private var migratedDatabase: ReminderDatabase? = null

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        migratedDatabase?.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migratesVersionOneReminderToCurrentSchemaWithoutLosingContent() = runBlocking {
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName), null).use { db ->
            db.execSQL(
                """
                CREATE TABLE reminders (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    text TEXT NOT NULL,
                    date TEXT NOT NULL,
                    time TEXT NOT NULL,
                    isCompleted INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "INSERT INTO reminders (id, text, date, time, isCompleted) " +
                    "VALUES (1, 'Control medico', '15/08/2026', '09:30', 0)"
            )
            db.version = 1
        }

        migratedDatabase = Room.databaseBuilder(
            context,
            ReminderDatabase::class.java,
            databaseName
        )
            .addMigrations(*ReminderDatabase.ALL_MIGRATIONS)
            .build()

        val reminder = migratedDatabase?.reminderDao()?.getReminderById(1)
        assertEquals("Control medico", reminder?.title)
        assertEquals("Control medico", reminder?.detail)
        assertFalse(reminder?.isCompleted ?: true)
        assertEquals("MANUAL", reminder?.source)
    }
}
