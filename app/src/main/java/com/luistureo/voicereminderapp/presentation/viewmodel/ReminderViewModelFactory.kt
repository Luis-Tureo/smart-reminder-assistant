package com.luistureo.voicereminderapp.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarReminderSynchronizer
import com.luistureo.voicereminderapp.domain.usecase.DeleteReminderUseCase
import com.luistureo.voicereminderapp.domain.usecase.GetReminderByIdUseCase
import com.luistureo.voicereminderapp.domain.usecase.GetRemindersUseCase
import com.luistureo.voicereminderapp.domain.usecase.SaveReminderDraftUseCase
import com.luistureo.voicereminderapp.domain.usecase.UpdateReminderUseCase

class ReminderViewModelFactory(
    private val context: Context,
    private val saveReminderDraftUseCase: SaveReminderDraftUseCase,
    private val getRemindersUseCase: GetRemindersUseCase,
    private val getReminderByIdUseCase: GetReminderByIdUseCase,
    private val deleteReminderUseCase: DeleteReminderUseCase,
    private val updateReminderUseCase: UpdateReminderUseCase,
    private val googleCalendarSynchronizer: GoogleCalendarReminderSynchronizer
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReminderViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ReminderViewModel(
                context = context.applicationContext,
                saveReminderDraftUseCase = saveReminderDraftUseCase,
                getRemindersUseCase = getRemindersUseCase,
                getReminderByIdUseCase = getReminderByIdUseCase,
                deleteReminderUseCase = deleteReminderUseCase,
                updateReminderUseCase = updateReminderUseCase,
                googleCalendarSynchronizer = googleCalendarSynchronizer
            ) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
