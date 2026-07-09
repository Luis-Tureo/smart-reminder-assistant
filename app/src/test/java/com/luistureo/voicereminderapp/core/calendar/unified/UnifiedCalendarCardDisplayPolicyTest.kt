package com.luistureo.voicereminderapp.core.calendar.unified

import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import com.luistureo.voicereminderapp.domain.model.Reminder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedCalendarCardDisplayPolicyTest {

    @Test
    fun appOriginSyncedWithBothProvidersUsesUnifiedText() {
        val lines = UnifiedCalendarCardDisplayPolicy.buildProviderLines(
            reminder().copy(
                originProvider = CalendarProvider.APP,
                syncedProviders = setOf(
                    CalendarProvider.APP,
                    CalendarProvider.GOOGLE_CALENDAR,
                    CalendarProvider.MICROSOFT_CALENDAR
                )
            )
        )

        assertTrue(lines.contains("Origen: Smart Reminder Assistant"))
        assertTrue(lines.contains("Sincronizado con: Google Calendar y Microsoft Calendar"))
    }

    @Test
    fun googleOriginAvoidsRedundantGoogleSyncText() {
        val lines = UnifiedCalendarCardDisplayPolicy.buildProviderLines(
            reminder().copy(
                originProvider = CalendarProvider.GOOGLE_CALENDAR,
                syncedProviders = setOf(
                    CalendarProvider.APP,
                    CalendarProvider.GOOGLE_CALENDAR,
                    CalendarProvider.MICROSOFT_CALENDAR
                )
            )
        )

        assertTrue(lines.contains("Origen: Google Calendar"))
        assertTrue(lines.contains("Sincronizado con: Microsoft Calendar y Smart Reminder Assistant"))
    }

    @Test
    fun microsoftOriginShowsGoogleAndAppAsSyncedTargets() {
        val lines = UnifiedCalendarCardDisplayPolicy.buildProviderLines(
            reminder().copy(
                originProvider = CalendarProvider.MICROSOFT_CALENDAR,
                syncedProviders = setOf(
                    CalendarProvider.APP,
                    CalendarProvider.GOOGLE_CALENDAR,
                    CalendarProvider.MICROSOFT_CALENDAR
                )
            )
        )

        assertTrue(lines.contains("Origen: Microsoft Calendar"))
        assertTrue(lines.contains("Sincronizado con: Google Calendar y Smart Reminder Assistant"))
    }

    @Test
    fun pendingOperationsAreNotRenderedAsVisibleProviderLines() {
        val withoutPending = UnifiedCalendarCardDisplayPolicy.buildProviderLines(reminder())
        val withPending = UnifiedCalendarCardDisplayPolicy.buildProviderLines(
            reminder().copy(
                pendingCreateProviders = setOf(CalendarProvider.MICROSOFT_CALENDAR)
            )
        )

        assertFalse(withoutPending.any { it.startsWith("Sincronización pendiente:") })
        assertFalse(withPending.any { it.startsWith("Sincronización pendiente:") })
    }

    private fun reminder(): Reminder {
        return Reminder(
            title = "Reunion",
            detail = "Reunion de equipo",
            scheduledAtEpochMillis = 1_800_000_000_000L
        )
    }
}
