package com.luistureo.voicereminderapp.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luistureo.voicereminderapp.core.calendar.ChileanHoliday
import com.luistureo.voicereminderapp.core.calendar.ChileanHolidayProvider
import com.luistureo.voicereminderapp.core.reminder.ReminderOccurrenceCalculatorCore
import com.luistureo.voicereminderapp.core.utils.ReminderDisplayFormatter
import com.luistureo.voicereminderapp.core.utils.DateTimeFormatter as ReminderDateTimeFormatter
import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.model.ReminderType
import com.luistureo.voicereminderapp.domain.usecase.GetRemindersUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter as JavaDateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class CalendarViewModel(
    private val getRemindersUseCase: GetRemindersUseCase,
    private val holidayProvider: ChileanHolidayProvider
) : ViewModel() {

    private data class ReminderEntry(
        val reminder: Reminder,
        val date: LocalDate,
        val time: LocalTime?
    )

    private val locale = Locale.forLanguageTag("es-CL")
    private val today = LocalDate.now()
    private val storedTimeFormatter = JavaDateTimeFormatter.ofPattern("HH:mm")
    private val monthTitleFormatter = JavaDateTimeFormatter.ofPattern("LLLL yyyy", locale)
    private val selectedDateFormatter = JavaDateTimeFormatter.ofPattern("EEEE d 'de' MMMM", locale)
    private val timeZoneId = ZoneId.systemDefault().id
    private val monthOptions = Month.entries.map { month ->
        month.getDisplayName(TextStyle.FULL_STANDALONE, locale).toUiTitleCase()
    }

    private var visibleMonth: YearMonth = YearMonth.now()
    private var selectedDate: LocalDate = today
    private var reminders: List<Reminder> = emptyList()

    private val _uiState = MutableStateFlow(
        CalendarUiState(
            isLoading = true,
            selectedMonthIndex = visibleMonth.monthValue - 1,
            selectedYear = visibleMonth.year,
            monthOptions = monthOptions,
            yearOptions = buildYearOptions(visibleMonth.year)
        )
    )
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        reloadCalendar()
    }

    fun reloadCalendar() {
        _uiState.update { currentState ->
            currentState.copy(isLoading = true, errorMessage = null)
        }

        viewModelScope.launch {
            try {
                reminders = getRemindersUseCase()
                    .sortedWith(
                        compareBy<Reminder> { it.scheduleState.nextTriggerAtEpochMillis ?: it.scheduledAtEpochMillis }
                            .thenByDescending { it.isUrgent }
                            .thenBy { it.title }
                    )

                publishState()
            } catch (exception: Exception) {
                _uiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        errorMessage = exception.message ?: "No fue posible cargar el calendario."
                    )
                }
            }
        }
    }

    fun goToPreviousMonth() {
        visibleMonth = visibleMonth.minusMonths(1)
        selectedDate = alignSelectedDate(selectedDate, visibleMonth)
        publishState()
    }

    fun goToNextMonth() {
        visibleMonth = visibleMonth.plusMonths(1)
        selectedDate = alignSelectedDate(selectedDate, visibleMonth)
        publishState()
    }

    fun onMonthSelected(monthIndex: Int) {
        visibleMonth = YearMonth.of(visibleMonth.year, monthIndex + 1)
        selectedDate = alignSelectedDate(selectedDate, visibleMonth)
        publishState()
    }

    fun onYearSelected(year: Int) {
        visibleMonth = YearMonth.of(year, visibleMonth.monthValue)
        selectedDate = alignSelectedDate(selectedDate, visibleMonth)
        publishState()
    }

    fun onDaySelected(date: LocalDate) {
        selectedDate = date
        visibleMonth = YearMonth.from(date)
        publishState()
    }

    private fun publishState() {
        val reminderEntries = buildReminderEntriesForMonth(reminders, visibleMonth)
        val remindersByDate = reminderEntries.groupBy { it.date }
        val holidaysByDate = holidayProvider.getHolidaysForMonth(visibleMonth)
        val selectedDateReminders = remindersByDate[selectedDate].orEmpty()
        val selectedDateHolidays = holidayProvider.getHolidays(selectedDate)

        _uiState.update { currentState ->
            currentState.copy(
                isLoading = false,
                errorMessage = null,
                monthTitle = visibleMonth.format(monthTitleFormatter).toUiTitleCase(),
                selectedMonthIndex = visibleMonth.monthValue - 1,
                selectedYear = visibleMonth.year,
                yearOptions = buildYearOptions(visibleMonth.year),
                days = buildCalendarDays(remindersByDate, holidaysByDate),
                selectedDateTitle = selectedDate.format(selectedDateFormatter).toUiTitleCase(),
                selectedDateSummary = buildSelectedDateSummary(
                    reminderCount = selectedDateReminders.size,
                    holidayCount = selectedDateHolidays.size
                ),
                selectedHolidays = selectedDateHolidays.map { holiday -> holiday.name },
                selectedDateReminders = selectedDateReminders.map { entry ->
                    CalendarReminderDetailUiModel(
                        id = entry.reminder.id,
                        title = entry.reminder.title,
                        detail = entry.reminder.detail,
                        time = entry.time?.format(storedTimeFormatter)
                            ?: ReminderDisplayFormatter.formatScheduledTime(entry.reminder),
                        type = entry.reminder.type,
                        isCompleted = entry.reminder.isCompleted
                    )
                },
                emptyStateMessage = if (selectedDateReminders.isEmpty()) {
                    if (selectedDateHolidays.isEmpty()) {
                        "No hay recordatorios para este dia."
                    } else {
                        "No hay recordatorios, pero el feriado sigue visible para esta fecha."
                    }
                } else {
                    ""
                }
            )
        }
    }

    private fun buildReminderEntriesForMonth(
        reminders: List<Reminder>,
        month: YearMonth
    ): List<ReminderEntry> {
        val startDate = month.atDay(1)
        val endDate = month.atEndOfMonth()

        return reminders.flatMap { reminder ->
            buildReminderEntries(reminder, startDate, endDate)
        }.sortedWith(
            compareBy<ReminderEntry> { it.date }
                .thenBy { it.time ?: LocalTime.MAX }
                .thenBy { it.reminder.title }
        )
    }

    private fun buildReminderEntries(
        reminder: Reminder,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<ReminderEntry> {
        val reminderTime = ReminderDateTimeFormatter.toLocalTime(reminder.scheduledAtEpochMillis)
        val entries = mutableListOf<ReminderEntry>()
        var currentDate = startDate

        while (!currentDate.isAfter(endDate)) {
            if (
                ReminderOccurrenceCalculatorCore.occursOnDate(
                    reminder = reminder,
                    year = currentDate.year,
                    monthNumber = currentDate.monthValue,
                    dayOfMonth = currentDate.dayOfMonth,
                    timeZoneId = timeZoneId
                )
            ) {
                entries += ReminderEntry(
                    reminder = reminder,
                    date = currentDate,
                    time = reminderTime
                )
            }
            currentDate = currentDate.plusDays(1)
        }

        return entries
    }

    private fun buildCalendarDays(
        remindersByDate: Map<LocalDate, List<ReminderEntry>>,
        holidaysByDate: Map<LocalDate, List<ChileanHoliday>>
    ): List<CalendarDayUiModel> {
        val firstDayOfMonth = visibleMonth.atDay(1)
        val firstDayOffset = (firstDayOfMonth.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
        val calendarStart = firstDayOfMonth.minusDays(firstDayOffset.toLong())
        val totalDaySlots = firstDayOffset + visibleMonth.lengthOfMonth()
        val totalCells = ((totalDaySlots + 6) / 7) * 7

        return (0 until totalCells).map { index ->
            val currentDate = calendarStart.plusDays(index.toLong())
            val reminders = remindersByDate[currentDate].orEmpty().map { it.reminder }
            val holidays = holidaysByDate[currentDate].orEmpty()

            CalendarDayUiModel(
                date = currentDate,
                dayNumber = currentDate.dayOfMonth.toString(),
                isCurrentMonth = YearMonth.from(currentDate) == visibleMonth,
                isToday = currentDate == today,
                isSelected = currentDate == selectedDate,
                holidayLabel = buildHolidayLabel(holidays),
                indicators = buildIndicators(reminders, holidays),
                summaryCountLabel = reminders.size.takeIf { it > 1 }?.let { "+$it" }
            )
        }
    }

    private fun buildIndicators(
        reminders: List<Reminder>,
        holidays: List<ChileanHoliday>
    ): List<CalendarIndicatorUiModel> {
        val birthdayCount = reminders.count { it.type == ReminderType.BIRTHDAY }
        val activeReminderCount = reminders.count {
            it.type == ReminderType.DEFAULT && !it.isCompleted
        }
        val completedCount = reminders.count { it.isCompleted }
        val indicators = mutableListOf<CalendarIndicatorUiModel>()

        if (holidays.isNotEmpty()) {
            indicators += CalendarIndicatorUiModel(
                label = if (holidays.size == 1) "Feriado" else holidays.size.toString(),
                style = CalendarIndicatorStyle.HOLIDAY
            )
        }

        if (birthdayCount > 0) {
            indicators += CalendarIndicatorUiModel(
                label = birthdayCount.toString(),
                style = CalendarIndicatorStyle.BIRTHDAY
            )
        }

        if (activeReminderCount > 0) {
            indicators += CalendarIndicatorUiModel(
                label = activeReminderCount.toString(),
                style = CalendarIndicatorStyle.REMINDER
            )
        }

        if (completedCount > 0) {
            indicators += CalendarIndicatorUiModel(
                label = completedCount.toString(),
                style = CalendarIndicatorStyle.COMPLETED
            )
        }

        return indicators.take(3)
    }

    private fun buildHolidayLabel(holidays: List<ChileanHoliday>): String? {
        return when {
            holidays.isEmpty() -> null
            holidays.size == 1 -> holidays.first().name
            else -> "${holidays.first().name} +${holidays.size - 1}"
        }
    }

    private fun buildSelectedDateSummary(
        reminderCount: Int,
        holidayCount: Int
    ): String {
        return when {
            reminderCount > 0 && holidayCount > 0 ->
                "$reminderCount ${pluralize(reminderCount, "recordatorio")} - $holidayCount ${pluralize(holidayCount, "feriado")}"

            reminderCount > 0 ->
                "$reminderCount ${pluralize(reminderCount, "recordatorio")}"

            holidayCount > 0 ->
                "$holidayCount ${pluralize(holidayCount, "feriado")}"

            else -> "Sin actividad para la fecha seleccionada"
        }
    }

    private fun pluralize(value: Int, singular: String): String {
        return if (value == 1) singular else "${singular}s"
    }

    private fun buildYearOptions(focusedYear: Int): List<Int> {
        val startYear = min(today.year - 10, focusedYear - 5)
        val endYear = max(today.year + 15, focusedYear + 5)
        return (startYear..endYear).toList()
    }

    private fun alignSelectedDate(date: LocalDate, targetMonth: YearMonth): LocalDate {
        val resolvedDay = min(date.dayOfMonth, targetMonth.lengthOfMonth())
        return targetMonth.atDay(resolvedDay)
    }

    private fun String.toUiTitleCase(): String {
        return replaceFirstChar { currentChar ->
            if (currentChar.isLowerCase()) {
                currentChar.titlecase(locale)
            } else {
                currentChar.toString()
            }
        }
    }
}
