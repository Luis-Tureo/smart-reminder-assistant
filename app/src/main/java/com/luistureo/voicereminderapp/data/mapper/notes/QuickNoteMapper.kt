package com.luistureo.voicereminderapp.data.mapper.notes

import com.luistureo.voicereminderapp.data.local.entity.notes.QuickNoteEntity
import com.luistureo.voicereminderapp.domain.notes.model.QuickNote
import com.luistureo.voicereminderapp.domain.notes.model.QuickNoteColorTag

fun QuickNoteEntity.toDomain(): QuickNote = QuickNote(
    id = id,
    title = title,
    content = content,
    isPinned = isPinned,
    colorTag = colorTag?.let { stored ->
        runCatching { QuickNoteColorTag.valueOf(stored) }.getOrNull()
    },
    createdAt = createdAt,
    updatedAt = updatedAt,
    isArchived = isArchived
)

fun QuickNote.toEntity(): QuickNoteEntity = QuickNoteEntity(
    id = id,
    title = title,
    content = content,
    isPinned = isPinned,
    colorTag = colorTag?.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isArchived = isArchived
)
