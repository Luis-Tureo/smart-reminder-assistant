package com.luistureo.voicereminderapp.data.local.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderDatabaseLegacyMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "legacy-reminder-migration-test"
    private var migratedDatabase: ReminderDatabase? = null

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReminderDatabase::class.java
    )

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

    @Test
    fun migrationSeventeenKeepsRemindersAndDropsOnlyRetiredTables() {
        migrationHelper.createDatabase(databaseName, 16).apply {
            execSQL(
                """
                INSERT INTO reminders (
                    id, title, detail, scheduledAtEpochMillis, isCompleted, type, isUrgent,
                    source, recurrenceInterval, recurrenceWeekdays, isRecurringActive,
                    activeAlertRepeatCount, googleCalendarSyncState, externalIdsByProvider,
                    originProvider, syncedProviders, providerSyncStates,
                    syncedFingerprintsByProvider, pendingCreateProviders, pendingUpdateProviders,
                    pendingDeleteProviders, isOnlineMeeting, meetingUrlsByProvider, isSuspended,
                    lastEditedSource, isAllDay, hiddenFromApp
                ) VALUES (
                    99, 'Reunion preservada', 'Detalle', 1786703400000, 0, 'DEFAULT', 0,
                    'MANUAL', 1, '', 0, 0, 'PENDING', '', 'APP', '', '', '', '', '', '',
                    0, '', 0, 'APP', 0, 0
                )
                """.trimIndent()
            )
            close()
        }

        migrationHelper.runMigrationsAndValidate(
            databaseName,
            17,
            true,
            *ReminderDatabase.ALL_MIGRATIONS
        ).use { database ->
            database.query("SELECT title FROM reminders WHERE id = 99").use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("Reunion preservada", cursor.getString(0))
            }
            assertEquals(false, database.hasTable("loan_records"))
            assertEquals(false, database.hasTable("routines"))
            assertEquals(true, database.hasTable("reminders"))
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.hasTable(tableName: String): Boolean =
        query(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
            arrayOf(tableName)
        ).use { it.moveToFirst() }
}
