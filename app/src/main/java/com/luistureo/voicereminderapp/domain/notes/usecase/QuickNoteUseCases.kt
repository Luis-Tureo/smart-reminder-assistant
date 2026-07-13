package com.luistureo.voicereminderapp.domain.notes.usecase

import com.luistureo.voicereminderapp.domain.notes.model.QuickNote
import com.luistureo.voicereminderapp.domain.notes.model.QuickNoteDraft
import com.luistureo.voicereminderapp.domain.notes.model.QuickNoteFilter
import com.luistureo.voicereminderapp.domain.notes.repository.QuickNoteRepository

class ObserveQuickNotesUseCase(private val repository: QuickNoteRepository) {
    operator fun invoke(filter: QuickNoteFilter, query: String) =
        repository.observeNotes(filter, query)
}

class GetQuickNoteUseCase(private val repository: QuickNoteRepository) {
    suspend operator fun invoke(noteId: Int) = repository.getNote(noteId)
}

class SaveQuickNoteUseCase(private val repository: QuickNoteRepository) {
    suspend operator fun invoke(draft: QuickNoteDraft) = repository.saveNote(draft)
}

class SetQuickNotePinnedUseCase(private val repository: QuickNoteRepository) {
    suspend operator fun invoke(noteId: Int, pinned: Boolean) =
        repository.setPinned(noteId, pinned)
}

class SetQuickNoteArchivedUseCase(private val repository: QuickNoteRepository) {
    suspend operator fun invoke(noteId: Int, archived: Boolean) =
        repository.setArchived(noteId, archived)
}

class DeleteQuickNoteUseCase(private val repository: QuickNoteRepository) {
    suspend operator fun invoke(noteId: Int) = repository.deleteNote(noteId)
}

class RestoreDeletedQuickNoteUseCase(private val repository: QuickNoteRepository) {
    suspend operator fun invoke(note: QuickNote) = repository.restoreDeletedNote(note)
}
