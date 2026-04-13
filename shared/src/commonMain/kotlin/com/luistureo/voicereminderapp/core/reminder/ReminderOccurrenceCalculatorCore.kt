package com.luistureo.voicereminderapp.core.reminder

import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.model.ReminderRecurrence
import com.luistureo.voicereminderapp.domain.model.ReminderRecurrenceUnit
import com.luistureo.voicereminderapp.domain.model.ReminderWeekday
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

// Contiene la parte multiplataforma del calculo de ocurrencias.
object ReminderOccurrenceCalculatorCore {

    fun resolveNextTriggerAtEpochMillis(
        reminder: Reminder,
        fromEpochMillis: Long,
        timeZoneId: String
    ): Long? {
        val timeZone = TimeZone.of(timeZoneId)
        val thresholdDateTime = Instant.fromEpochMilliseconds(fromEpochMillis)
            .toLocalDateTime(timeZone)
        val reminderDateTime = Instant.fromEpochMilliseconds(reminder.scheduledAtEpochMillis)
            .toLocalDateTime(timeZone)

        val recurrence = reminder.recurrence

        if (recurrence == null) {
            return reminder.scheduledAtEpochMillis.takeIf { it > fromEpochMillis }
        }

        if (!recurrence.isActive) {
            return null
        }

        var candidateDate = maxDate(reminderDateTime.date, thresholdDateTime.date)

        repeat(MAX_SEARCH_DAYS) {
            if (occursOnDate(reminder, candidateDate, timeZone)) {
                val candidateDateTime = LocalDateTime(
                    year = candidateDate.year,
                    monthNumber = candidateDate.monthNumber,
                    dayOfMonth = candidateDate.dayOfMonth,
                    hour = reminderDateTime.hour,
                    minute = reminderDateTime.minute,
                    second = reminderDateTime.second,
                    nanosecond = reminderDateTime.nanosecond
                )
                val candidateEpochMillis = candidateDateTime.toInstant(timeZone).toEpochMilliseconds()
                if (candidateEpochMillis > fromEpochMillis) {
                    return candidateEpochMillis
                }
            }
            candidateDate = candidateDate.plus(1, DateTimeUnit.DAY)
        }

        return null
    }

    fun resolveNextGlobalSummaryTrigger(
        summaryHour: Int,
        summaryMinute: Int,
        nowEpochMillis: Long,
        timeZoneId: String
    ): Long {
        val timeZone = TimeZone.of(timeZoneId)
        val now = Instant.fromEpochMilliseconds(nowEpochMillis)
            .toLocalDateTime(timeZone)

        var nextSummaryDate = now.date
        var nextSummaryEpochMillis = LocalDateTime(
            year = nextSummaryDate.year,
            monthNumber = nextSummaryDate.monthNumber,
            dayOfMonth = nextSummaryDate.dayOfMonth,
            hour = summaryHour,
            minute = summaryMinute
        ).toInstant(timeZone).toEpochMilliseconds()

        if (nextSummaryEpochMillis <= nowEpochMillis) {
            nextSummaryDate = nextSummaryDate.plus(1, DateTimeUnit.DAY)
            nextSummaryEpochMillis = LocalDateTime(
                year = nextSummaryDate.year,
                monthNumber = nextSummaryDate.monthNumber,
                dayOfMonth = nextSummaryDate.dayOfMonth,
                hour = summaryHour,
                minute = summaryMinute
            ).toInstant(timeZone).toEpochMilliseconds()
        }

        return nextSummaryEpochMillis
    }

    private fun occursOnDate(
        reminder: Reminder,
        date: LocalDate,
        timeZone: TimeZone
    ): Boolean {
        val reminderDate = Instant.fromEpochMilliseconds(reminder.scheduledAtEpochMillis)
            .toLocalDateTime(timeZone)
            .date

        if (date < reminderDate) {
            return false
        }

        val recurrence = reminder.recurrence ?: return date == reminderDate
        if (!recurrence.isActive) {
            return date == reminderDate
        }

        return when (recurrence.unit) {
            ReminderRecurrenceUnit.DAY -> {
                val daysBetween = reminderDate.daysUntil(date)
                daysBetween >= 0 && daysBetween % recurrence.normalizedInterval == 0
            }

            ReminderRecurrenceUnit.WEEK -> matchesWeeklyRecurrence(
                recurrence = recurrence,
                reminderDate = reminderDate,
                date = date
            )

            ReminderRecurrenceUnit.MONTH -> {
                val monthsBetween = (date.year - reminderDate.year) * MONTHS_PER_YEAR +
                    (date.monthNumber - reminderDate.monthNumber)

                monthsBetween >= 0 &&
                    reminderDate.dayOfMonth == date.dayOfMonth &&
                    monthsBetween % recurrence.normalizedInterval == 0
            }

            ReminderRecurrenceUnit.YEAR -> {
                val yearsBetween = date.year - reminderDate.year

                yearsBetween >= 0 &&
                    reminderDate.dayOfMonth == date.dayOfMonth &&
                    reminderDate.monthNumber == date.monthNumber &&
                    yearsBetween % recurrence.normalizedInterval == 0
            }
        }
    }

    private fun matchesWeeklyRecurrence(
        recurrence: ReminderRecurrence,
        reminderDate: LocalDate,
        date: LocalDate
    ): Boolean {
        val reminderWeekStart = startOfWeek(reminderDate)
        val dateWeekStart = startOfWeek(date)
        val weeksBetween = reminderWeekStart.daysUntil(dateWeekStart) / DAYS_PER_WEEK

        if (weeksBetween < 0 || weeksBetween % recurrence.normalizedInterval != 0) {
            return false
        }

        val targetWeekdays = recurrence.weekdays.ifEmpty {
            setOf(ReminderWeekday.fromIsoDayNumber(dayOfWeekToIsoDayNumber(reminderDate.dayOfWeek)))
        }

        return targetWeekdays.any { it.isoDayNumber == dayOfWeekToIsoDayNumber(date.dayOfWeek) }
    }

    private fun startOfWeek(date: LocalDate): LocalDate {
        val offset = dayOfWeekToIsoDayNumber(date.dayOfWeek) - 1
        return date.plus(-offset, DateTimeUnit.DAY)
    }

    private fun dayOfWeekToIsoDayNumber(dayOfWeek: DayOfWeek): Int = when (dayOfWeek) {
        DayOfWeek.MONDAY -> 1
        DayOfWeek.TUESDAY -> 2
        DayOfWeek.WEDNESDAY -> 3
        DayOfWeek.THURSDAY -> 4
        DayOfWeek.FRIDAY -> 5
        DayOfWeek.SATURDAY -> 6
        DayOfWeek.SUNDAY -> 7
    }

    private fun maxDate(first: LocalDate, second: LocalDate): LocalDate {
        return if (first >= second) first else second
    }

    const val DEFAULT_NEXT_DAY_SUMMARY_HOUR = 20
    const val DEFAULT_NEXT_DAY_SUMMARY_MINUTE = 0

    private const val DAYS_PER_WEEK = 7
    private const val MONTHS_PER_YEAR = 12
    private const val MAX_SEARCH_DAYS = 3660
}
