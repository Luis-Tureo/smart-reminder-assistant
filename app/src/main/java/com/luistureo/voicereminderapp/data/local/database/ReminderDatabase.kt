package com.luistureo.voicereminderapp.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.luistureo.voicereminderapp.data.local.dao.LoanDao
import com.luistureo.voicereminderapp.data.local.dao.NutritionDao
import com.luistureo.voicereminderapp.data.local.dao.ReminderDao
import com.luistureo.voicereminderapp.data.local.dao.RoutineDao
import com.luistureo.voicereminderapp.data.local.dao.recovery.RecoveryDao
import com.luistureo.voicereminderapp.data.local.entity.HydrationEntryEntity
import com.luistureo.voicereminderapp.data.local.entity.LoanEntity
import com.luistureo.voicereminderapp.data.local.entity.LoanInstallmentEntity
import com.luistureo.voicereminderapp.data.local.entity.LoanPaymentEntity
import com.luistureo.voicereminderapp.data.local.entity.NutritionMealEntity
import com.luistureo.voicereminderapp.data.local.entity.NutritionPlanEntity
import com.luistureo.voicereminderapp.data.local.entity.NutritionPreferenceEntity
import com.luistureo.voicereminderapp.data.local.entity.NutritionTemplateEntity
import com.luistureo.voicereminderapp.data.local.entity.NutritionTemplateMealEntity
import com.luistureo.voicereminderapp.data.local.entity.ReminderEntity
import com.luistureo.voicereminderapp.data.local.entity.RoutineDailyExecutionEntity
import com.luistureo.voicereminderapp.data.local.entity.RoutineEntity
import com.luistureo.voicereminderapp.data.local.entity.RoutineHistoryEntity
import com.luistureo.voicereminderapp.data.local.entity.RoutineTaskEntity
import com.luistureo.voicereminderapp.data.local.entity.RoutineTemplateEntity
import com.luistureo.voicereminderapp.data.local.entity.RoutineTemplateTaskEntity
import com.luistureo.voicereminderapp.data.local.entity.RoutineSuggestionEntity
import com.luistureo.voicereminderapp.data.local.entity.ShoppingItemEntity
import com.luistureo.voicereminderapp.data.local.entity.recovery.RecoveryCheckInEntity
import com.luistureo.voicereminderapp.data.local.entity.recovery.RecoveryGoalEntity
import com.luistureo.voicereminderapp.data.local.entity.recovery.RecoveryHelpfulActionEntity
import com.luistureo.voicereminderapp.data.local.entity.recovery.RecoveryMilestoneEntity
import com.luistureo.voicereminderapp.data.local.entity.recovery.RecoveryReminderEntity
import com.luistureo.voicereminderapp.data.local.entity.recovery.RecoverySupportContactEntity
import com.luistureo.voicereminderapp.data.local.entity.recovery.RecoveryTriggerEntity

@Database(
    entities = [
        ReminderEntity::class,
        LoanEntity::class,
        LoanPaymentEntity::class,
        LoanInstallmentEntity::class,
        RoutineEntity::class,
        RoutineTaskEntity::class,
        RoutineDailyExecutionEntity::class,
        RoutineHistoryEntity::class,
        RoutineTemplateEntity::class,
        RoutineTemplateTaskEntity::class,
        RoutineSuggestionEntity::class,
        NutritionPlanEntity::class,
        NutritionMealEntity::class,
        HydrationEntryEntity::class,
        ShoppingItemEntity::class,
        NutritionTemplateEntity::class,
        NutritionTemplateMealEntity::class,
        NutritionPreferenceEntity::class,
        RecoveryGoalEntity::class,
        RecoveryCheckInEntity::class,
        RecoveryTriggerEntity::class,
        RecoveryHelpfulActionEntity::class,
        RecoverySupportContactEntity::class,
        RecoveryMilestoneEntity::class,
        RecoveryReminderEntity::class
    ],
    version = 18,
    exportSchema = true
)
abstract class ReminderDatabase : RoomDatabase() {

