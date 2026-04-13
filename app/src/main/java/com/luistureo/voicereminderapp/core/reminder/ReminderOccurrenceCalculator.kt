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
        return ReminderOccurrenceCalculatorCore.resolveNextTriggerAtEpochMillis(
            reminder = reminder,
            fromEpochMillis = fromEpochMillis,
            timeZoneId = zoneId.id
        )
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

    fun resolveNextGlobalSummaryTrigger(
        summaryHour: Int = DEFAULT_NEXT_DAY_SUMMARY_HOUR,
        summaryMinute: Int = DEFAULT_NEXT_DAY_SUMMARY_MINUTE,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): Long {
        return ReminderOccurrenceCalculatorCore.resolveNextGlobalSummaryTrigger(
            summaryHour = summaryHour,
            summaryMinute = summaryMinute,
            nowEpochMillis = nowEpochMillis,
            timeZoneId = zoneId.id
        )
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
            setOf(ReminderWeekday.fromIsoDayNumber(reminderDate.dayOfWeek.value))
        }

        return targetWeekdays.any { it.isoDayNumber == date.dayOfWeek.value }
    }

    companion object {
        const val DEFAULT_NEXT_DAY_SUMMARY_HOUR = ReminderOccurrenceCalculatorCore.DEFAULT_NEXT_DAY_SUMMARY_HOUR
        const val DEFAULT_NEXT_DAY_SUMMARY_MINUTE = ReminderOccurrenceCalculatorCore.DEFAULT_NEXT_DAY_SUMMARY_MINUTE
    }
}
