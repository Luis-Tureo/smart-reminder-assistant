package com.luistureo.voicereminderapp.domain.notes.repository

import com.luistureo.voicereminderapp.domain.notes.model.QuickNote
import com.luistureo.voicereminderapp.domain.notes.model.QuickNoteDraft
import com.luistureo.voicereminderapp.domain.notes.model.QuickNoteFilter
import kotlinx.coroutines.flow.Flow

interface QuickNoteRepository {
    fun observeNotes(filter: QuickNoteFilter, query: String): Flow<List<QuickNote>>
    suspend fun getNote(noteId: Int): QuickNote?
    suspend fun saveNote(draft: QuickNoteDraft): QuickNote?
    suspend fun setPinned(noteId: Int, pinned: Boolean): Boolean
    suspend fun setArchived(noteId: Int, archived: Boolean): Boolean
    suspend fun deleteNote(noteId: Int): QuickNote?
    suspend fun restoreDeletedNote(note: QuickNote): Boolean
}
