package com.luistureo.voicereminderapp.presentation.calendar

import com.luistureo.voicereminderapp.core.utils.DateTimeFormatter
import com.luistureo.voicereminderapp.domain.model.ReminderSource
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

    fun creationChoices(): List<ReminderSource> {
        return listOf(ReminderSource.MANUAL, ReminderSource.CAMERA)
    }

    fun canDelete(detail: CalendarReminderDetailUiModel): Boolean {
        return detail.localReminderId != null || !detail.googleCalendarEventId.isNullOrBlank()
    }

    fun shouldUseOfflineFallback(hasGoogleCalendarPermission: Boolean): Boolean {
        return !hasGoogleCalendarPermission
    }
}
