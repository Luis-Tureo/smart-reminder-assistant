package com.luistureo.voicereminderapp.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.luistureo.voicereminderapp.data.local.dao.ReminderDao
import com.luistureo.voicereminderapp.data.local.entity.ReminderEntity

@Database(entities = [ReminderEntity::class], version = 12, exportSchema = false)
abstract class ReminderDatabase : RoomDatabase() {

    abstract fun reminderDao(): ReminderDao

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
                        MIGRATION_11_12
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

        /*
         * TODO migraciones historicas:
         * Solo existen migraciones verificables desde la version 6. No se debe usar migracion destructiva
         * porque elimina recordatorios reales. Para soportar instalaciones en versiones 1 a 5 sin
         * riesgo, recuperar/exportar los esquemas historicos o crear una migracion conservadora que
         * inspeccione columnas existentes, copie datos a una tabla nueva y nunca borre filas de usuario.
         */
    }
}
