package com.luistureo.voicereminderapp.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.luistureo.voicereminderapp.data.local.dao.LoanDao
import com.luistureo.voicereminderapp.data.local.dao.ReminderDao
import com.luistureo.voicereminderapp.data.local.entity.LoanEntity
import com.luistureo.voicereminderapp.data.local.entity.LoanInstallmentEntity
import com.luistureo.voicereminderapp.data.local.entity.LoanPaymentEntity
import com.luistureo.voicereminderapp.data.local.entity.ReminderEntity

@Database(
    entities = [
        ReminderEntity::class,
        LoanEntity::class,
        LoanPaymentEntity::class,
        LoanInstallmentEntity::class
    ],
    version = 13,
    exportSchema = false
)
abstract class ReminderDatabase : RoomDatabase() {

    abstract fun reminderDao(): ReminderDao
    abstract fun loanDao(): LoanDao

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
                    .addMigrations(
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13
                    )
                    .build()

                instance = newInstance
                newInstance
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

        /*
         * TODO migraciones historicas:
         * Solo existen migraciones verificables desde la version 6. No se debe usar migracion destructiva
         * porque elimina recordatorios reales. Para soportar instalaciones en versiones 1 a 5 sin
         * riesgo, recuperar/exportar los esquemas historicos o crear una migracion conservadora que
         * inspeccione columnas existentes, copie datos a una tabla nueva y nunca borre filas de usuario.
         */
    }
}
