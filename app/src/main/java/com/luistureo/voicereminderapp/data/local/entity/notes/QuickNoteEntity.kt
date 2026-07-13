package com.luistureo.voicereminderapp.data.local.entity.notes

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "quick_notes",
    indices = [
        Index(value = ["isArchived", "isPinned", "updatedAt"])
    ]
)
data class QuickNoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String?,
    val content: String,
    val isPinned: Boolean,
    val colorTag: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val isArchived: Boolean
)
