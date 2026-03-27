package com.luistureo.voicereminderapp.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.luistureo.voicereminderapp.data.local.dao.ReminderDao
import com.luistureo.voicereminderapp.data.local.entity.ReminderEntity

@Database(entities = [ReminderEntity::class], version = 1)
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
                ).build()

                instance = newInstance
                newInstance
            }
        }
    }
}