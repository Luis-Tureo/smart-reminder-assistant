package com.luistureo.voicereminderapp.core.calendar.unified

import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import com.luistureo.voicereminderapp.domain.model.Reminder
import kotlin.math.abs

object CalendarMeetingMergePolicy {
    fun findExisting(
        reminders: List<Reminder>,
        importingProvider: CalendarProvider,
        eventId: String,
        title: String,
        scheduledAtEpochMillis: Long?,
        isManagedCopy: Boolean,
        originProviderHint: CalendarProvider? = null,
        localIdHint: Int? = null
    ): Reminder? {
        reminders.firstOrNull {
            it.externalIdsByProvider[importingProvider] == eventId ||
                    (importingProvider == CalendarProvider.GOOGLE_CALENDAR &&
                            it.googleCalendarEventId == eventId) ||
                    (importingProvider == CalendarProvider.MICROSOFT_CALENDAR &&
                            it.microsoftCalendarEventId == eventId)
        }?.let { return it }
        if (!isManagedCopy || scheduledAtEpochMillis == null) return null

        return reminders.asSequence()
            .filterNot(Reminder::hiddenFromApp)
            .filter { it.externalIdsByProvider[importingProvider].isNullOrBlank() }
            .filter { localIdHint == null || it.id == localIdHint }
            .filter { originProviderHint == null || it.originProvider == originProviderHint }
            .filter { normalizeTitle(it.title) == normalizeTitle(title) }
            .filter { abs(it.scheduledAtEpochMillis - scheduledAtEpochMillis) <= 60_000L }
            .singleOrNull()
    }

    private fun normalizeTitle(value: String): String = value
        .removePrefix("Completado:")
        .trim()
        .lowercase()
}
