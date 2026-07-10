package com.luistureo.voicereminderapp.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luistureo.voicereminderapp.core.alarm.ReminderScheduler
import com.luistureo.voicereminderapp.core.calendar.ChileanHoliday
import com.luistureo.voicereminderapp.core.calendar.ChileanHolidayProvider
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarAuthManager
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarErrorCode
import com.luistureo.voicereminderapp.core.calendar.microsoft.MicrosoftCalendarErrorCode
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarSyncLogger
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarConflictPolicy
import com.luistureo.voicereminderapp.core.calendar.unified.PerReminderCalendarSyncPolicy
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarSuspensionPolicy
import com.luistureo.voicereminderapp.core.calendar.unified.MeetingUrlPolicy
import com.luistureo.voicereminderapp.core.calendar.unified.UnifiedCalendarCardDisplayPolicy
import com.luistureo.voicereminderapp.core.calendar.unified.UnifiedCalendarSynchronizer
import com.luistureo.voicereminderapp.core.calendar.unified.UnifiedCalendarSyncSummary
import com.luistureo.voicereminderapp.core.reminder.ReminderOccurrenceCalculator
import com.luistureo.voicereminderapp.core.utils.DateTimeFormatter as ReminderDateTimeFormatter
import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.model.ReminderType
import com.luistureo.voicereminderapp.domain.usecase.DeleteReminderUseCase
import com.luistureo.voicereminderapp.domain.usecase.GetRemindersUseCase
import com.luistureo.voicereminderapp.domain.usecase.UpdateReminderUseCase
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
    private val deleteReminderUseCase: DeleteReminderUseCase,
    private val updateReminderUseCase: UpdateReminderUseCase,
    private val googleCalendarAuthManager: GoogleCalendarAuthManager,
    private val unifiedCalendarSynchronizer: UnifiedCalendarSynchronizer,
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
        val googleCalendarEventId: String? = null,
        val providerExternalIds: Map<CalendarProvider, String> = emptyMap(),
        val providerLines: List<String> = emptyList(),
        val meetingUrl: String? = null,
        val externalEditNote: String? = null,
        val isSuspended: Boolean = false,
        val syncActions: Set<CalendarProvider> = emptySet(),
        val canEdit: Boolean = false,
        val isAllDay: Boolean = false,
        val occurrenceAtEpochMillis: Long? = null
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
    private var lastExternalImportAtEpochMillis: Long = 0L
    private var lastExternalImportMonth: YearMonth? = null

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

    fun reloadCalendar(forceExternalSync: Boolean = false) {
        if (forceExternalSync) {
            lastExternalImportAtEpochMillis = 0L
            lastExternalImportMonth = null
        }
        _uiState.update { currentState ->
            currentState.copy(isLoading = true, errorMessage = null)
        }

        viewModelScope.launch {
            val externalSyncResult = runCatching {
                syncExternalCalendarsForVisibleMonth()
            }
            externalSyncResult.exceptionOrNull()?.let { exception ->
                CalendarSyncLogger.visibleMonthLoad(
                    provider = visibleMonthErrorProvider(),
                    succeeded = false,
                    reason = visibleMonthFailureReason(exception)
                )
            } ?: CalendarSyncLogger.visibleMonthLoad(
                provider = visibleMonthSuccessProvider(),
                succeeded = true
            )
            val cachedRemindersResult = runCatching { loadCachedReminders() }
            cachedReminders = cachedRemindersResult.getOrDefault(emptyList())
            calendarEntries = cachedReminders.toCachedCalendarEntries()

            publishState(
                errorMessage = buildFallbackMessage(
                    cacheException = cachedRemindersResult.exceptionOrNull()
                ),
                syncError = externalSyncResult.exceptionOrNull()?.let { exception ->
                    CalendarSyncInlineError(
                        provider = visibleMonthErrorProvider(),
                        reason = visibleMonthFailureReason(exception)
                    )
                },
                showDeleteSyncSuccess = externalSyncResult.getOrNull()
                    ?.completedDeleteCount
                    ?.let { it > 0 }
                    ?: _uiState.value.showDeleteSyncSuccess
            )
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
        deleteCalendarItem(item, deleteExternalCalendars = true)
    }

    fun deleteCalendarItem(
        item: CalendarReminderDetailUiModel,
        deleteExternalCalendars: Boolean
    ) {
        viewModelScope.launch {
            runCatching {
                val reminder = item.localReminderId?.let { reminderId ->
                    cachedReminders.firstOrNull { it.id == reminderId }
                } ?: item.googleCalendarEventId?.let { eventId ->
                    cachedReminders.firstOrNull { it.googleCalendarEventId == eventId }
                }

                if (reminder != null) {
                    val deletionResult = unifiedCalendarSynchronizer.deleteReminderEvent(
                        reminder = reminder,
                        deleteExternalCalendars = deleteExternalCalendars
                    )
                    if (deletionResult.pendingDeleteProviders.isEmpty()) {
                        deleteReminderUseCase(deletionResult)
                    }
                    reminderScheduler.cancelReminder(reminder.id)
                    reminderScheduler.scheduleNextDaySummary()
                }
            }.onSuccess {
                reloadCalendar()
            }.onFailure {
                publishState(
                    errorMessage = "No fue posible eliminar el evento seleccionado."
                )
            }
        }
    }

    fun syncCalendarItemWithProvider(
        item: CalendarReminderDetailUiModel,
        provider: CalendarProvider
    ) {
        viewModelScope.launch {
            runCatching {
                val reminder = item.localReminderId?.let { reminderId ->
                    cachedReminders.firstOrNull { it.id == reminderId }
                } ?: return@runCatching
                val updatedReminder = PerReminderCalendarSyncPolicy.applyTargets(
                    reminder = reminder,
                    targetProviders = PerReminderCalendarSyncPolicy.selectedTargets(reminder) +
                            provider,
                    markExistingForUpdate = false
                )

                updateReminderUseCase(updatedReminder)
                val syncedReminder = unifiedCalendarSynchronizer.syncSavedReminder(updatedReminder)
                reminderScheduler.syncReminderSchedule(syncedReminder)
            }.onSuccess {
                reloadCalendar()
            }.onFailure {
                publishState(
                    errorMessage = "No fue posible sincronizar con ${provider.displayName}."
                )
            }
        }
    }

    fun reactivateCalendarItem(item: CalendarReminderDetailUiModel) {
        viewModelScope.launch {
            runCatching {
                val reminder = item.localReminderId?.let { reminderId ->
                    cachedReminders.firstOrNull { it.id == reminderId }
                } ?: return@runCatching
                val reactivatedReminder = CalendarSuspensionPolicy.reactivate(reminder)

                CalendarSyncLogger.appointmentReactivated(reminder.originProvider)

                updateReminderUseCase(reactivatedReminder)
                val syncedReminder = unifiedCalendarSynchronizer.syncSavedReminder(reactivatedReminder)
                reminderScheduler.syncReminderSchedule(syncedReminder)
            }.onSuccess {
                reloadCalendar()
            }.onFailure {
                publishState(
                    errorMessage = "No fue posible reactivar la cita."
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

    private suspend fun syncExternalCalendarsForVisibleMonth(): UnifiedCalendarSyncSummary? {
        return if (
            !googleCalendarAuthManager.isConnected() &&
            !unifiedCalendarSynchronizer.isMicrosoftConnected
        ) {
            null
        } else {
            val nowEpochMillis = System.currentTimeMillis()
            if (
                lastExternalImportMonth == visibleMonth &&
                nowEpochMillis - lastExternalImportAtEpochMillis <
                EXTERNAL_IMPORT_COOLDOWN_MILLIS
            ) {
                CalendarSyncLogger.cooldown(
                    provider = null,
                    action = "calendar_open_import",
                    remainingMillis = EXTERNAL_IMPORT_COOLDOWN_MILLIS -
                            (nowEpochMillis - lastExternalImportAtEpochMillis)
                )
                null
            } else {
                lastExternalImportAtEpochMillis = nowEpochMillis
                lastExternalImportMonth = visibleMonth
                val (rangeStart, rangeEndExclusive) = buildVisibleDateRange(visibleMonth)
                val timeMin = rangeStart.atStartOfDay(zoneId).toInstant()
                val timeMax = rangeEndExclusive.atStartOfDay(zoneId).toInstant()
                unifiedCalendarSynchronizer.importExternalEvents(
                    timeMin = timeMin,
                    timeMax = timeMax
                )
            }
        }
    }

    private fun List<Reminder>.toCachedCalendarEntries(): List<CalendarEntry> {
        val (rangeStart, rangeEndExclusive) = buildVisibleDateRange(visibleMonth)
        val entries = mutableListOf<CalendarEntry>()

        forEach { reminder ->
            var currentDate = rangeStart
            val reminderTime = ReminderDateTimeFormatter.toLocalTime(reminder.scheduledAtEpochMillis)
            val reminderStartAt = reminder.scheduledAtEpochMillis

            while (currentDate.isBefore(rangeEndExclusive)) {
                if (occurrenceCalculator.occursOnDate(reminder, currentDate)) {
                    val occurrenceAt = currentDate
                        .atTime(reminderTime)
                        .atZone(zoneId)
                        .toInstant()
                        .toEpochMilli()
                    val isSuspendedOccurrence = reminder.isSuspended &&
                            (
                                    reminder.suspendedOccurrenceAtEpochMillis == null ||
                                            reminder.suspendedOccurrenceAtEpochMillis == occurrenceAt ||
                                            reminder.suspendedOccurrenceAtEpochMillis == reminderStartAt
                                    )
                    entries += CalendarEntry(
                        id = CalendarConflictPolicy.occurrenceCandidateId(
                            reminder.id,
                            occurrenceAt
                        ),
                        title = reminder.title,
                        detail = reminder.detail,
                        date = currentDate,
                        time = if (reminder.isAllDay) null else reminderTime,
                        type = reminder.type,
                        isCompleted = reminder.isCompleted,
                        localReminderId = reminder.id,
                        googleCalendarEventId = reminder.googleCalendarEventId,
                        providerExternalIds = reminder.externalIdsByProvider,
                        providerLines = UnifiedCalendarCardDisplayPolicy.buildProviderLines(reminder),
                        meetingUrl = reminder.meetingUrl,
                        externalEditNote = reminder.externalEditNote,
                        isSuspended = isSuspendedOccurrence,
                        syncActions = reminder.buildSyncActions(),
                        canEdit = reminder.canOpenFromCalendarEditor(),
                        isAllDay = reminder.isAllDay,
                        occurrenceAtEpochMillis = occurrenceAt
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

    private fun publishState(
        errorMessage: String? = null,
        syncError: CalendarSyncInlineError? = null,
        showDeleteSyncSuccess: Boolean = _uiState.value.showDeleteSyncSuccess
    ) {
        val entriesByDate = calendarEntries.groupBy { it.date }
        val holidaysByDate = holidayProvider.getHolidaysForMonth(visibleMonth)
        val selectedDateEntries = entriesByDate[selectedDate].orEmpty()
        val selectedDateHolidays = holidayProvider.getHolidays(selectedDate)
        val duplicateGroups = buildDuplicateGroups(calendarEntries)
        val duplicateItemIds = duplicateGroups
            .flatMap { group -> group.candidates.map { candidate -> candidate.id } }
            .toSet()
        val selectedDuplicateItemIds = duplicateGroups
            .filter { group -> group.candidates.firstOrNull()?.date == selectedDate }
            .flatMap { group -> group.candidates.map { candidate -> candidate.id } }
            .toSet()

        _uiState.update { currentState ->
            currentState.copy(
                isLoading = false,
                errorMessage = errorMessage,
                syncError = syncError,
                showDeleteSyncSuccess = showDeleteSyncSuccess,
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
                    entry.toDetailUiModel(hasNearbySchedule = entry.id in selectedDuplicateItemIds)
                },
                filteredItems = buildFilteredItems(
                    entries = calendarEntries,
                    holidaysByDate = holidaysByDate,
                    duplicateItemIds = duplicateItemIds
                ),
                emptyStateMessage = if (selectedDateEntries.isEmpty()) {
                    if (selectedDateHolidays.isEmpty()) {
                        "No hay eventos para este dia."
                    } else {
                        "No hay eventos, pero el feriado sigue visible para esta fecha."
                    }
                } else {
                    ""
                },
                selectedDateDuplicateWarning = selectedDuplicateItemIds
                    .takeIf { it.isNotEmpty() }
                    ?.let { ids ->
                        CalendarDuplicateWarningUiModel(
                            selectedDate = selectedDate,
                            duplicateCount = ids.size
                        )
                    }
            )
        }
    }

    private fun visibleMonthErrorProvider(): CalendarProvider {
        return if (googleCalendarAuthManager.isConnected()) {
            CalendarProvider.GOOGLE_CALENDAR
        } else {
            CalendarProvider.MICROSOFT_CALENDAR
        }
    }

    private fun visibleMonthSuccessProvider(): CalendarProvider? {
        return when {
            googleCalendarAuthManager.isConnected() -> CalendarProvider.GOOGLE_CALENDAR
            unifiedCalendarSynchronizer.isMicrosoftConnected ->
                CalendarProvider.MICROSOFT_CALENDAR
            else -> null
        }
    }

    private fun visibleMonthFailureReason(exception: Throwable): String {
        return when (visibleMonthErrorProvider()) {
            CalendarProvider.MICROSOFT_CALENDAR ->
                MicrosoftCalendarErrorCode.fromFailure(exception).value
            else -> GoogleCalendarErrorCode.fromSyncFailure(exception).value
        }
    }

    private fun buildDuplicateGroups(
        entries: List<CalendarEntry>
    ): List<CalendarConflictPolicy.ConflictGroup> {
        return CalendarConflictPolicy.findConflicts(
            entries.filter { !it.isSuspended }.map { entry ->
                CalendarConflictPolicy.Candidate(
                    id = entry.id,
                    startAtEpochMillis = entry.occurrenceAtEpochMillis,
                    isAllDay = entry.isAllDay
                )
            }
        )
    }

    private fun buildFilteredItems(
        entries: List<CalendarEntry>,
        holidaysByDate: Map<LocalDate, List<ChileanHoliday>>,
        duplicateItemIds: Set<String>
    ): List<CalendarFilteredListItemUiModel> {
        val eventItems = entries.map { entry ->
            CalendarFilteredListItemUiModel(
                date = entry.date,
                dateTitle = entry.date.format(selectedDateFormatter).toUiTitleCase(),
                detail = entry.toDetailUiModel(
                    hasNearbySchedule = entry.id in duplicateItemIds
                ),
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

    private fun CalendarEntry.toDetailUiModel(
        hasNearbySchedule: Boolean = false
    ): CalendarReminderDetailUiModel {
        return CalendarReminderDetailUiModel(
            id = id,
            title = title,
            detail = detail,
            time = time?.format(storedTimeFormatter) ?: "Todo el dia",
            type = type,
            isCompleted = isCompleted,
            localReminderId = localReminderId,
            googleCalendarEventId = googleCalendarEventId,
            providerExternalIds = providerExternalIds,
            providerLines = providerLines,
            meetingUrl = meetingUrl,
            externalEditNote = externalEditNote,
            hasNearbySchedule = hasNearbySchedule,
            isSuspended = isSuspended,
            syncActions = syncActions,
            canEdit = canEdit,
            canDelete = CalendarActionRules.canDelete(
                CalendarReminderDetailUiModel(
                    id = id,
                    title = title,
                    detail = detail,
                    time = time?.format(storedTimeFormatter) ?: "Todo el dia",
                    type = type,
                    isCompleted = isCompleted,
                    localReminderId = localReminderId,
                    googleCalendarEventId = googleCalendarEventId,
                    providerExternalIds = providerExternalIds
                )
            ),
            canReactivate = isSuspended && localReminderId != null
        )
    }

    private fun Reminder.canOpenFromCalendarEditor(): Boolean {
        return originProvider == CalendarProvider.APP &&
                !isOnlineMeeting &&
                !MeetingUrlPolicy.isSupportedMeetingUrl(meetingUrl)
    }

    private fun Reminder.buildSyncActions(): Set<CalendarProvider> {
        return buildSet {
            if (
                PerReminderCalendarSyncPolicy.canSyncImportedReminderTo(
                    reminder = this@buildSyncActions,
                    provider = CalendarProvider.GOOGLE_CALENDAR,
                    isProviderConnected = googleCalendarAuthManager.isConnected()
                )
            ) {
                add(CalendarProvider.GOOGLE_CALENDAR)
            }
            if (
                PerReminderCalendarSyncPolicy.canSyncImportedReminderTo(
                    reminder = this@buildSyncActions,
                    provider = CalendarProvider.MICROSOFT_CALENDAR,
                    isProviderConnected = unifiedCalendarSynchronizer.isMicrosoftConnected
                )
            ) {
                add(CalendarProvider.MICROSOFT_CALENDAR)
            }
        }
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
        cacheException: Throwable?
    ): String? {
        return cacheException?.let { "El cache local no pudo cargarse por completo." }
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

    private companion object {
        const val EXTERNAL_IMPORT_COOLDOWN_MILLIS = 10 * 60_000L
    }
}
