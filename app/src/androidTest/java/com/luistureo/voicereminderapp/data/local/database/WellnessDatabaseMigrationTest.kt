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
class WellnessDatabaseMigrationTest {

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ReminderDatabase::class.java
    )

    @Test
    fun migratesSixteenThroughEighteenWithWellnessSchemaAndLegacyRowsPreserved() {
        migrationHelper.createDatabase(DATABASE_NAME, 16).use { database ->
            insertReminder(database)
            insertRoutine(database)
            insertLoan(database)
        }

        val wellnessMigrations = ReminderDatabase.ALL_MIGRATIONS.filter {
            (it.startVersion == 16 && it.endVersion == 17) ||
                (it.startVersion == 17 && it.endVersion == 18)
        }.toTypedArray()

        migrationHelper.runMigrationsAndValidate(
            DATABASE_NAME,
            18,
            true,
            *wellnessMigrations
        ).use { database ->
            assertWellnessSchema(database)
            assertTextValue(database, "reminders", "title", "Recordatorio conservado")
            assertTextValue(database, "routines", "name", "Rutina conservada")
            assertTextValue(database, "loan_records", "personName", "Préstamo conservado")
        }
    }

    private fun insertReminder(database: SupportSQLiteDatabase) {
        val values = ContentValues().apply {
            put("id", 1)
            put("title", "Recordatorio conservado")
            put("detail", "Detalle local")
            put("scheduledAtEpochMillis", 1_800_000_000_000L)
            put("isCompleted", 0)
            put("type", "ONE_TIME")
            put("isUrgent", 0)
            put("source", "MANUAL")
            put("recurrenceInterval", 1)
            put("recurrenceWeekdays", "")
            put("isRecurringActive", 0)
            put("activeAlertRepeatCount", 0)
            put("googleCalendarSyncState", "NOT_SYNCED")
            put("externalIdsByProvider", "")
            put("originProvider", "LOCAL")
            put("syncedProviders", "")
            put("providerSyncStates", "")
            put("syncedFingerprintsByProvider", "")
            put("pendingCreateProviders", "")
            put("pendingUpdateProviders", "")
            put("pendingDeleteProviders", "")
            put("isOnlineMeeting", 0)
            put("meetingUrlsByProvider", "")
            put("isSuspended", 0)
            put("lastEditedSource", "APP")
            put("isAllDay", 0)
            put("hiddenFromApp", 0)
        }
        assertTrue(database.insert("reminders", 0, values) > 0)
    }

    private fun insertRoutine(database: SupportSQLiteDatabase) {
        val values = ContentValues().apply {
            put("id", 1)
            put("name", "Rutina conservada")
            put("description", "Descripción")
            put("category", "PERSONAL")
            put("icon", "routine")
            put("color", 0x123456)
            put("enabled", 1)
            put("period", "MORNING")
            put("assistantMode", "NONE")
            put("voiceEnabled", 0)
            put("motivationBubbleEnabled", 0)
            put("createdAtEpochMillis", 1_700_000_000_000L)
            put("updatedAtEpochMillis", 1_700_000_000_000L)
        }
        assertTrue(database.insert("routines", 0, values) > 0)
    }

    private fun insertLoan(database: SupportSQLiteDatabase) {
        val values = ContentValues().apply {
            put("id", 1)
            put("type", "LENT")
            put("personName", "Préstamo conservado")
            put("principalAmountClp", 50_000L)
            put("loanDateEpochMillis", 1_700_000_000_000L)
            put("dueDateEpochMillis", 1_800_000_000_000L)
            put("reason", "Prueba de migración")
            put("paymentMode", "SINGLE")
            put("installmentCount", 1)
            put("interestEnabled", 0)
            put("interestPercentage", 0.0)
            put("interestMode", "SIMPLE")
            put("interestPeriod", "TOTAL")
            put("totalExpectedAmountClp", 50_000L)
            put("remainingAmountClp", 50_000L)
            put("createdAtEpochMillis", 1_700_000_000_000L)
            put("updatedAtEpochMillis", 1_700_000_000_000L)
            put("status", "ACTIVE")
            put("reminderSameDay", 0)
            put("reminderOneDayBefore", 0)
            put("reminderThreeDaysBefore", 0)
        }
        assertTrue(database.insert("loan_records", 0, values) > 0)
    }

    private fun assertWellnessSchema(database: SupportSQLiteDatabase) {
        WELLNESS_COLUMNS.forEach { (table, expectedColumns) ->
            val actualColumns = database.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameColumn = cursor.getColumnIndexOrThrow("name")
                buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(nameColumn))
                }
            }
            assertEquals("Columnas inesperadas en $table", expectedColumns, actualColumns)
        }
    }

    private fun assertTextValue(
        database: SupportSQLiteDatabase,
        table: String,
        column: String,
        expected: String
    ) {
        database.query("SELECT `$column` FROM `$table` WHERE id = 1").use { cursor ->
            assertTrue("No se conservó la fila de $table", cursor.moveToFirst())
            assertEquals(expected, cursor.getString(0))
        }
    }

    private companion object {
        const val DATABASE_NAME = "wellness-migration-16-18"

        val WELLNESS_COLUMNS = mapOf(
            "nutrition_plans" to setOf(
                "id", "dateEpochDay", "createdAtEpochMillis", "updatedAtEpochMillis"
            ),
            "nutrition_meals" to setOf(
                "id", "planId", "name", "period", "optionalTimeMinutes", "foodsOrDishes",
                "preparationNote", "photoUri", "reminderType", "reminderMinutesBefore",
                "customReminderAtEpochMillis", "status", "personalNotes", "createdAtEpochMillis",
                "updatedAtEpochMillis"
            ),
            "nutrition_hydration_entries" to setOf(
                "id", "dateEpochDay", "amountMl", "loggedAtEpochMillis"
            ),
            "nutrition_shopping_items" to setOf(
                "id", "name", "normalizedName", "quantityOrNote", "category", "checked",
                "archived", "checkedAtEpochDay", "createdAtEpochMillis", "updatedAtEpochMillis"
            ),
            "nutrition_templates" to setOf(
                "id", "name", "description", "preparationComplexity", "practicalBenefits",
                "editable", "builtIn", "builtInKey"
            ),
            "nutrition_template_meals" to setOf(
                "id", "templateId", "period", "name", "foodsOrDishes", "preparationNote",
                "orderPriority"
            ),
            "nutrition_preferences" to setOf(
                "id", "dietaryStyle", "dislikes", "exclusions", "allergiesOrIntolerances",
                "preferredFoods", "organizationalGoals", "enabledMealPeriods", "hydrationEnabled",
                "hydrationTargetMl", "hydrationContainerMl", "hydrationReminderStartMinutes",
                "hydrationReminderEndMinutes", "hydrationReminderIntervalMinutes", "remindersEnabled",
                "assistantVoiceEnabled", "temporaryBubbleEnabled", "preferredChartType",
                "privacyModeEnabled", "updatedAtEpochMillis"
            ),
            "recovery_goals" to setOf(
                "id", "historyKey", "title", "category", "customCategory", "startDateEpochDay",
                "targetDateEpochDay", "personalReason", "motivations", "reductionTrackingEnabled",
                "status", "createdAtEpochMillis", "updatedAtEpochMillis"
            ),
            "recovery_check_ins" to setOf(
                "id", "goalId", "goalHistoryKey", "dateEpochDay", "status", "cravingIntensity",
                "trigger", "helpfulAction", "note", "reducedFrequency", "resetsStreak",
                "createdAtEpochMillis", "updatedAtEpochMillis"
            ),
            "recovery_triggers" to setOf("id", "goalId", "label", "enabled", "sortOrder"),
            "recovery_helpful_actions" to setOf(
                "id", "goalId", "label", "enabled", "sortOrder"
            ),
            "recovery_support_contacts" to setOf(
                "id", "goalId", "name", "description", "phone", "preferredAction"
            ),
            "recovery_milestones" to setOf(
                "id", "goalId", "label", "thresholdDays", "kind", "enabled",
                "achievedAtEpochMillis"
            ),
            "recovery_reminders" to setOf(
                "id", "goalId", "type", "timeMinutes", "enabled", "snoozeMinutes",
                "updatedAtEpochMillis"
            )
        )
    }
}
