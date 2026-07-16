package com.luistureo.voicereminderapp.core.reminder

import com.luistureo.voicereminderapp.core.utils.DateTimeFormatter
import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.model.ReminderRecurrence
import com.luistureo.voicereminderapp.domain.model.ReminderRecurrenceUnit
import com.luistureo.voicereminderapp.domain.model.ReminderWeekday
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

class ReminderOccurrenceCalculator(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {

    fun resolveNextTriggerAtEpochMillis(
        reminder: Reminder,
        fromEpochMillis: Long = System.currentTimeMillis()
    ): Long? {
        val thresholdDateTime = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(fromEpochMillis),
            zoneId
        )
        val reminderDateTime = DateTimeFormatter.toLocalDateTime(reminder.scheduledAtEpochMillis)

        if (reminder.recurrence == null) {
            return reminder.scheduledAtEpochMillis.takeIf { it > fromEpochMillis }
        }

        if (!reminder.recurrence.isActive) {
            return null
        }

        var candidateDate = maxOf(reminderDateTime.toLocalDate(), thresholdDateTime.toLocalDate())

        repeat(3660) {
            if (occursOnDate(reminder, candidateDate)) {
                val candidateDateTime = LocalDateTime.of(candidateDate, reminderDateTime.toLocalTime())
                val candidateEpochMillis = candidateDateTime.atZone(zoneId).toInstant().toEpochMilli()
                if (candidateEpochMillis > fromEpochMillis) {
                    return candidateEpochMillis
                }
            }
            candidateDate = candidateDate.plusDays(1)
        }

        return null
    }

    fun occursOnDate(
        reminder: Reminder,
        date: LocalDate
    ): Boolean {
        val reminderDateTime = DateTimeFormatter.toLocalDateTime(reminder.scheduledAtEpochMillis)
        val reminderDate = reminderDateTime.toLocalDate()

        if (date.isBefore(reminderDate)) {
            return false
        }

        val recurrence = reminder.recurrence ?: return date == reminderDate
        if (!recurrence.isActive) {
            return date == reminderDate
        }

        return when (recurrence.unit) {
            ReminderRecurrenceUnit.DAY -> {
                val daysBetween = ChronoUnit.DAYS.between(reminderDate, date)
                daysBetween >= 0 && daysBetween % recurrence.normalizedInterval == 0L
            }

            ReminderRecurrenceUnit.WEEK -> matchesWeeklyRecurrence(
                recurrence = recurrence,
                reminderDate = reminderDate,
                date = date
            )

            ReminderRecurrenceUnit.MONTH -> {
                val monthsBetween = ChronoUnit.MONTHS.between(
                    reminderDate.withDayOfMonth(1),
                    date.withDayOfMonth(1)
                )
                monthsBetween >= 0 &&
                        reminderDate.dayOfMonth == date.dayOfMonth &&
                        monthsBetween % recurrence.normalizedInterval == 0L
            }

            ReminderRecurrenceUnit.YEAR -> {
                val yearsBetween = ChronoUnit.YEARS.between(
                    reminderDate.withDayOfYear(1),
                    date.withDayOfYear(1)
                )
                yearsBetween >= 0 &&
                        reminderDate.dayOfMonth == date.dayOfMonth &&
                        reminderDate.monthValue == date.monthValue &&
                        yearsBetween % recurrence.normalizedInterval == 0L
            }
        }
    }

    fun resolveOccurrenceAtEpochMillis(
        reminder: Reminder,
        date: LocalDate
    ): Long? {
        if (!occursOnDate(reminder, date)) {
            return null
        }

        val reminderTime = DateTimeFormatter.toLocalTime(reminder.scheduledAtEpochMillis)
        val occurrenceDateTime = LocalDateTime.of(date, reminderTime)

        return occurrenceDateTime.atZone(zoneId).toInstant().toEpochMilli()
    }

    private fun matchesWeeklyRecurrence(
        recurrence: ReminderRecurrence,
        reminderDate: LocalDate,
        date: LocalDate
    ): Boolean {
        val reminderWeekStart = reminderDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val dateWeekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weeksBetween = ChronoUnit.WEEKS.between(reminderWeekStart, dateWeekStart)

        if (weeksBetween < 0 || weeksBetween % recurrence.normalizedInterval != 0L) {
            return false
        }

        val targetWeekdays = recurrence.weekdays.ifEmpty {
            setOf(ReminderWeekday.fromDayOfWeek(reminderDate.dayOfWeek))
        }

        return targetWeekdays.any { it.dayOfWeek == date.dayOfWeek }
    }
}
