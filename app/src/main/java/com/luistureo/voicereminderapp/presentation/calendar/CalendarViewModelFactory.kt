package com.luistureo.voicereminderapp.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.luistureo.voicereminderapp.core.alarm.ReminderScheduler
import com.luistureo.voicereminderapp.core.calendar.ChileanHolidayProvider
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarAuthManager
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarReminderSynchronizer
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarRestClient
import com.luistureo.voicereminderapp.domain.usecase.DeleteReminderUseCase
import com.luistureo.voicereminderapp.domain.usecase.GetRemindersUseCase

class CalendarViewModelFactory(
    private val getRemindersUseCase: GetRemindersUseCase,
    private val deleteReminderUseCase: DeleteReminderUseCase,
    private val googleCalendarAuthManager: GoogleCalendarAuthManager,
    private val googleCalendarSynchronizer: GoogleCalendarReminderSynchronizer,
    private val reminderScheduler: ReminderScheduler,
    private val googleCalendarRestClient: GoogleCalendarRestClient = GoogleCalendarRestClient(),
    private val holidayProvider: ChileanHolidayProvider = ChileanHolidayProvider()
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CalendarViewModel(
                getRemindersUseCase = getRemindersUseCase,
                deleteReminderUseCase = deleteReminderUseCase,
                googleCalendarAuthManager = googleCalendarAuthManager,
                googleCalendarRestClient = googleCalendarRestClient,
                googleCalendarSynchronizer = googleCalendarSynchronizer,
                reminderScheduler = reminderScheduler,
                holidayProvider = holidayProvider
            ) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
