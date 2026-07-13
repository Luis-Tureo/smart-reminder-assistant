package com.luistureo.voicereminderapp.presentation.notes

import com.luistureo.voicereminderapp.domain.notes.model.QuickNote
import com.luistureo.voicereminderapp.domain.notes.model.QuickNoteDraft
import com.luistureo.voicereminderapp.domain.notes.model.QuickNoteFilter
import com.luistureo.voicereminderapp.domain.notes.repository.QuickNoteRepository
import com.luistureo.voicereminderapp.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuickNotesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `deleted note remains available for a late undo observer`() =
        runTest(mainDispatcherRule.dispatcher) {
            val note = note()
            val repository = FakeListRepository(note)
            val viewModel = QuickNotesViewModel(repository)

            viewModel.delete(note)
            advanceUntilIdle()

            assertEquals(note, viewModel.pendingDeleted.first { it != null })
            viewModel.dismissDeleteUndo(note.id)
            assertNull(viewModel.pendingDeleted.value)
        }

    @Test
    fun `undo restores the deleted note and clears pending state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val note = note()
            val repository = FakeListRepository(note)
            val viewModel = QuickNotesViewModel(repository)

            viewModel.delete(note)
            advanceUntilIdle()
            viewModel.restoreDeleted(note)
            advanceUntilIdle()

            assertEquals(note, repository.row)
            assertNull(viewModel.pendingDeleted.value)
        }

    @Test
    fun `update failure is buffered until the screen observes events`() =
        runTest(mainDispatcherRule.dispatcher) {
            val note = note()
            val repository = FakeListRepository(note, failUpdates = true)
            val viewModel = QuickNotesViewModel(repository)

            viewModel.togglePinned(note)
            advanceUntilIdle()

            assertEquals(QuickNotesEvent.UpdateFailed, viewModel.events.first())
        }

    private fun note() = QuickNote(
        id = 7,
        title = "Idea",
        content = "Contenido local",
        isPinned = false,
        colorTag = null,
        createdAt = 10L,
        updatedAt = 20L,
        isArchived = false
    )

    private class FakeListRepository(
        initial: QuickNote,
        private val failUpdates: Boolean = false
    ) : QuickNoteRepository {
        var row: QuickNote? = initial

        override fun observeNotes(filter: QuickNoteFilter, query: String): Flow<List<QuickNote>> =
            MutableStateFlow(listOfNotNull(row))

        override suspend fun getNote(noteId: Int): QuickNote? = row?.takeIf { it.id == noteId }

        override suspend fun saveNote(draft: QuickNoteDraft): QuickNote? = null

        override suspend fun setPinned(noteId: Int, pinned: Boolean): Boolean = !failUpdates

        override suspend fun setArchived(noteId: Int, archived: Boolean): Boolean = !failUpdates

        override suspend fun deleteNote(noteId: Int): QuickNote? =
            row?.takeIf { it.id == noteId }?.also { row = null }

        override suspend fun restoreDeletedNote(note: QuickNote): Boolean {
            row = note
            return true
        }
    }
}
