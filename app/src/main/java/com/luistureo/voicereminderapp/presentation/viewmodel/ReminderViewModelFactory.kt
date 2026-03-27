package com.luistureo.voicereminderapp.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.luistureo.voicereminderapp.domain.usecase.AddReminderUseCase
import com.luistureo.voicereminderapp.domain.usecase.DeleteReminderUseCase
import com.luistureo.voicereminderapp.domain.usecase.GetRemindersUseCase
import com.luistureo.voicereminderapp.domain.usecase.UpdateReminderUseCase

class ReminderViewModelFactory(
    private val addReminderUseCase: AddReminderUseCase,
    private val getRemindersUseCase: GetRemindersUseCase,
    private val deleteReminderUseCase: DeleteReminderUseCase,
    private val updateReminderUseCase: UpdateReminderUseCase
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReminderViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ReminderViewModel(
                addReminderUseCase = addReminderUseCase,
                getRemindersUseCase = getRemindersUseCase,
                deleteReminderUseCase = deleteReminderUseCase,
                updateReminderUseCase = updateReminderUseCase
            ) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}