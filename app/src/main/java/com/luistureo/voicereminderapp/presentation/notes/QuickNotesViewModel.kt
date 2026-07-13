package com.luistureo.voicereminderapp.presentation.notes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luistureo.voicereminderapp.data.repository.notes.QuickNoteRepositoryProvider
import com.luistureo.voicereminderapp.domain.notes.model.QuickNote
import com.luistureo.voicereminderapp.domain.notes.model.QuickNoteFilter
import com.luistureo.voicereminderapp.domain.notes.repository.QuickNoteRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class QuickNotesViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: QuickNoteRepository = QuickNoteRepositoryProvider.create(application)
) : AndroidViewModel(application) {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(QuickNoteFilter.ALL)

    private val eventsFlow = MutableSharedFlow<QuickNotesEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<QuickNotesEvent> = eventsFlow.asSharedFlow()

    val uiState: StateFlow<QuickNotesUiState> = combine(query, filter) { text, selectedFilter ->
        text to selectedFilter
    }.flatMapLatest { (text, selectedFilter) ->
        repository.observeNotes(selectedFilter, text).map { notes ->
            QuickNotesUiState(
                notes = notes,
                query = text,
                filter = selectedFilter,
                isLoading = false
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = QuickNotesUiState()
    )

    fun setQuery(value: String) {
        query.value = value
    }

    fun setFilter(value: QuickNoteFilter) {
        filter.value = value
    }

    fun togglePinned(note: QuickNote) {
        viewModelScope.launch {
            runCatching { repository.setPinned(note.id, !note.isPinned) }
                .onSuccess { updated ->
                    if (!updated) eventsFlow.emit(QuickNotesEvent.UpdateFailed)
                }
                .onFailure { eventsFlow.emit(QuickNotesEvent.UpdateFailed) }
        }
    }

    fun toggleArchived(note: QuickNote) {
        viewModelScope.launch {
            runCatching { repository.setArchived(note.id, !note.isArchived) }
                .onSuccess { updated ->
                    if (!updated) eventsFlow.emit(QuickNotesEvent.UpdateFailed)
                }
                .onFailure { eventsFlow.emit(QuickNotesEvent.UpdateFailed) }
        }
    }

    fun delete(note: QuickNote) {
        viewModelScope.launch {
            runCatching { repository.deleteNote(note.id) }
                .onSuccess { deleted ->
                    if (deleted == null) eventsFlow.emit(QuickNotesEvent.DeleteFailed)
                    else eventsFlow.emit(QuickNotesEvent.NoteDeleted(deleted))
                }
                .onFailure { eventsFlow.emit(QuickNotesEvent.DeleteFailed) }
        }
    }

    fun restoreDeleted(note: QuickNote) {
        viewModelScope.launch {
            runCatching { repository.restoreDeletedNote(note) }
                .onSuccess { restored ->
                    if (!restored) eventsFlow.emit(QuickNotesEvent.RestoreFailed)
                }
                .onFailure { eventsFlow.emit(QuickNotesEvent.RestoreFailed) }
        }
    }
}
