package com.luistureo.voicereminderapp.presentation.notes

import androidx.lifecycle.SavedStateHandle
import com.luistureo.voicereminderapp.domain.notes.model.QuickNote
import com.luistureo.voicereminderapp.domain.notes.model.QuickNoteDraft
import com.luistureo.voicereminderapp.domain.notes.model.QuickNoteFilter
import com.luistureo.voicereminderapp.domain.notes.repository.QuickNoteRepository
import com.luistureo.voicereminderapp.domain.notes.validation.QuickNoteValidator
import com.luistureo.voicereminderapp.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuickNoteEditorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `autosave creates one row and later changes update the same note`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeEditorRepository()
            val viewModel = QuickNoteEditorViewModel(
                SavedStateHandle(),
                repository,
                autosaveDebounceMillis = 500L
            )

            viewModel.updateContent("Primera versión")
            advanceTimeBy(499L)
            runCurrent()
            assertEquals(0, repository.saveCalls)

            advanceTimeBy(1L)
            runCurrent()
            assertEquals(1, repository.saveCalls)
            assertEquals(1, repository.rows.size)
            val savedId = viewModel.uiState.value.id

            viewModel.updateContent("Segunda versión")
            advanceUntilIdle()

            assertEquals(2, repository.saveCalls)
            assertEquals(1, repository.rows.size)
            assertEquals(savedId, viewModel.uiState.value.id)
            assertEquals("Segunda versión", repository.rows.getValue(savedId).content)
        }

    @Test
    fun `restored saved state updates existing row without creating duplicates`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeEditorRepository()
            val handle = SavedStateHandle()
            val firstViewModel = QuickNoteEditorViewModel(
                handle,
                repository,
                autosaveDebounceMillis = 100L
            )

            firstViewModel.updateTitle("Idea")
            advanceTimeBy(100L)
            runCurrent()
            val savedId = firstViewModel.uiState.value.id
            assertTrue(savedId > 0)
            val savedTimestamp = repository.rows.getValue(savedId).updatedAt
            val restoredHandle = SavedStateHandle(
                handle.keys().associateWith { key -> handle.get<Any?>(key) }
            )

            QuickNoteEditorViewModel(
                restoredHandle,
                repository,
                autosaveDebounceMillis = 100L
            )
            advanceTimeBy(100L)
            runCurrent()

            assertEquals(1, repository.rows.size)
            assertEquals(1, repository.saveCalls)
            assertEquals(savedId, repository.rows.values.single().id)
            assertEquals(savedTimestamp, repository.rows.values.single().updatedAt)
        }

    @Test
    fun `back performs final save before debounce and finishes`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeEditorRepository()
            val handle = SavedStateHandle()
            val viewModel = QuickNoteEditorViewModel(
                handle,
                repository,
                autosaveDebounceMillis = 10_000L
            )
            viewModel.updateContent("Guardar al salir")
            viewModel.requestBack()
            advanceUntilIdle()

            assertEquals(1, repository.saveCalls)
            assertEquals("Guardar al salir", repository.rows.values.single().content)
            assertTrue(viewModel.finishRequested.value)
            val restoredHandle = SavedStateHandle(
                handle.keys().associateWith { key -> handle.get<Any?>(key) }
            )
            val recreated = QuickNoteEditorViewModel(
                restoredHandle,
                repository,
                autosaveDebounceMillis = 10_000L
            )
            assertTrue(recreated.finishRequested.value)
        }

    @Test
    fun `final save persists an edit made while an earlier save is suspended`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeEditorRepository()
            val firstSaveGate = CompletableDeferred<Unit>()
            repository.nextSaveGate = firstSaveGate
            val viewModel = QuickNoteEditorViewModel(
                SavedStateHandle(),
                repository,
                autosaveDebounceMillis = 100L
            )

            viewModel.updateContent("Versión inicial")
            advanceTimeBy(100L)
            runCurrent()
            assertEquals(1, repository.saveCalls)

            viewModel.updateContent("Última versión")
            viewModel.requestBack()
            runCurrent()
            firstSaveGate.complete(Unit)
            advanceUntilIdle()

            assertEquals(2, repository.saveCalls)
            assertEquals(1, repository.rows.size)
            assertEquals("Última versión", repository.rows.values.single().content)
            assertTrue(viewModel.finishRequested.value)
        }

    @Test
    fun `empty draft never creates a row and explicit done reports validation`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeEditorRepository()
            val viewModel = QuickNoteEditorViewModel(
                SavedStateHandle(),
                repository,
                autosaveDebounceMillis = 100L
            )
            val event = async { viewModel.events.first() }

            viewModel.updateTitle("   ")
            viewModel.updateContent("\n\t")
            advanceTimeBy(100L)
            runCurrent()
            viewModel.requestDone()

            assertTrue(repository.rows.isEmpty())
            assertEquals(QuickNoteEditorEvent.ShowValidation, event.await())
        }

    @Test
    fun `autosave failure exposes error and does not finish`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeEditorRepository(failSaves = true)
            val viewModel = QuickNoteEditorViewModel(
                SavedStateHandle(),
                repository,
                autosaveDebounceMillis = 100L
            )

            viewModel.updateContent("No se guardará")
            advanceTimeBy(100L)
            runCurrent()

            assertEquals(QuickNoteSaveState.ERROR, viewModel.uiState.value.saveState)
            assertTrue(repository.rows.isEmpty())
        }

    private class FakeEditorRepository(
        private val failSaves: Boolean = false
    ) : QuickNoteRepository {
        val rows = linkedMapOf<Int, QuickNote>()
        var saveCalls = 0
        var nextSaveGate: CompletableDeferred<Unit>? = null
        private var nextId = 1

        override fun observeNotes(filter: QuickNoteFilter, query: String): Flow<List<QuickNote>> =
            MutableStateFlow(rows.values.toList())

        override suspend fun getNote(noteId: Int): QuickNote? = rows[noteId]

        override suspend fun saveNote(draft: QuickNoteDraft): QuickNote? {
            saveCalls += 1
            nextSaveGate?.also {
                nextSaveGate = null
                it.await()
            }
            if (failSaves) return null
            val normalized = QuickNoteValidator.normalizeOrNull(draft) ?: return null
            val id = normalized.id.takeIf { it > 0 } ?: nextId++
            val existing = rows[id]
            return QuickNote(
                id = id,
                title = normalized.title,
                content = normalized.content,
                isPinned = normalized.isPinned,
                colorTag = normalized.colorTag,
                createdAt = existing?.createdAt ?: 1L,
                updatedAt = (existing?.updatedAt ?: 0L) + 1L,
                isArchived = normalized.isArchived
            ).also { rows[id] = it }
        }

        override suspend fun setPinned(noteId: Int, pinned: Boolean): Boolean = false
        override suspend fun setArchived(noteId: Int, archived: Boolean): Boolean = false

        override suspend fun deleteNote(noteId: Int): QuickNote? = rows.remove(noteId)

        override suspend fun restoreDeletedNote(note: QuickNote): Boolean {
            rows[note.id] = note
            return true
        }
    }
}
