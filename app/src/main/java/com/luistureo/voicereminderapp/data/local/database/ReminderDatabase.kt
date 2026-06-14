package com.luistureo.voicereminderapp.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.luistureo.voicereminderapp.data.local.dao.ReminderDao
import com.luistureo.voicereminderapp.data.local.entity.ReminderEntity

@Database(entities = [ReminderEntity::class], version = 7, exportSchema = false)
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
                    .addMigrations(MIGRATION_6_7)
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

        /*
         * TODO migraciones historicas:
         * Solo existe una migracion verificable de 6 a 7. No se debe usar migracion destructiva
         * porque elimina recordatorios reales. Para soportar instalaciones en versiones 1 a 5 sin
         * riesgo, recuperar/exportar los esquemas historicos o crear una migracion conservadora que
         * inspeccione columnas existentes, copie datos a una tabla nueva y nunca borre filas de usuario.
         */
    }
}
