package com.luistureo.voicereminderapp.core.loan

import com.luistureo.voicereminderapp.domain.loan.model.Loan
import com.luistureo.voicereminderapp.domain.loan.model.LoanReminderKind
import com.luistureo.voicereminderapp.domain.loan.model.LoanStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object LoanReminderPolicy {
    fun shouldSchedule(loan: Loan): Boolean {
        return loan.status != LoanStatus.PAID &&
                loan.status != LoanStatus.CANCELED &&
                loan.remainingAmountClp > 0L
    }

    fun resolveReminderTimes(
        loan: Loan,
        nowEpochMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Map<LoanReminderKind, Long> {
        if (!shouldSchedule(loan)) return emptyMap()

        val dueDate = Instant.ofEpochMilli(loan.dueDateEpochMillis)
            .atZone(zoneId)
            .toLocalDate()
        val now = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId).toLocalDateTime()
        val result = linkedMapOf<LoanReminderKind, Long>()

        fun addFuture(kind: LoanReminderKind, date: LocalDate) {
            val trigger = date.atTime(DEFAULT_REMINDER_TIME)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
            if (trigger > nowEpochMillis) result[kind] = trigger
        }

        if (loan.reminderThreeDaysBefore) addFuture(LoanReminderKind.THREE_DAYS_BEFORE, dueDate.minusDays(3))
        if (loan.reminderOneDayBefore) addFuture(LoanReminderKind.ONE_DAY_BEFORE, dueDate.minusDays(1))
        if (loan.reminderSameDay) addFuture(LoanReminderKind.SAME_DAY, dueDate)
        loan.customReminderAtEpochMillis
            ?.takeIf { it > nowEpochMillis }
            ?.let { result[LoanReminderKind.CUSTOM] = it }

        val repeatEveryDays = loan.repeatAfterDueEveryDays
        if (repeatEveryDays != null && now.toLocalDate().isAfter(dueDate)) {
            var repeatDate = dueDate.plusDays(repeatEveryDays.toLong())
            while (!repeatDate.atTime(DEFAULT_REMINDER_TIME).isAfter(now)) {
                repeatDate = repeatDate.plusDays(repeatEveryDays.toLong())
            }
            result[LoanReminderKind.REPEAT_AFTER_DUE] = repeatDate
                .atTime(DEFAULT_REMINDER_TIME)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
        }

        return result
    }

    private val DEFAULT_REMINDER_TIME: LocalTime = LocalTime.of(9, 0)
}
