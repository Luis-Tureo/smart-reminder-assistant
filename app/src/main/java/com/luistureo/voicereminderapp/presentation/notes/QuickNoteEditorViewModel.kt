package com.luistureo.voicereminderapp.presentation.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luistureo.voicereminderapp.domain.notes.model.QuickNote
import com.luistureo.voicereminderapp.domain.notes.model.QuickNoteColorTag
import com.luistureo.voicereminderapp.domain.notes.model.QuickNoteDraft
import com.luistureo.voicereminderapp.domain.notes.repository.QuickNoteRepository
import com.luistureo.voicereminderapp.domain.notes.validation.QuickNoteValidator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class QuickNoteEditorViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val repository: QuickNoteRepository,
    private val autosaveDebounceMillis: Long = AUTOSAVE_DEBOUNCE_MILLIS
) : ViewModel() {
    private val saveMutex = Mutex()
    private val changes = Channel<Unit>(Channel.CONFLATED)
    private val eventsChannel = Channel<QuickNoteEditorEvent>(Channel.BUFFERED)
    private val stateFlow = MutableStateFlow(restoredState())
    private val finishRequestedFlow = MutableStateFlow(
        savedStateHandle[KEY_FINISH_REQUESTED] ?: false
    )
    private var lastPersistedDraft: QuickNoteDraft? = restoredPersistedDraft()
    private var isClosing = finishRequestedFlow.value

    val uiState: StateFlow<QuickNoteEditorUiState> = stateFlow.asStateFlow()
    val events: Flow<QuickNoteEditorEvent> = eventsChannel.receiveAsFlow()
    val finishRequested: StateFlow<Boolean> = finishRequestedFlow.asStateFlow()

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
        if (isClosing) {
            changes.close()
            autosaveWorker.cancel()
        } else if (savedStateHandle.get<Boolean>(KEY_DRAFT_INITIALIZED) == true) {
            stateFlow.update { it.copy(isLoaded = true) }
            val restoredDraft = QuickNoteValidator.normalizeOrNull(stateFlow.value.toDraft())
            val persistedDraft = lastPersistedDraft
            if (
                restoredDraft != null &&
                (persistedDraft == null || !equivalent(restoredDraft, persistedDraft))
            ) {
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
            eventsChannel.trySend(QuickNoteEditorEvent.ShowValidation)
            return
        }
        eventsChannel.trySend(
            QuickNoteEditorEvent.Share(
                title = normalized.title,
                content = normalized.content
            )
        )
    }

    fun setArchivedAndFinish(archived: Boolean) {
        if (QuickNoteValidator.normalizeOrNull(stateFlow.value.toDraft()) == null) {
            eventsChannel.trySend(QuickNoteEditorEvent.ShowValidation)
            return
        }

        updateDraft { copy(isArchived = archived) }
        viewModelScope.launch {
            persistUntilCleanAndFinish()
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
                requestFinish()
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
                persistPersistedDraft(loaded.toDraft())
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
                eventsChannel.trySend(QuickNoteEditorEvent.ShowValidation)
            }
            return
        }

        viewModelScope.launch {
            persistUntilCleanAndFinish()
        }
    }

    private suspend fun persistLatest(): Boolean = saveMutex.withLock {
        if (isClosing) return@withLock false
        when (persistLatestLocked()) {
            PersistResult.CLEAN -> true
            PersistResult.DIRTY -> {
                changes.trySend(Unit)
                true
            }
            PersistResult.INVALID,
            PersistResult.ERROR -> false
        }
    }

    private suspend fun persistLatestLocked(): PersistResult {
        val normalized = QuickNoteValidator.normalizeOrNull(stateFlow.value.toDraft())
            ?: return PersistResult.INVALID

        val previous = lastPersistedDraft
        if (previous != null && equivalent(normalized, previous)) {
            stateFlow.update { it.copy(saveState = QuickNoteSaveState.SAVED) }
            return PersistResult.CLEAN
        }

        stateFlow.update { it.copy(saveState = QuickNoteSaveState.SAVING) }
        val saved = runCatching { repository.saveNote(normalized) }.getOrNull()
        if (saved == null) {
            stateFlow.update { it.copy(saveState = QuickNoteSaveState.ERROR) }
            return PersistResult.ERROR
        }

        val persisted = saved.toDraft()
        lastPersistedDraft = persisted
        persistPersistedDraft(persisted)
        savedStateHandle[QuickNoteEditorActivity.EXTRA_NOTE_ID] = saved.id
        stateFlow.update { current ->
            val currentWithId = current.copy(id = saved.id)
            val latestNormalized = QuickNoteValidator.normalizeOrNull(currentWithId.toDraft())
            currentWithId.copy(
                saveState = if (
                    latestNormalized != null && equivalent(latestNormalized, persisted)
                ) {
                    QuickNoteSaveState.SAVED
                } else {
                    QuickNoteSaveState.IDLE
                }
            )
        }
        persistStateHandle(stateFlow.value)

        return if (stateFlow.value.saveState == QuickNoteSaveState.SAVED) {
            PersistResult.CLEAN
        } else {
            PersistResult.DIRTY
        }
    }

    private suspend fun persistUntilCleanAndFinish() {
        var shouldShowValidation = false
        saveMutex.withLock {
            if (isClosing) return@withLock

            var result: PersistResult
            do {
                result = persistLatestLocked()
            } while (result == PersistResult.DIRTY && !isClosing)

            when (result) {
                PersistResult.CLEAN -> prepareToClose()
                PersistResult.INVALID -> shouldShowValidation = true
                PersistResult.DIRTY,
                PersistResult.ERROR -> Unit
            }
        }

        if (shouldShowValidation) eventsChannel.send(QuickNoteEditorEvent.ShowValidation)
    }

    private fun closeAndFinish() {
        prepareToClose()
    }

    private fun prepareToClose(): Boolean {
        if (isClosing) return false
        isClosing = true
        changes.close()
        autosaveWorker.cancel()
        requestFinish()
        return true
    }

    private fun requestFinish() {
        savedStateHandle[KEY_FINISH_REQUESTED] = true
        finishRequestedFlow.value = true
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

    private fun persistPersistedDraft(draft: QuickNoteDraft) {
        savedStateHandle[KEY_PERSISTED_ID] = draft.id
        savedStateHandle[KEY_PERSISTED_TITLE] = draft.title
        savedStateHandle[KEY_PERSISTED_CONTENT] = draft.content
        savedStateHandle[KEY_PERSISTED_PINNED] = draft.isPinned
        savedStateHandle[KEY_PERSISTED_COLOR] = draft.colorTag?.name
        savedStateHandle[KEY_PERSISTED_ARCHIVED] = draft.isArchived
    }

    private fun restoredPersistedDraft(): QuickNoteDraft? {
        val id = savedStateHandle.get<Int>(KEY_PERSISTED_ID)?.takeIf { it > 0 } ?: return null
        return QuickNoteDraft(
            id = id,
            title = savedStateHandle[KEY_PERSISTED_TITLE],
            content = savedStateHandle[KEY_PERSISTED_CONTENT] ?: "",
            isPinned = savedStateHandle[KEY_PERSISTED_PINNED] ?: false,
            colorTag = savedStateHandle.get<String>(KEY_PERSISTED_COLOR)?.let { stored ->
                runCatching { QuickNoteColorTag.valueOf(stored) }.getOrNull()
            },
            isArchived = savedStateHandle[KEY_PERSISTED_ARCHIVED] ?: false
        )
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
        private const val KEY_PERSISTED_ID = "quick_note_persisted_id"
        private const val KEY_PERSISTED_TITLE = "quick_note_persisted_title"
        private const val KEY_PERSISTED_CONTENT = "quick_note_persisted_content"
        private const val KEY_PERSISTED_PINNED = "quick_note_persisted_pinned"
        private const val KEY_PERSISTED_COLOR = "quick_note_persisted_color"
        private const val KEY_PERSISTED_ARCHIVED = "quick_note_persisted_archived"
        private const val KEY_FINISH_REQUESTED = "quick_note_finish_requested"
    }

    private enum class PersistResult {
        CLEAN,
        DIRTY,
        INVALID,
        ERROR
    }
}
