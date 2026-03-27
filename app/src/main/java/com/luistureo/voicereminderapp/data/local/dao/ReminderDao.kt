package com.luistureo.voicereminderapp.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.luistureo.voicereminderapp.data.local.entity.ReminderEntity

@Dao
interface ReminderDao {

    @Insert
    suspend fun insertReminder(reminder: ReminderEntity)

    @Query("SELECT * FROM reminders ORDER BY id DESC")
    suspend fun getAllReminders(): List<ReminderEntity>

    @Delete
    suspend fun deleteReminder(reminder: ReminderEntity)

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)
}