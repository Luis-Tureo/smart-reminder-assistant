package com.luistureo.voicereminderapp.presentation.notes

import com.luistureo.voicereminderapp.domain.notes.model.QuickNoteColorTag
import com.luistureo.voicereminderapp.domain.notes.model.QuickNoteDraft

enum class QuickNoteSaveState {
    IDLE,
    SAVING,
    SAVED,
    ERROR
}

data class QuickNoteEditorUiState(
    val id: Int = 0,
    val title: String = "",
    val content: String = "",
    val isPinned: Boolean = false,
    val colorTag: QuickNoteColorTag? = null,
    val isArchived: Boolean = false,
    val isLoaded: Boolean = false,
    val saveState: QuickNoteSaveState = QuickNoteSaveState.IDLE
) {
    fun toDraft(): QuickNoteDraft = QuickNoteDraft(
        id = id,
        title = title,
        content = content,
        isPinned = isPinned,
        colorTag = colorTag,
        isArchived = isArchived
    )
}

sealed interface QuickNoteEditorEvent {
    data object ShowValidation : QuickNoteEditorEvent
    data class Share(val title: String?, val content: String) : QuickNoteEditorEvent
}
