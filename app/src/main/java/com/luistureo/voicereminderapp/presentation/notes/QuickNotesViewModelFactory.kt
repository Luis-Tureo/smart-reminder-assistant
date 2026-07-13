package com.luistureo.voicereminderapp.presentation.notes

import android.content.Context
import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.savedstate.SavedStateRegistryOwner
import com.luistureo.voicereminderapp.data.repository.notes.QuickNoteRepositoryProvider
import com.luistureo.voicereminderapp.domain.notes.repository.QuickNoteRepository

class QuickNotesViewModelFactory(
    private val repository: QuickNoteRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(QuickNotesViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return QuickNotesViewModel(repository) as T
    }

    companion object {
        fun from(context: Context): QuickNotesViewModelFactory = QuickNotesViewModelFactory(
            QuickNoteRepositoryProvider.create(context.applicationContext)
        )
    }
}

class QuickNoteEditorViewModelFactory(
    owner: SavedStateRegistryOwner,
    defaultArgs: Bundle?,
    private val repository: QuickNoteRepository
) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {
    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle
    ): T {
        require(modelClass.isAssignableFrom(QuickNoteEditorViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return QuickNoteEditorViewModel(handle, repository) as T
    }

    companion object {
        fun from(
            context: Context,
            owner: SavedStateRegistryOwner,
            defaultArgs: Bundle?
        ): QuickNoteEditorViewModelFactory = QuickNoteEditorViewModelFactory(
            owner = owner,
            defaultArgs = defaultArgs,
            repository = QuickNoteRepositoryProvider.create(context.applicationContext)
        )
    }
}
