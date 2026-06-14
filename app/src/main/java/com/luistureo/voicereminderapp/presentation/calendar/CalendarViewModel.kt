package com.luistureo.voicereminderapp.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luistureo.voicereminderapp.core.alarm.ReminderScheduler
import com.luistureo.voicereminderapp.core.calendar.ChileanHoliday
import com.luistureo.voicereminderapp.core.calendar.ChileanHolidayProvider
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarAuthException
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarAuthManager
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarEvent
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarReminderSynchronizer
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarRestClient
import com.luistureo.voicereminderapp.core.reminder.ReminderOccurrenceCalculator
import com.luistureo.voicereminderapp.core.utils.DateTimeFormatter as ReminderDateTimeFormatter
import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.model.ReminderType
import com.luistureo.voicereminderapp.domain.usecase.DeleteReminderUseCase
import com.luistureo.voicereminderapp.domain.usecase.GetRemindersUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Normalizer
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
    private val deleteReminderUseCase: DeleteReminderUseCase,
    private val googleCalendarAuthManager: GoogleCalendarAuthManager,
    private val googleCalendarRestClient: GoogleCalendarRestClient,
    private val googleCalendarSynchronizer: GoogleCalendarReminderSynchronizer,
    private val reminderScheduler: ReminderScheduler,
    private val holidayProvider: ChileanHolidayProvider
) : ViewModel() {

    private data class CalendarEntry(
        val id: String,
        val title: String,
        val detail: String,
        val date: LocalDate,
        val time: LocalTime?,
        val type: ReminderType,
        val isCompleted: Boolean,
        val localReminderId: Int? = null,
        val googleCalendarEventId: String? = null
    )

    private val locale = Locale.forLanguageTag("es-CL")
    private val today = LocalDate.now()
    private val zoneId = ZoneId.systemDefault()
    private val storedTimeFormatter = JavaDateTimeFormatter.ofPattern("HH:mm")
    private val monthTitleFormatter = JavaDateTimeFormatter.ofPattern("LLLL yyyy", locale)
    private val selectedDateFormatter = JavaDateTimeFormatter.ofPattern("EEEE d 'de' MMMM", locale)
    private val monthOptions = Month.entries.map { month ->
        month.getDisplayName(TextStyle.FULL_STANDALONE, locale).toUiTitleCase()
    }
    private val occurrenceCalculator = ReminderOccurrenceCalculator()

    private var visibleMonth: YearMonth = YearMonth.now()
    private var selectedDate: LocalDate = today
    private var calendarEntries: List<CalendarEntry> = emptyList()
    private var cachedReminders: List<Reminder> = emptyList()

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
            val cachedRemindersResult = runCatching { loadCachedReminders() }
            cachedReminders = cachedRemindersResult.getOrDefault(emptyList())

            runCatching {
                loadGoogleCalendarEntries()
            }.onSuccess { googleEntries ->
                calendarEntries = googleEntries
                publishState()
            }.onFailure { googleException ->
                calendarEntries = cachedRemindersResult
                    .getOrDefault(emptyList())
                    .toCachedCalendarEntries()

                publishState(
                    errorMessage = buildFallbackMessage(
                        googleException = googleException,
                        cacheException = cachedRemindersResult.exceptionOrNull()
                    )
                )
            }
        }
    }

    fun goToPreviousMonth() {
        visibleMonth = visibleMonth.minusMonths(1)
        selectedDate = alignSelectedDate(selectedDate, visibleMonth)
        reloadCalendar()
    }

    fun goToNextMonth() {
        visibleMonth = visibleMonth.plusMonths(1)
        selectedDate = alignSelectedDate(selectedDate, visibleMonth)
        reloadCalendar()
    }

    fun onMonthSelected(monthIndex: Int) {
        visibleMonth = YearMonth.of(visibleMonth.year, monthIndex + 1)
        selectedDate = alignSelectedDate(selectedDate, visibleMonth)
        reloadCalendar()
    }

    fun onYearSelected(year: Int) {
        visibleMonth = YearMonth.of(year, visibleMonth.monthValue)
        selectedDate = alignSelectedDate(selectedDate, visibleMonth)
        reloadCalendar()
    }

    fun onDaySelected(date: LocalDate) {
        selectedDate = date
        visibleMonth = YearMonth.from(date)
        reloadCalendar()
    }

    fun deleteCalendarItem(item: CalendarReminderDetailUiModel) {
        viewModelScope.launch {
            runCatching {
                val reminder = item.localReminderId?.let { reminderId ->
                    cachedReminders.firstOrNull { it.id == reminderId }
                } ?: item.googleCalendarEventId?.let { eventId ->
                    cachedReminders.firstOrNull { it.googleCalendarEventId == eventId }
                }

                if (reminder != null) {
                    googleCalendarSynchronizer.deleteReminderEvent(reminder)
                    deleteReminderUseCase(reminder)
                    reminderScheduler.cancelReminder(reminder.id)
                    reminderScheduler.scheduleNextDaySummary()
                } else {
                    item.googleCalendarEventId?.let { eventId ->
                        googleCalendarSynchronizer.deleteEventById(eventId)
                    }
                }
            }.onSuccess {
                reloadCalendar()
            }.onFailure { exception ->
                publishState(
                    errorMessage = exception.message
                        ?: "No fue posible eliminar el evento seleccionado."
                )
            }
        }
    }

    private suspend fun loadCachedReminders(): List<Reminder> {
        return getRemindersUseCase()
            .sortedWith(
                compareBy<Reminder> {
                    it.scheduleState.nextTriggerAtEpochMillis ?: it.scheduledAtEpochMillis
                }
                    .thenByDescending { it.isUrgent }
                    .thenBy { it.title }
            )
    }

    private suspend fun loadGoogleCalendarEntries(): List<CalendarEntry> {
        if (CalendarActionRules.shouldUseOfflineFallback(googleCalendarAuthManager.hasCalendarPermission())) {
            throw GoogleCalendarAuthException.NotConnected
        }

        val (rangeStart, rangeEndExclusive) = buildVisibleDateRange(visibleMonth)
        val accessToken = googleCalendarAuthManager.getAccessToken()
        val googleEvents = googleCalendarRestClient.listEvents(
            accessToken = accessToken,
            timeMin = rangeStart.atStartOfDay(zoneId).toInstant(),
            timeMax = rangeEndExclusive.atStartOfDay(zoneId).toInstant()
        )

        return googleEvents
            .flatMap { event ->
                event.toCalendarEntries(rangeStart, rangeEndExclusive)
            }
            .sortedWith(
                compareBy<CalendarEntry> { it.date }
                    .thenBy { it.time ?: LocalTime.MIN }
                    .thenBy { it.title }
            )
    }

    private fun List<Reminder>.toCachedCalendarEntries(): List<CalendarEntry> {
        val (rangeStart, rangeEndExclusive) = buildVisibleDateRange(visibleMonth)
        val entries = mutableListOf<CalendarEntry>()

        forEach { reminder ->
            var currentDate = rangeStart
            val reminderTime = ReminderDateTimeFormatter.toLocalTime(reminder.scheduledAtEpochMillis)

            while (currentDate.isBefore(rangeEndExclusive)) {
                if (occurrenceCalculator.occursOnDate(reminder, currentDate)) {
                    entries += CalendarEntry(
                        id = reminder.id.toString(),
                        title = reminder.title,
                        detail = reminder.detail,
                        date = currentDate,
                        time = reminderTime,
                        type = reminder.type,
                        isCompleted = reminder.isCompleted,
                        localReminderId = reminder.id,
                        googleCalendarEventId = reminder.googleCalendarEventId
                    )
                }

                currentDate = currentDate.plusDays(1)
            }
        }

        return entries.sortedWith(
            compareBy<CalendarEntry> { it.date }
                .thenBy { it.time ?: LocalTime.MIN }
                .thenBy { it.title }
        )
    }

    private fun GoogleCalendarEvent.toCalendarEntries(
        rangeStart: LocalDate,
        rangeEndExclusive: LocalDate
    ): List<CalendarEntry> {
        val resolvedType = resolveEventType(title, description)
        val resolvedDetail = description.trim().ifBlank {
            if (isAllDay) {
                "Evento de Google Calendar de dia completo."
            } else {
                "Evento de Google Calendar."
            }
        }

        if (isAllDay) {
            val eventStart = startDate ?: return emptyList()
            val eventEndExclusive = endDate ?: eventStart.plusDays(1)
            val entries = mutableListOf<CalendarEntry>()
            var currentDate = maxOfDate(eventStart, rangeStart)
            val lastDateExclusive = minOfDate(eventEndExclusive, rangeEndExclusive)

            while (currentDate.isBefore(lastDateExclusive)) {
                entries += CalendarEntry(
                    id = "$id-${currentDate}",
                    title = title,
                    detail = resolvedDetail,
                    date = currentDate,
                    time = null,
                    type = resolvedType,
                    isCompleted = isCompleted,
                    googleCalendarEventId = id
                )
                currentDate = currentDate.plusDays(1)
            }

            return entries
        }

        val startAt = startDateTime ?: return emptyList()
        val eventDate = startAt.withZoneSameInstant(zoneId).toLocalDate()
        if (eventDate.isBefore(rangeStart) || !eventDate.isBefore(rangeEndExclusive)) {
            return emptyList()
        }

        return listOf(
            CalendarEntry(
                id = id,
                title = title,
                detail = resolvedDetail,
                date = eventDate,
                time = startAt.withZoneSameInstant(zoneId).toLocalTime(),
                type = resolvedType,
                isCompleted = isCompleted,
                googleCalendarEventId = id
            )
        )
    }

    private fun publishState(errorMessage: String? = null) {
        val entriesByDate = calendarEntries.groupBy { it.date }
        val holidaysByDate = holidayProvider.getHolidaysForMonth(visibleMonth)
        val selectedDateEntries = entriesByDate[selectedDate].orEmpty()
        val selectedDateHolidays = holidayProvider.getHolidays(selectedDate)

        _uiState.update { currentState ->
            currentState.copy(
                isLoading = false,
                errorMessage = errorMessage,
                monthTitle = visibleMonth.format(monthTitleFormatter).toUiTitleCase(),
                selectedMonthIndex = visibleMonth.monthValue - 1,
                selectedYear = visibleMonth.year,
                yearOptions = buildYearOptions(visibleMonth.year),
                days = buildCalendarDays(entriesByDate, holidaysByDate),
                selectedDateTitle = selectedDate.format(selectedDateFormatter).toUiTitleCase(),
                selectedDateSummary = buildSelectedDateSummary(
                    eventCount = selectedDateEntries.size,
                    holidayCount = selectedDateHolidays.size
                ),
                selectedHolidays = selectedDateHolidays.map { holiday -> holiday.name },
                selectedDateReminders = selectedDateEntries.map { entry ->
                    entry.toDetailUiModel()
                },
                filteredItems = buildFilteredItems(calendarEntries, holidaysByDate),
                emptyStateMessage = if (selectedDateEntries.isEmpty()) {
                    if (selectedDateHolidays.isEmpty()) {
                        "No hay eventos para este dia."
                    } else {
                        "No hay eventos, pero el feriado sigue visible para esta fecha."
                    }
                } else {
                    ""
                }
            )
        }
    }

    private fun buildFilteredItems(
        entries: List<CalendarEntry>,
        holidaysByDate: Map<LocalDate, List<ChileanHoliday>>
    ): List<CalendarFilteredListItemUiModel> {
        val eventItems = entries.map { entry ->
            CalendarFilteredListItemUiModel(
                date = entry.date,
                dateTitle = entry.date.format(selectedDateFormatter).toUiTitleCase(),
                detail = entry.toDetailUiModel(),
                style = entry.toIndicatorStyle()
            )
        }
        val holidayItems = holidaysByDate.flatMap { (date, holidays) ->
            holidays.map { holiday ->
                CalendarFilteredListItemUiModel(
                    date = date,
                    dateTitle = date.format(selectedDateFormatter).toUiTitleCase(),
                    holidayName = holiday.name,
                    style = CalendarIndicatorStyle.HOLIDAY
                )
            }
        }

        return (eventItems + holidayItems).sortedWith(
            compareBy<CalendarFilteredListItemUiModel> { it.date }
                .thenBy { it.detail?.time ?: "" }
                .thenBy { it.detail?.title ?: it.holidayName.orEmpty() }
        )
    }

    private fun CalendarEntry.toDetailUiModel(): CalendarReminderDetailUiModel {
        return CalendarReminderDetailUiModel(
            id = id,
            title = title,
            detail = detail,
            time = time?.format(storedTimeFormatter) ?: "Todo el dia",
            type = type,
            isCompleted = isCompleted,
            localReminderId = localReminderId,
            googleCalendarEventId = googleCalendarEventId,
            canDelete = CalendarActionRules.canDelete(
                CalendarReminderDetailUiModel(
                    id = id,
                    title = title,
                    detail = detail,
                    time = time?.format(storedTimeFormatter) ?: "Todo el dia",
                    type = type,
                    isCompleted = isCompleted,
                    localReminderId = localReminderId,
                    googleCalendarEventId = googleCalendarEventId
                )
            )
        )
    }

    private fun CalendarEntry.toIndicatorStyle(): CalendarIndicatorStyle {
        return when {
            isCompleted -> CalendarIndicatorStyle.COMPLETED
            type == ReminderType.BIRTHDAY -> CalendarIndicatorStyle.BIRTHDAY
            else -> CalendarIndicatorStyle.REMINDER
        }
    }

    private fun buildCalendarDays(
        entriesByDate: Map<LocalDate, List<CalendarEntry>>,
        holidaysByDate: Map<LocalDate, List<ChileanHoliday>>
    ): List<CalendarDayUiModel> {
        val (calendarStart, calendarEndExclusive) = buildVisibleDateRange(visibleMonth)
        val totalCells = calendarEndExclusive.toEpochDay() - calendarStart.toEpochDay()

        return (0 until totalCells.toInt()).map { index ->
            val currentDate = calendarStart.plusDays(index.toLong())
            val entries = entriesByDate[currentDate].orEmpty()
            val holidays = holidaysByDate[currentDate].orEmpty()

            CalendarDayUiModel(
                date = currentDate,
                dayNumber = currentDate.dayOfMonth.toString(),
                isCurrentMonth = YearMonth.from(currentDate) == visibleMonth,
                isToday = currentDate == today,
                isSelected = currentDate == selectedDate,
                holidayLabel = buildHolidayLabel(holidays),
                indicators = buildIndicators(entries, holidays),
                summaryCountLabel = entries.size.takeIf { it > 1 }?.let { "+$it" }
            )
        }
    }

    private fun buildIndicators(
        entries: List<CalendarEntry>,
        holidays: List<ChileanHoliday>
    ): List<CalendarIndicatorUiModel> {
        val birthdayCount = entries.count { it.type == ReminderType.BIRTHDAY }
        val activeEventCount = entries.count {
            it.type == ReminderType.DEFAULT && !it.isCompleted
        }
        val completedCount = entries.count { it.isCompleted }
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

        if (activeEventCount > 0) {
            indicators += CalendarIndicatorUiModel(
                label = activeEventCount.toString(),
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

    private fun buildVisibleDateRange(month: YearMonth): Pair<LocalDate, LocalDate> {
        val firstDayOfMonth = month.atDay(1)
        val firstDayOffset = (firstDayOfMonth.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
        val calendarStart = firstDayOfMonth.minusDays(firstDayOffset.toLong())
        val totalDaySlots = firstDayOffset + month.lengthOfMonth()
        val totalCells = ((totalDaySlots + 6) / 7) * 7

        return calendarStart to calendarStart.plusDays(totalCells.toLong())
    }

    private fun buildHolidayLabel(holidays: List<ChileanHoliday>): String? {
        return when {
            holidays.isEmpty() -> null
            holidays.size == 1 -> holidays.first().name
            else -> "${holidays.first().name} +${holidays.size - 1}"
        }
    }

    private fun buildSelectedDateSummary(
        eventCount: Int,
        holidayCount: Int
    ): String {
        return when {
            eventCount > 0 && holidayCount > 0 ->
                "$eventCount ${pluralize(eventCount, "evento")} - $holidayCount ${pluralize(holidayCount, "feriado")}"

            eventCount > 0 ->
                "$eventCount ${pluralize(eventCount, "evento")}"

            holidayCount > 0 ->
                "$holidayCount ${pluralize(holidayCount, "feriado")}"

            else -> "Sin actividad para la fecha seleccionada"
        }
    }

    private fun buildFallbackMessage(
        googleException: Throwable,
        cacheException: Throwable?
    ): String {
        val baseMessage = when (googleException) {
            is GoogleCalendarAuthException ->
                "Google Calendar no esta conectado. Mostrando recordatorios guardados en el dispositivo."

            else ->
                "No fue posible cargar Google Calendar. Mostrando datos guardados en el dispositivo."
        }

        return if (cacheException == null) {
            baseMessage
        } else {
            "$baseMessage El cache local no pudo cargarse por completo."
        }
    }

    private fun resolveEventType(title: String, description: String): ReminderType {
        val normalizedContent = normalizeText("$title $description")

        return if (
            "cumple" in normalizedContent ||
            "cumpleanos" in normalizedContent ||
            "birthday" in normalizedContent
        ) {
            ReminderType.BIRTHDAY
        } else {
            ReminderType.DEFAULT
        }
    }

    private fun normalizeText(value: String): String {
        return Normalizer.normalize(value.lowercase(locale), Normalizer.Form.NFD)
            .replace(Regex("""\p{InCombiningDiacriticalMarks}+"""), "")
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

    private fun maxOfDate(first: LocalDate, second: LocalDate): LocalDate {
        return if (first.isAfter(second)) first else second
    }

    private fun minOfDate(first: LocalDate, second: LocalDate): LocalDate {
        return if (first.isBefore(second)) first else second
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
