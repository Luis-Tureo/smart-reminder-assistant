package com.luistureo.voicereminderapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val text: String,
    val date: String,
    val time: String,
    val isCompleted: Boolean = false
)