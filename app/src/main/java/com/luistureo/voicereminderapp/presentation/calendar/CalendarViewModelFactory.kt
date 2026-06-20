package com.luistureo.voicereminderapp.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.luistureo.voicereminderapp.core.alarm.ReminderScheduler
import com.luistureo.voicereminderapp.core.calendar.ChileanHolidayProvider
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarAuthManager
import com.luistureo.voicereminderapp.core.calendar.unified.UnifiedCalendarSynchronizer
import com.luistureo.voicereminderapp.domain.usecase.DeleteReminderUseCase
import com.luistureo.voicereminderapp.domain.usecase.GetRemindersUseCase
import com.luistureo.voicereminderapp.domain.usecase.UpdateReminderUseCase

class CalendarViewModelFactory(
    private val getRemindersUseCase: GetRemindersUseCase,
    private val deleteReminderUseCase: DeleteReminderUseCase,
    private val updateReminderUseCase: UpdateReminderUseCase,
    private val googleCalendarAuthManager: GoogleCalendarAuthManager,
    private val unifiedCalendarSynchronizer: UnifiedCalendarSynchronizer,
    private val reminderScheduler: ReminderScheduler,
    private val holidayProvider: ChileanHolidayProvider = ChileanHolidayProvider()
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CalendarViewModel(
                getRemindersUseCase = getRemindersUseCase,
                deleteReminderUseCase = deleteReminderUseCase,
                updateReminderUseCase = updateReminderUseCase,
                googleCalendarAuthManager = googleCalendarAuthManager,
                unifiedCalendarSynchronizer = unifiedCalendarSynchronizer,
                reminderScheduler = reminderScheduler,
                holidayProvider = holidayProvider
            ) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
