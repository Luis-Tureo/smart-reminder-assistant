package com.luistureo.voicereminderapp.presentation.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luistureo.voicereminderapp.domain.notes.model.QuickNote
import com.luistureo.voicereminderapp.domain.notes.model.QuickNoteFilter
import com.luistureo.voicereminderapp.domain.notes.repository.QuickNoteRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class QuickNotesViewModel(
    private val repository: QuickNoteRepository
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(QuickNoteFilter.ALL)

    private val eventsChannel = Channel<QuickNotesEvent>(Channel.BUFFERED)
    private val pendingDeletedFlow = MutableStateFlow<QuickNote?>(null)
    val events: Flow<QuickNotesEvent> = eventsChannel.receiveAsFlow()
    val pendingDeleted: StateFlow<QuickNote?> = pendingDeletedFlow.asStateFlow()

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
                    if (!updated) eventsChannel.send(QuickNotesEvent.UpdateFailed)
                }
                .onFailure { eventsChannel.send(QuickNotesEvent.UpdateFailed) }
        }
    }

    fun toggleArchived(note: QuickNote) {
        viewModelScope.launch {
            runCatching { repository.setArchived(note.id, !note.isArchived) }
                .onSuccess { updated ->
                    if (!updated) eventsChannel.send(QuickNotesEvent.UpdateFailed)
                }
                .onFailure { eventsChannel.send(QuickNotesEvent.UpdateFailed) }
        }
    }

    fun delete(note: QuickNote) {
        viewModelScope.launch {
            runCatching { repository.deleteNote(note.id) }
                .onSuccess { deleted ->
                    if (deleted == null) {
                        eventsChannel.send(QuickNotesEvent.DeleteFailed)
                    } else {
                        pendingDeletedFlow.value = deleted
                    }
                }
                .onFailure { eventsChannel.send(QuickNotesEvent.DeleteFailed) }
        }
    }

    fun restoreDeleted(note: QuickNote) {
        dismissDeleteUndo(note.id)
        viewModelScope.launch {
            runCatching { repository.restoreDeletedNote(note) }
                .onSuccess { restored ->
                    if (!restored) {
                        pendingDeletedFlow.value = note
                        eventsChannel.send(QuickNotesEvent.RestoreFailed)
                    }
                }
                .onFailure {
                    pendingDeletedFlow.value = note
                    eventsChannel.send(QuickNotesEvent.RestoreFailed)
                }
        }
    }

    fun dismissDeleteUndo(noteId: Int) {
        if (pendingDeletedFlow.value?.id == noteId) pendingDeletedFlow.value = null
    }
}
