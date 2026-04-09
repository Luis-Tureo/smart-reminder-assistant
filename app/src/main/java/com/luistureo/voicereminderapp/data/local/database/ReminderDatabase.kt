package com.luistureo.voicereminderapp.data.local.database

import android.content.Context
import androidx.room.Room
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.luistureo.voicereminderapp.data.local.dao.ReminderDao
import com.luistureo.voicereminderapp.data.local.entity.ReminderEntity

@Database(entities = [ReminderEntity::class], version = 3)
abstract class ReminderDatabase : RoomDatabase() {

    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile
        private var instance: ReminderDatabase? = null

        private val migration2To3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Se agrega el tipo para clasificar recordatorios sin perder los datos actuales.
                database.execSQL(
                    """
                    ALTER TABLE reminders
                    ADD COLUMN type TEXT NOT NULL DEFAULT 'DEFAULT'
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): ReminderDatabase {
            return instance ?: synchronized(this) {
                val newInstance = Room.databaseBuilder(
                    context.applicationContext,
                    ReminderDatabase::class.java,
                    "reminder_database"
                )
                    .addMigrations(migration2To3)
                    .fallbackToDestructiveMigration()
                    .build()

                instance = newInstance
                newInstance
            }
        }
    }
}
