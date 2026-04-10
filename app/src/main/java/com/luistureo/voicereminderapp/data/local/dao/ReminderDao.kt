package com.luistureo.voicereminderapp.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.luistureo.voicereminderapp.data.local.entity.ReminderEntity

@Dao
interface ReminderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Query("SELECT * FROM reminders ORDER BY COALESCE(nextTriggerAtEpochMillis, scheduledAtEpochMillis) ASC, id DESC")
    suspend fun getAllReminders(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :reminderId LIMIT 1")
    suspend fun getReminderById(reminderId: Int): ReminderEntity?

    @Delete
    suspend fun deleteReminder(reminder: ReminderEntity)

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)
}
