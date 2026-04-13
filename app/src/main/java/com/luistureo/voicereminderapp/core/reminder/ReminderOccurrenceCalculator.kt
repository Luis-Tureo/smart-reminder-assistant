package com.luistureo.voicereminderapp.core.reminder

import com.luistureo.voicereminderapp.domain.model.Reminder
import java.time.LocalDate
import java.time.ZoneId

class ReminderOccurrenceCalculator(
    private val timeZoneId: String = ZoneId.systemDefault().id
) {
    // Mantiene compatibilidad mientras existan llamadas con ZoneId.
    constructor(zoneId: ZoneId) : this(timeZoneId = zoneId.id)

    fun resolveNextTriggerAtEpochMillis(
        reminder: Reminder,
        fromEpochMillis: Long = System.currentTimeMillis()
    ): Long? {
        return ReminderOccurrenceCalculatorCore.resolveNextTriggerAtEpochMillis(
            reminder = reminder,
            fromEpochMillis = fromEpochMillis,
            timeZoneId = timeZoneId
        )
    }

    fun occursOnDate(
        reminder: Reminder,
        date: LocalDate
    ): Boolean {
        return ReminderOccurrenceCalculatorCore.occursOnDate(
            reminder = reminder,
            year = date.year,
            monthNumber = date.monthValue,
            dayOfMonth = date.dayOfMonth,
            timeZoneId = timeZoneId
        )
    }

    fun resolveOccurrenceAtEpochMillis(
        reminder: Reminder,
        date: LocalDate
    ): Long? {
        return ReminderOccurrenceCalculatorCore.resolveOccurrenceAtEpochMillis(
            reminder = reminder,
            year = date.year,
            monthNumber = date.monthValue,
            dayOfMonth = date.dayOfMonth,
            timeZoneId = timeZoneId
        )
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
            timeZoneId = timeZoneId
        )
    }

    companion object {
        const val DEFAULT_NEXT_DAY_SUMMARY_HOUR = ReminderOccurrenceCalculatorCore.DEFAULT_NEXT_DAY_SUMMARY_HOUR
        const val DEFAULT_NEXT_DAY_SUMMARY_MINUTE = ReminderOccurrenceCalculatorCore.DEFAULT_NEXT_DAY_SUMMARY_MINUTE
    }
}
