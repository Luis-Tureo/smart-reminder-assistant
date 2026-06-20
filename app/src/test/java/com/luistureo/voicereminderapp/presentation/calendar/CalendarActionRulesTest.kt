package com.luistureo.voicereminderapp.presentation.calendar

import com.luistureo.voicereminderapp.domain.model.ReminderType
import com.luistureo.voicereminderapp.domain.model.ReminderSource
import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalendarActionRulesTest {

    @Test
    fun defaultFilterKeepsCalendarGridAndAllItems() {
        val items = listOf(
            filteredItem(CalendarIndicatorStyle.REMINDER),
            filteredItem(CalendarIndicatorStyle.HOLIDAY)
        )

        assertTrue(CalendarActionRules.shouldShowMonthGrid(null))
        assertEquals(items, CalendarActionRules.filterItems(items, null))
    }

    @Test
    fun activeLegendFilterHidesGridAndReturnsOnlyMatchingItems() {
        val items = listOf(
            filteredItem(CalendarIndicatorStyle.REMINDER),
            filteredItem(CalendarIndicatorStyle.BIRTHDAY),
            filteredItem(CalendarIndicatorStyle.COMPLETED)
        )

        val result = CalendarActionRules.filterItems(items, CalendarIndicatorStyle.BIRTHDAY)

        assertFalse(CalendarActionRules.shouldShowMonthGrid(CalendarIndicatorStyle.BIRTHDAY))
        assertEquals(1, result.size)
        assertEquals(CalendarIndicatorStyle.BIRTHDAY, result.first().style)
    }

    @Test
    fun selectedDateIsFormattedAndLockedForCreationFlows() {
        val prefilledDate = CalendarActionRules.formatPrefilledDate(LocalDate.of(2026, 6, 13))

        assertEquals("13/06/2026", prefilledDate)
        assertTrue(CalendarActionRules.shouldLockDate(prefilledDate))
        assertFalse(CalendarActionRules.shouldLockDate(""))
    }

    @Test
    fun createReminderChoiceKeepsSelectedDateForManualAndCameraFlows() {
        assertEquals(
            listOf(ReminderSource.MANUAL, ReminderSource.CAMERA),
            CalendarActionRules.creationChoices()
        )
    }

    @Test
    fun deleteIsAvailableForLocalOrExternalProviderItems() {
        assertTrue(CalendarActionRules.canDelete(detail(localId = 7, googleId = null)))
        assertTrue(CalendarActionRules.canDelete(detail(localId = null, googleId = "event-1")))
        assertTrue(
            CalendarActionRules.canDelete(
                detail(localId = null, googleId = null).copy(
                    providerExternalIds = mapOf(CalendarProvider.MICROSOFT_CALENDAR to "event-2")
                )
            )
        )
        assertFalse(CalendarActionRules.canDelete(detail(localId = null, googleId = null)))
    }

    @Test
    fun missingGooglePermissionUsesOfflineFallback() {
        assertTrue(CalendarActionRules.shouldUseOfflineFallback(hasGoogleCalendarPermission = false))
        assertFalse(CalendarActionRules.shouldUseOfflineFallback(hasGoogleCalendarPermission = true))
    }

    private fun filteredItem(style: CalendarIndicatorStyle): CalendarFilteredListItemUiModel {
        return CalendarFilteredListItemUiModel(
            date = LocalDate.of(2026, 6, 13),
            dateTitle = "Sabado 13 de junio",
            detail = detail(localId = 1, googleId = "event-1"),
            style = style
        )
    }

    private fun detail(
        localId: Int?,
        googleId: String?
    ): CalendarReminderDetailUiModel {
        return CalendarReminderDetailUiModel(
            id = googleId ?: localId?.toString().orEmpty(),
            title = "Comprar pan",
            detail = "Comprar pan",
            time = "09:00",
            type = ReminderType.DEFAULT,
            isCompleted = false,
            localReminderId = localId,
            googleCalendarEventId = googleId
        )
    }
}
