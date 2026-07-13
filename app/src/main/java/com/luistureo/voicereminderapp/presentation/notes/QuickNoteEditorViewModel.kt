package com.luistureo.voicereminderapp.presentation.notes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.luistureo.voicereminderapp.data.repository.notes.QuickNoteRepositoryProvider
import com.luistureo.voicereminderapp.domain.notes.model.QuickNote
import com.luistureo.voicereminderapp.domain.notes.model.QuickNoteColorTag
import com.luistureo.voicereminderapp.domain.notes.model.QuickNoteDraft
import com.luistureo.voicereminderapp.domain.notes.repository.QuickNoteRepository
import com.luistureo.voicereminderapp.domain.notes.validation.QuickNoteValidator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class QuickNoteEditorViewModel @JvmOverloads constructor(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val repository: QuickNoteRepository = QuickNoteRepositoryProvider.create(application),
    private val autosaveDebounceMillis: Long = AUTOSAVE_DEBOUNCE_MILLIS
) : AndroidViewModel(application) {
    private val saveMutex = Mutex()
    private val changes = Channel<Unit>(Channel.CONFLATED)
    private val eventsFlow = MutableSharedFlow<QuickNoteEditorEvent>(extraBufferCapacity = 1)
    private val stateFlow = MutableStateFlow(restoredState())
    private var lastPersistedDraft: QuickNoteDraft? = null
    private var isClosing = false

    val uiState: StateFlow<QuickNoteEditorUiState> = stateFlow.asStateFlow()
    val events: SharedFlow<QuickNoteEditorEvent> = eventsFlow.asSharedFlow()

    private val autosaveWorker = viewModelScope.launch {
        for (ignored in changes) {
            var newerChange: Boolean
            do {
                delay(autosaveDebounceMillis)
                newerChange = changes.tryReceive().isSuccess
            } while (newerChange)

            if (!isClosing) persistLatest()
        }
    }

    init {
        if (savedStateHandle.get<Boolean>(KEY_DRAFT_INITIALIZED) == true) {
            stateFlow.update { it.copy(isLoaded = true) }
            if (QuickNoteValidator.isMeaningful(stateFlow.value.toDraft())) {
                changes.trySend(Unit)
            }
        } else {
            initializeDraft()
        }
    }

    fun updateTitle(value: String) = updateDraft { copy(title = value) }

    fun updateContent(value: String) = updateDraft { copy(content = value) }

    fun updatePinned(value: Boolean) = updateDraft { copy(isPinned = value) }

    fun updateColor(value: QuickNoteColorTag?) = updateDraft { copy(colorTag = value) }

    fun requestDone() {
        requestExit(allowDiscardEmptyNew = false)
    }

    fun requestBack() {
        requestExit(allowDiscardEmptyNew = true)
    }

    fun requestShare() {
        val normalized = QuickNoteValidator.normalizeOrNull(stateFlow.value.toDraft())
        if (normalized == null) {
            eventsFlow.tryEmit(QuickNoteEditorEvent.ShowValidation)
            return
        }
        eventsFlow.tryEmit(
            QuickNoteEditorEvent.Share(
                title = normalized.title,
                content = normalized.content
            )
        )
    }

    fun setArchivedAndFinish(archived: Boolean) {
        if (QuickNoteValidator.normalizeOrNull(stateFlow.value.toDraft()) == null) {
            eventsFlow.tryEmit(QuickNoteEditorEvent.ShowValidation)
            return
        }

        updateDraft { copy(isArchived = archived) }
        viewModelScope.launch {
            if (persistLatest()) closeAndFinish()
        }
    }

    fun deleteAndFinish() {
        if (isClosing) return
        isClosing = true
        viewModelScope.launch {
            val deleted = saveMutex.withLock {
                val noteId = stateFlow.value.id
                if (noteId <= 0) true else runCatching {
                    repository.deleteNote(noteId) != null
                }.getOrDefault(false)
            }
            if (deleted) {
                changes.close()
                autosaveWorker.cancel()
                eventsFlow.emit(QuickNoteEditorEvent.Finish)
            } else {
                isClosing = false
                stateFlow.update { it.copy(saveState = QuickNoteSaveState.ERROR) }
            }
        }
    }

    private fun initializeDraft() {
        val noteId = stateFlow.value.id
        if (noteId <= 0) {
            markInitialized(stateFlow.value.copy(isLoaded = true))
            return
        }

        viewModelScope.launch {
            val note = runCatching { repository.getNote(noteId) }.getOrNull()
            if (note == null) {
                markInitialized(
                    QuickNoteEditorUiState(
                        id = 0,
                        isLoaded = true,
                        saveState = QuickNoteSaveState.ERROR
                    )
                )
            } else {
                val loaded = note.toEditorState()
                lastPersistedDraft = loaded.toDraft()
                markInitialized(loaded)
            }
        }
    }

    private fun updateDraft(transform: QuickNoteEditorUiState.() -> QuickNoteEditorUiState) {
        if (!stateFlow.value.isLoaded || isClosing) return
        stateFlow.update { current ->
            current.transform().copy(saveState = QuickNoteSaveState.IDLE)
        }
        persistStateHandle(stateFlow.value)
        changes.trySend(Unit)
    }

    private fun requestExit(allowDiscardEmptyNew: Boolean) {
        if (isClosing) return
        val state = stateFlow.value
        val normalized = QuickNoteValidator.normalizeOrNull(state.toDraft())
        if (normalized == null) {
            if (allowDiscardEmptyNew && state.id <= 0) {
                closeAndFinish()
            } else {
                eventsFlow.tryEmit(QuickNoteEditorEvent.ShowValidation)
            }
            return
        }

        viewModelScope.launch {
            if (persistLatest()) closeAndFinish()
        }
    }

    private suspend fun persistLatest(): Boolean = saveMutex.withLock {
        if (isClosing) return@withLock false
        val normalized = QuickNoteValidator.normalizeOrNull(stateFlow.value.toDraft())
            ?: return@withLock false

        val previous = lastPersistedDraft
        if (previous != null && equivalent(normalized, previous)) {
            stateFlow.update { it.copy(saveState = QuickNoteSaveState.SAVED) }
            return@withLock true
        }

        stateFlow.update { it.copy(saveState = QuickNoteSaveState.SAVING) }
        val saved = runCatching { repository.saveNote(normalized) }.getOrNull()
        if (saved == null) {
            stateFlow.update { it.copy(saveState = QuickNoteSaveState.ERROR) }
            return@withLock false
        }

        val persisted = saved.toDraft()
        lastPersistedDraft = persisted
        savedStateHandle[QuickNoteEditorActivity.EXTRA_NOTE_ID] = saved.id
        stateFlow.update { current ->
            val currentWithId = current.copy(id = saved.id)
            currentWithId.copy(
                saveState = if (equivalent(currentWithId.toDraft(), persisted)) {
                    QuickNoteSaveState.SAVED
                } else {
                    QuickNoteSaveState.IDLE
                }
            )
        }
        persistStateHandle(stateFlow.value)

        if (stateFlow.value.saveState == QuickNoteSaveState.IDLE) changes.trySend(Unit)
        true
    }

    private fun closeAndFinish() {
        if (isClosing) return
        isClosing = true
        changes.close()
        autosaveWorker.cancel()
        eventsFlow.tryEmit(QuickNoteEditorEvent.Finish)
    }

    private fun markInitialized(state: QuickNoteEditorUiState) {
        stateFlow.value = state.copy(isLoaded = true)
        savedStateHandle[KEY_DRAFT_INITIALIZED] = true
        persistStateHandle(stateFlow.value)
    }

    private fun persistStateHandle(state: QuickNoteEditorUiState) {
        savedStateHandle[QuickNoteEditorActivity.EXTRA_NOTE_ID] = state.id
        savedStateHandle[KEY_TITLE] = state.title
        savedStateHandle[KEY_CONTENT] = state.content
        savedStateHandle[KEY_PINNED] = state.isPinned
        savedStateHandle[KEY_COLOR] = state.colorTag?.name
        savedStateHandle[KEY_ARCHIVED] = state.isArchived
    }

    private fun restoredState(): QuickNoteEditorUiState = QuickNoteEditorUiState(
        id = savedStateHandle[QuickNoteEditorActivity.EXTRA_NOTE_ID] ?: 0,
        title = savedStateHandle[KEY_TITLE] ?: "",
        content = savedStateHandle[KEY_CONTENT] ?: "",
        isPinned = savedStateHandle[KEY_PINNED] ?: false,
        colorTag = savedStateHandle.get<String>(KEY_COLOR)?.let { stored ->
            runCatching { QuickNoteColorTag.valueOf(stored) }.getOrNull()
        },
        isArchived = savedStateHandle[KEY_ARCHIVED] ?: false,
        isLoaded = savedStateHandle[KEY_DRAFT_INITIALIZED] ?: false
    )

    private fun QuickNote.toEditorState(): QuickNoteEditorUiState = QuickNoteEditorUiState(
        id = id,
        title = title.orEmpty(),
        content = content,
        isPinned = isPinned,
        colorTag = colorTag,
        isArchived = isArchived,
        isLoaded = true,
        saveState = QuickNoteSaveState.SAVED
    )

    private fun QuickNote.toDraft(): QuickNoteDraft = QuickNoteDraft(
        id = id,
        title = title,
        content = content,
        isPinned = isPinned,
        colorTag = colorTag,
        isArchived = isArchived
    )

    private fun equivalent(first: QuickNoteDraft, second: QuickNoteDraft): Boolean =
        first.id == second.id &&
            first.title == second.title &&
            first.content == second.content &&
            first.isPinned == second.isPinned &&
            first.colorTag == second.colorTag &&
            first.isArchived == second.isArchived

    companion object {
        private const val AUTOSAVE_DEBOUNCE_MILLIS = 650L
        private const val KEY_DRAFT_INITIALIZED = "quick_note_draft_initialized"
        private const val KEY_TITLE = "quick_note_title"
        private const val KEY_CONTENT = "quick_note_content"
        private const val KEY_PINNED = "quick_note_pinned"
        private const val KEY_COLOR = "quick_note_color"
        private const val KEY_ARCHIVED = "quick_note_archived"
    }
}
