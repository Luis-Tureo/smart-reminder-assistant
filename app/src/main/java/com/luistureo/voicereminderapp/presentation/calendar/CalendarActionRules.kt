package com.luistureo.voicereminderapp.presentation.calendar

import com.luistureo.voicereminderapp.core.utils.DateTimeFormatter
import com.luistureo.voicereminderapp.core.reminder.ReminderTemporalValidationPolicy
import java.time.LocalDate

object CalendarActionRules {

    fun filterItems(
        items: List<CalendarFilteredListItemUiModel>,
        activeFilter: CalendarIndicatorStyle?
    ): List<CalendarFilteredListItemUiModel> {
        return activeFilter?.let { filter ->
            items.filter { it.style == filter }
        } ?: items
    }

    fun shouldShowMonthGrid(activeFilter: CalendarIndicatorStyle?): Boolean {
        return activeFilter == null
    }

    fun formatPrefilledDate(date: LocalDate): String {
        return DateTimeFormatter.formatDate(
            day = date.dayOfMonth,
            month = date.monthValue,
            year = date.year
        )
    }

    fun shouldLockDate(prefilledDate: String): Boolean {
        return prefilledDate.isNotBlank()
    }

    fun canCreateReminderOnDate(
        selectedDate: LocalDate,
        today: LocalDate = LocalDate.now()
    ): Boolean {
        return ReminderTemporalValidationPolicy.canCreateOnDate(selectedDate, today)
    }

    fun canDelete(detail: CalendarReminderDetailUiModel): Boolean {
        return detail.localReminderId != null ||
                !detail.googleCalendarEventId.isNullOrBlank() ||
                detail.providerExternalIds.isNotEmpty()
    }

    fun shouldUseOfflineFallback(hasGoogleCalendarPermission: Boolean): Boolean {
        return !hasGoogleCalendarPermission
    }
}
