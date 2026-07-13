package com.luistureo.voicereminderapp.domain.notes.model

data class QuickNote(
    val id: Int,
    val title: String?,
    val content: String,
    val isPinned: Boolean,
    val colorTag: QuickNoteColorTag?,
    val createdAt: Long,
    val updatedAt: Long,
    val isArchived: Boolean
)

data class QuickNoteDraft(
    val id: Int = 0,
    val title: String? = null,
    val content: String = "",
    val isPinned: Boolean = false,
    val colorTag: QuickNoteColorTag? = null,
    val isArchived: Boolean = false
)

enum class QuickNoteColorTag {
    YELLOW,
    BLUE,
    GREEN,
    PINK
}

enum class QuickNoteFilter {
    ALL,
    PINNED,
    ARCHIVED
}
