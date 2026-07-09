package com.luistureo.voicereminderapp.core.loan

import com.luistureo.voicereminderapp.domain.loan.model.Loan
import com.luistureo.voicereminderapp.domain.loan.model.LoanReminderKind
import com.luistureo.voicereminderapp.domain.loan.model.LoanStatus
import com.luistureo.voicereminderapp.domain.loan.model.LoanType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LoanReminderPolicyTest {

    private val zoneId = ZoneId.systemDefault()

    @Test
    fun schedulesSelectedLocalReminderOptionsOnlyWhenUnpaid() {
        val loan = loan(
            status = LoanStatus.PENDING,
            remaining = 30_000L,
            sameDay = true,
            oneDay = true
        )
        val now = LocalDate.of(2026, 1, 1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val reminders = LoanReminderPolicy.resolveReminderTimes(loan, now, zoneId)

        assertTrue(reminders.containsKey(LoanReminderKind.SAME_DAY))
        assertTrue(reminders.containsKey(LoanReminderKind.ONE_DAY_BEFORE))
    }

    @Test
    fun cancelsReminderPolicyWhenLoanIsPaid() {
        val loan = loan(status = LoanStatus.PAID, remaining = 0L, sameDay = true)

        assertFalse(LoanReminderPolicy.shouldSchedule(loan))
        assertTrue(LoanReminderPolicy.resolveReminderTimes(loan).isEmpty())
    }

    @Test
    fun schedulesRepeatAfterDueWhileUnpaid() {
        val loan = loan(
            status = LoanStatus.OVERDUE,
            remaining = 30_000L,
            repeatEveryDays = 3
        )
        val now = LocalDate.of(2026, 1, 10).atStartOfDay(zoneId).toInstant().toEpochMilli()

        assertTrue(
            LoanReminderPolicy.resolveReminderTimes(loan, now, zoneId)
                .containsKey(LoanReminderKind.REPEAT_AFTER_DUE)
        )
    }

    private fun loan(
        status: LoanStatus,
        remaining: Long,
        sameDay: Boolean = false,
        oneDay: Boolean = false,
        repeatEveryDays: Int? = null
    ) = Loan(
        type = LoanType.MONEY_LENT_TO_ME,
        personName = "Ana",
        principalAmountClp = 30_000L,
        loanDateEpochMillis = date("2026-01-01"),
        dueDateEpochMillis = date("2026-01-08"),
        reason = "motivo",
        totalExpectedAmountClp = 30_000L,
        remainingAmountClp = remaining,
        status = status,
        reminderSameDay = sameDay,
        reminderOneDayBefore = oneDay,
        repeatAfterDueEveryDays = repeatEveryDays
    )

    private fun date(value: String): Long {
        return LocalDate.parse(value).atStartOfDay(zoneId).toInstant().toEpochMilli()
    }
}
