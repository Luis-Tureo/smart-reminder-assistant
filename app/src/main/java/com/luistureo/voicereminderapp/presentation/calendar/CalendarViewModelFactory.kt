package com.luistureo.voicereminderapp.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.luistureo.voicereminderapp.core.calendar.ChileanHolidayProvider
import com.luistureo.voicereminderapp.domain.usecase.GetRemindersUseCase

class CalendarViewModelFactory(
    private val getRemindersUseCase: GetRemindersUseCase,
    private val holidayProvider: ChileanHolidayProvider = ChileanHolidayProvider()
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CalendarViewModel(
                getRemindersUseCase = getRemindersUseCase,
                holidayProvider = holidayProvider
            ) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
