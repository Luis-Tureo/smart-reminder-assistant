package com.luistureo.voicereminderapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.luistureo.voicereminderapp.domain.model.ReminderType

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val detail: String,
    val date: String,
    val time: String,
    val isCompleted: Boolean = false,
    val type: String = ReminderType.DEFAULT.name
)
