package com.luistureo.voicereminderapp.presentation.notes

import com.luistureo.voicereminderapp.domain.notes.model.QuickNote
import com.luistureo.voicereminderapp.domain.notes.model.QuickNoteFilter

data class QuickNotesUiState(
    val notes: List<QuickNote> = emptyList(),
    val query: String = "",
    val filter: QuickNoteFilter = QuickNoteFilter.ALL,
    val isLoading: Boolean = true
)

sealed interface QuickNotesEvent {
    data class NoteDeleted(val note: QuickNote) : QuickNotesEvent
    data object DeleteFailed : QuickNotesEvent
    data object RestoreFailed : QuickNotesEvent
    data object UpdateFailed : QuickNotesEvent
}
