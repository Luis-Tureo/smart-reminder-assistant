package com.luistureo.voicereminderapp.presentation.calendar

import com.luistureo.voicereminderapp.domain.model.ReminderType
import java.time.LocalDate

data class CalendarUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val monthTitle: String = "",
    val selectedMonthIndex: Int = 0,
    val selectedYear: Int = 0,
    val monthOptions: List<String> = emptyList(),
    val yearOptions: List<Int> = emptyList(),
    val days: List<CalendarDayUiModel> = emptyList(),
    val selectedDateTitle: String = "",
    val selectedDateSummary: String = "",
    val selectedHolidays: List<String> = emptyList(),
    val selectedDateReminders: List<CalendarReminderDetailUiModel> = emptyList(),
    val filteredItems: List<CalendarFilteredListItemUiModel> = emptyList(),
    val emptyStateMessage: String = ""
)

data class CalendarDayUiModel(
    val date: LocalDate,
    val dayNumber: String,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
    val holidayLabel: String? = null,
    val indicators: List<CalendarIndicatorUiModel> = emptyList(),
    val summaryCountLabel: String? = null
)

data class CalendarIndicatorUiModel(
    val label: String,
    val style: CalendarIndicatorStyle
)

enum class CalendarIndicatorStyle {
    REMINDER,
    BIRTHDAY,
    HOLIDAY,
    COMPLETED
}

data class CalendarReminderDetailUiModel(
    val id: String,
    val title: String,
    val detail: String,
    val time: String,
    val type: ReminderType,
    val isCompleted: Boolean,
    val localReminderId: Int? = null,
    val googleCalendarEventId: String? = null,
    val canDelete: Boolean = true
)

data class CalendarFilteredListItemUiModel(
    val date: LocalDate,
    val dateTitle: String,
    val detail: CalendarReminderDetailUiModel? = null,
    val holidayName: String? = null,
    val style: CalendarIndicatorStyle
)