    abstract fun reminderDao(): ReminderDao
    abstract fun loanDao(): LoanDao
    abstract fun routineDao(): RoutineDao
    abstract fun nutritionDao(): NutritionDao
    abstract fun recoveryDao(): RecoveryDao

    companion object {
        @Volatile
        private var instance: ReminderDatabase? = null

        fun getDatabase(context: Context): ReminderDatabase {
            return instance ?: synchronized(this) {
                val newInstance = Room.databaseBuilder(
                    context.applicationContext,
                    ReminderDatabase::class.java,
                    "reminder_database"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .build()

                instance = newInstance
                newInstance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE reminders_v2 (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        detail TEXT NOT NULL,
                        date TEXT NOT NULL,
                        time TEXT NOT NULL,
                        isCompleted INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO reminders_v2 (id, title, detail, date, time, isCompleted)
                    SELECT id, text, text, date, time, isCompleted FROM reminders
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE reminders")
                db.execSQL("ALTER TABLE reminders_v2 RENAME TO reminders")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE reminders ADD COLUMN type TEXT NOT NULL DEFAULT 'DEFAULT'"
                )
            }
        }

        private val MIGRATION_3_6 = object : Migration(3, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE reminders_v6 (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        detail TEXT NOT NULL,
                        scheduledAtEpochMillis INTEGER NOT NULL,
                        isCompleted INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        isUrgent INTEGER NOT NULL,
                        source TEXT NOT NULL,
                        recurrenceUnit TEXT,
                        recurrenceInterval INTEGER NOT NULL,
                        recurrenceWeekdays TEXT NOT NULL,
                        isRecurringActive INTEGER NOT NULL,
                        nextTriggerAtEpochMillis INTEGER,
                        lastTriggeredAtEpochMillis INTEGER,
                        activeAlertAtEpochMillis INTEGER,
                        activeAlertRepeatCount INTEGER NOT NULL,
                        nextUrgentRepeatAtEpochMillis INTEGER
                    )
                    """.trimIndent()
                )
                val legacyScheduleExpression =
                    """
                    COALESCE(
                        CAST(
                            strftime(
                                '%s',
                                substr(date, 7, 4) || '-' ||
                                    substr(date, 4, 2) || '-' ||
                                    substr(date, 1, 2) || ' ' || time || ':00',
                                'utc'
                            ) AS INTEGER
                        ) * 1000,
                        0
                    )
                    """.trimIndent()
                db.execSQL(
                    """
                    INSERT INTO reminders_v6 (
                        id, title, detail, scheduledAtEpochMillis, isCompleted, type,
                        isUrgent, source, recurrenceUnit, recurrenceInterval,
                        recurrenceWeekdays, isRecurringActive, nextTriggerAtEpochMillis,
                        lastTriggeredAtEpochMillis, activeAlertAtEpochMillis,
                        activeAlertRepeatCount, nextUrgentRepeatAtEpochMillis
                    )
                    SELECT
                        id, title, detail, $legacyScheduleExpression, isCompleted, type,
                        0, 'MANUAL', NULL, 1, '', 0, NULL, NULL, NULL, 0, NULL
                    FROM reminders
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE reminders")
                db.execSQL("ALTER TABLE reminders_v6 RENAME TO reminders")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN googleCalendarEventId TEXT")
                db.execSQL(
                    "ALTER TABLE reminders ADD COLUMN googleCalendarSyncState TEXT NOT NULL DEFAULT 'PENDING'"
                )
                db.execSQL(
                    "ALTER TABLE reminders ADD COLUMN googleCalendarLastSyncAtEpochMillis INTEGER"
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN externalIdsByProvider TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE reminders ADD COLUMN originProvider TEXT NOT NULL DEFAULT 'APP'")
                db.execSQL("ALTER TABLE reminders ADD COLUMN syncedProviders TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE reminders ADD COLUMN providerSyncStates TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE reminders ADD COLUMN pendingCreateProviders TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE reminders ADD COLUMN pendingUpdateProviders TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE reminders ADD COLUMN pendingDeleteProviders TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE reminders ADD COLUMN meetingUrl TEXT")
                db.execSQL("ALTER TABLE reminders ADD COLUMN isSuspended INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reminders ADD COLUMN suspendedOccurrenceAtEpochMillis INTEGER")
                db.execSQL("ALTER TABLE reminders ADD COLUMN lastEditedSource TEXT NOT NULL DEFAULT 'APP'")
                db.execSQL("ALTER TABLE reminders ADD COLUMN externalEditNote TEXT")
                db.execSQL("ALTER TABLE reminders ADD COLUMN isAllDay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reminders ADD COLUMN hiddenFromApp INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE reminders ADD COLUMN microsoftCalendarLastSyncAtEpochMillis INTEGER"
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE reminders ADD COLUMN meetingUrlsByProvider TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE reminders ADD COLUMN syncedFingerprintsByProvider TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN microsoftCalendarEventId TEXT")
                db.execSQL("ALTER TABLE reminders ADD COLUMN meetingProvider TEXT")
                db.execSQL(
                    "ALTER TABLE reminders ADD COLUMN isOnlineMeeting INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "UPDATE reminders SET isOnlineMeeting = 1 WHERE meetingUrl IS NOT NULL AND TRIM(meetingUrl) != ''"
                )
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS loan_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        type TEXT NOT NULL,
                        personName TEXT NOT NULL,
                        phoneOrContact TEXT,
                        principalAmountClp INTEGER NOT NULL,
                        loanDateEpochMillis INTEGER NOT NULL,
                        dueDateEpochMillis INTEGER NOT NULL,
                        reason TEXT NOT NULL,
                        attachmentUri TEXT,
                        paymentMode TEXT NOT NULL,
                        installmentCount INTEGER NOT NULL,
                        interestEnabled INTEGER NOT NULL,
                        interestPercentage REAL NOT NULL,
                        interestMode TEXT NOT NULL DEFAULT 'SIMPLE',
                        interestPeriod TEXT NOT NULL DEFAULT 'MONTHLY',
                        totalExpectedAmountClp INTEGER NOT NULL,
                        remainingAmountClp INTEGER NOT NULL,
                        notes TEXT,
                        createdAtEpochMillis INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        reminderSameDay INTEGER NOT NULL,
                        reminderOneDayBefore INTEGER NOT NULL,
                        reminderThreeDaysBefore INTEGER NOT NULL,
                        customReminderAtEpochMillis INTEGER,
                        repeatAfterDueEveryDays INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS loan_payments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        loanId INTEGER NOT NULL,
                        paidAmountClp INTEGER NOT NULL,
                        paymentDateEpochMillis INTEGER NOT NULL,
                        note TEXT,
                        attachmentUri TEXT,
                        createdAtEpochMillis INTEGER NOT NULL,
                        FOREIGN KEY(loanId) REFERENCES loan_records(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_loan_payments_loanId ON loan_payments(loanId)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS loan_installments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        loanId INTEGER NOT NULL,
                        installmentNumber INTEGER NOT NULL,
                        dueDateEpochMillis INTEGER NOT NULL,
                        expectedAmountClp INTEGER NOT NULL,
                        paidAmountClp INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        FOREIGN KEY(loanId) REFERENCES loan_records(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_loan_installments_loanId ON loan_installments(loanId)"
                )
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS routines (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL,
                        category TEXT NOT NULL,
                        icon TEXT NOT NULL,
                        color INTEGER NOT NULL,
                        enabled INTEGER NOT NULL,
                        period TEXT NOT NULL,
                        startTimeMinutes INTEGER,
                        deadlineTimeMinutes INTEGER,
                        assistantMode TEXT NOT NULL,
                        voiceEnabled INTEGER NOT NULL,
                        motivationBubbleEnabled INTEGER NOT NULL,
                        motivationScheduleMinutes INTEGER,
                        createdAtEpochMillis INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_routines_period ON routines(period)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_routines_enabled ON routines(enabled)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS routine_tasks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        routineId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT,
                        orderPriority INTEGER NOT NULL,
                        completed INTEGER NOT NULL,
                        completedOnEpochDay INTEGER,
                        optionalTimeMinutes INTEGER,
                        estimatedDurationMinutes INTEGER,
                        notes TEXT,
                        FOREIGN KEY(routineId) REFERENCES routines(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_routine_tasks_routineId ON routine_tasks(routineId)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_routine_tasks_routineId_orderPriority " +
                        "ON routine_tasks(routineId, orderPriority)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS routine_daily_executions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        dateEpochDay INTEGER NOT NULL,
                        routineId INTEGER NOT NULL,
                        state TEXT NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL,
                        FOREIGN KEY(routineId) REFERENCES routines(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_routine_daily_executions_routineId " +
                        "ON routine_daily_executions(routineId)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_routine_daily_executions_routineId_dateEpochDay " +
                        "ON routine_daily_executions(routineId, dateEpochDay)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS routine_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        dateEpochDay INTEGER NOT NULL,
                        routineId INTEGER NOT NULL,
                        completedTasks INTEGER NOT NULL,
                        totalTasks INTEGER NOT NULL,
                        completionPercentage REAL NOT NULL,
                        finalState TEXT NOT NULL,
                        FOREIGN KEY(routineId) REFERENCES routines(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_routine_history_routineId " +
                        "ON routine_history(routineId)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_routine_history_routineId_dateEpochDay " +
                        "ON routine_history(routineId, dateEpochDay)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS routine_templates (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL,
                        benefitsExplanation TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS routine_template_tasks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        templateId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        description TEXT,
                        orderPriority INTEGER NOT NULL,
                        estimatedDurationMinutes INTEGER,
                        FOREIGN KEY(templateId) REFERENCES routine_templates(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_routine_template_tasks_templateId " +
                        "ON routine_template_tasks(templateId)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_routine_template_tasks_templateId_orderPriority " +
                        "ON routine_template_tasks(templateId, orderPriority)"
                )
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE routine_daily_executions " +
                        "ADD COLUMN assistantGuidanceMode TEXT"
                )
                db.execSQL(
                    "ALTER TABLE routine_history " +
                        "ADD COLUMN assistantGuidanceMode TEXT"
                )
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE routine_history ADD COLUMN periodAtExecution TEXT")
                db.execSQL("ALTER TABLE routine_history ADD COLUMN routineNameAtExecution TEXT")
                db.execSQL(
                    "ALTER TABLE routine_history ADD COLUMN pendingTaskTitles TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL("ALTER TABLE routine_history ADD COLUMN completedAtEpochMillis INTEGER")
                db.execSQL(
                    "ALTER TABLE routine_templates ADD COLUMN period TEXT NOT NULL DEFAULT 'MORNING'"
                )
                db.execSQL(
                    "ALTER TABLE routine_templates ADD COLUMN estimatedTotalDurationMinutes INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL("ALTER TABLE routine_templates ADD COLUMN icon TEXT")
                db.execSQL("ALTER TABLE routine_templates ADD COLUMN color INTEGER")
                db.execSQL(
                    "ALTER TABLE routine_templates ADD COLUMN category TEXT NOT NULL DEFAULT 'Organización'"
                )
                db.execSQL(
                    "ALTER TABLE routine_templates ADD COLUMN editable INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE routine_templates ADD COLUMN builtIn INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL("ALTER TABLE routine_templates ADD COLUMN builtInKey TEXT")
                db.execSQL(
                    "UPDATE routine_templates SET builtInKey = 'legacy_' || id WHERE builtInKey IS NULL"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS routine_suggestions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        routineId INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        message TEXT NOT NULL,
                        primaryAction TEXT NOT NULL,
                        secondaryAction TEXT NOT NULL,
                        createdAtEpochDay INTEGER NOT NULL,
                        dismissedAtEpochDay INTEGER,
                        active INTEGER NOT NULL,
                        FOREIGN KEY(routineId) REFERENCES routines(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_routine_suggestions_routineId " +
                        "ON routine_suggestions(routineId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_routine_suggestions_routineId_type_createdAtEpochDay " +
                        "ON routine_suggestions(routineId, type, createdAtEpochDay)"
                )
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS nutrition_plans (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        dateEpochDay INTEGER NOT NULL,
                        createdAtEpochMillis INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_nutrition_plans_dateEpochDay " +
                        "ON nutrition_plans(dateEpochDay)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS nutrition_meals (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        planId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        period TEXT NOT NULL,
                        optionalTimeMinutes INTEGER,
                        foodsOrDishes TEXT,
                        preparationNote TEXT,
                        photoUri TEXT,
                        reminderType TEXT NOT NULL,
                        reminderMinutesBefore INTEGER,
                        customReminderAtEpochMillis INTEGER,
                        status TEXT NOT NULL,
                        personalNotes TEXT,
                        createdAtEpochMillis INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL,
                        FOREIGN KEY(planId) REFERENCES nutrition_plans(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_nutrition_meals_planId ON nutrition_meals(planId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_nutrition_meals_planId_period " +
                        "ON nutrition_meals(planId, period)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_nutrition_meals_planId_status " +
                        "ON nutrition_meals(planId, status)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_nutrition_meals_customReminderAtEpochMillis " +
                        "ON nutrition_meals(customReminderAtEpochMillis)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS nutrition_hydration_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        dateEpochDay INTEGER NOT NULL,
                        amountMl INTEGER NOT NULL,
                        loggedAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_nutrition_hydration_entries_dateEpochDay " +
                        "ON nutrition_hydration_entries(dateEpochDay)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_nutrition_hydration_entries_loggedAtEpochMillis " +
                        "ON nutrition_hydration_entries(loggedAtEpochMillis)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS nutrition_shopping_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        normalizedName TEXT NOT NULL,
                        quantityOrNote TEXT,
                        category TEXT NOT NULL,
                        checked INTEGER NOT NULL,
                        archived INTEGER NOT NULL,
                        checkedAtEpochDay INTEGER,
                        createdAtEpochMillis INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_nutrition_shopping_items_normalizedName_archived " +
                        "ON nutrition_shopping_items(normalizedName, archived)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_nutrition_shopping_items_checked_archived " +
                        "ON nutrition_shopping_items(checked, archived)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_nutrition_shopping_items_checkedAtEpochDay " +
                        "ON nutrition_shopping_items(checkedAtEpochDay)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS nutrition_templates (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL,
                        preparationComplexity TEXT NOT NULL,
                        practicalBenefits TEXT NOT NULL,
                        editable INTEGER NOT NULL,
                        builtIn INTEGER NOT NULL,
                        builtInKey TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_nutrition_templates_builtInKey " +
                        "ON nutrition_templates(builtInKey)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS nutrition_template_meals (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        templateId INTEGER NOT NULL,
                        period TEXT NOT NULL,
                        name TEXT NOT NULL,
                        foodsOrDishes TEXT,
                        preparationNote TEXT,
                        orderPriority INTEGER NOT NULL,
                        FOREIGN KEY(templateId) REFERENCES nutrition_templates(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_nutrition_template_meals_templateId " +
                        "ON nutrition_template_meals(templateId)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_nutrition_template_meals_templateId_orderPriority " +
                        "ON nutrition_template_meals(templateId, orderPriority)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS nutrition_preferences (
                        id INTEGER NOT NULL,
                        dietaryStyle TEXT NOT NULL,
                        dislikes TEXT NOT NULL,
                        exclusions TEXT NOT NULL,
                        allergiesOrIntolerances TEXT NOT NULL,
                        preferredFoods TEXT NOT NULL,
                        organizationalGoals TEXT NOT NULL,
                        enabledMealPeriods TEXT NOT NULL,
                        hydrationEnabled INTEGER NOT NULL,
                        hydrationTargetMl INTEGER NOT NULL,
                        hydrationContainerMl INTEGER NOT NULL,
                        hydrationReminderStartMinutes INTEGER,
                        hydrationReminderEndMinutes INTEGER,
                        hydrationReminderIntervalMinutes INTEGER,
                        remindersEnabled INTEGER NOT NULL,
                        assistantVoiceEnabled INTEGER NOT NULL,
                        temporaryBubbleEnabled INTEGER NOT NULL,
                        preferredChartType TEXT NOT NULL,
                        privacyModeEnabled INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recovery_goals (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        historyKey TEXT NOT NULL,
                        title TEXT NOT NULL,
                        category TEXT NOT NULL,
                        customCategory TEXT,
                        startDateEpochDay INTEGER,
                        targetDateEpochDay INTEGER,
                        personalReason TEXT,
                        motivations TEXT,
                        reductionTrackingEnabled INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        createdAtEpochMillis INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_recovery_goals_historyKey " +
                        "ON recovery_goals(historyKey)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recovery_goals_status ON recovery_goals(status)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recovery_check_ins (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        goalId INTEGER,
                        goalHistoryKey TEXT NOT NULL,
                        dateEpochDay INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        cravingIntensity INTEGER,
                        `trigger` TEXT,
                        helpfulAction TEXT,
                        note TEXT,
                        reducedFrequency INTEGER NOT NULL,
                        resetsStreak INTEGER NOT NULL,
                        createdAtEpochMillis INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL,
                        FOREIGN KEY(goalId) REFERENCES recovery_goals(id) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recovery_check_ins_goalId ON recovery_check_ins(goalId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recovery_check_ins_goalHistoryKey " +
                        "ON recovery_check_ins(goalHistoryKey)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_recovery_check_ins_goalHistoryKey_dateEpochDay " +
                        "ON recovery_check_ins(goalHistoryKey, dateEpochDay)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recovery_check_ins_dateEpochDay " +
                        "ON recovery_check_ins(dateEpochDay)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recovery_triggers (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        goalId INTEGER NOT NULL,
                        label TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        FOREIGN KEY(goalId) REFERENCES recovery_goals(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recovery_triggers_goalId ON recovery_triggers(goalId)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_recovery_triggers_goalId_label " +
                        "ON recovery_triggers(goalId, label)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recovery_helpful_actions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        goalId INTEGER NOT NULL,
                        label TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        FOREIGN KEY(goalId) REFERENCES recovery_goals(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recovery_helpful_actions_goalId " +
                        "ON recovery_helpful_actions(goalId)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_recovery_helpful_actions_goalId_label " +
                        "ON recovery_helpful_actions(goalId, label)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recovery_support_contacts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        goalId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT,
                        phone TEXT NOT NULL,
                        preferredAction TEXT NOT NULL,
                        FOREIGN KEY(goalId) REFERENCES recovery_goals(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recovery_support_contacts_goalId " +
                        "ON recovery_support_contacts(goalId)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recovery_milestones (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        goalId INTEGER NOT NULL,
                        label TEXT NOT NULL,
                        thresholdDays INTEGER,
                        kind TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        achievedAtEpochMillis INTEGER,
                        FOREIGN KEY(goalId) REFERENCES recovery_goals(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recovery_milestones_goalId " +
                        "ON recovery_milestones(goalId)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_recovery_milestones_goalId_kind_thresholdDays " +
                        "ON recovery_milestones(goalId, kind, thresholdDays)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recovery_reminders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        goalId INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        timeMinutes INTEGER NOT NULL,
                        enabled INTEGER NOT NULL,
                        snoozeMinutes INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL,
                        FOREIGN KEY(goalId) REFERENCES recovery_goals(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recovery_reminders_goalId " +
                        "ON recovery_reminders(goalId)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_recovery_reminders_goalId_type " +
                        "ON recovery_reminders(goalId, type)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recovery_reminders_enabled " +
                        "ON recovery_reminders(enabled)"
                )
            }
        }

        internal val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18
        )
    }
}
