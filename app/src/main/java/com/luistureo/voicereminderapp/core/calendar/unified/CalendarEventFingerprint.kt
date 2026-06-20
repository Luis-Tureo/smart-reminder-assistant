package com.luistureo.voicereminderapp.core.calendar.unified

import com.luistureo.voicereminderapp.domain.model.Reminder
import java.security.MessageDigest

object CalendarEventFingerprint {
    fun fromReminder(reminder: Reminder): String {
        val canonical = listOf(
            reminder.title.trim(),
            normalizeDetail(reminder.detail),
            reminder.scheduledAtEpochMillis.toString(),
            reminder.isAllDay.toString(),
            reminder.isCompleted.toString(),
            reminder.isUrgent.toString(),
            reminder.isSuspended.toString(),
            reminder.suspendedOccurrenceAtEpochMillis?.toString().orEmpty(),
            reminder.recurrenceLabel.orEmpty(),
            reminder.meetingUrl.orEmpty()
        ).joinToString(separator = "|")
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    fun normalizeDetail(detail: String): String {
        return detail
            .substringBefore("\n\nEstado:")
            .substringBefore("\n\nCreado desde Smart Reminder Assistant.")
            .trim()
    }
}

object CalendarIdempotencyKey {
    fun googleEventId(reminderId: Int): String {
        return "sra" + stableUuid(reminderId).replace("-", "")
    }

    fun microsoftTransactionId(reminderId: Int): String = stableUuid(reminderId)

    private fun stableUuid(reminderId: Int): String {
        return java.util.UUID.nameUUIDFromBytes(
            "smart-reminder:$reminderId".toByteArray(Charsets.UTF_8)
        ).toString()
    }
}
