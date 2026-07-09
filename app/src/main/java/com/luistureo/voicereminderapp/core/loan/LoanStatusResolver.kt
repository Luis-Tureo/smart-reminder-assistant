package com.luistureo.voicereminderapp.core.loan

import com.luistureo.voicereminderapp.domain.loan.model.LoanStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object LoanStatusResolver {
    fun resolve(
        dueDateEpochMillis: Long,
        totalExpectedAmountClp: Long,
        remainingAmountClp: Long,
        nowDate: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        canceled: Boolean = false
    ): LoanStatus {
        if (canceled) return LoanStatus.CANCELED
        if (remainingAmountClp <= 0L) return LoanStatus.PAID

        val dueDate = Instant.ofEpochMilli(dueDateEpochMillis)
            .atZone(zoneId)
            .toLocalDate()
        if (nowDate.isAfter(dueDate)) return LoanStatus.OVERDUE

        val paidAmount = (totalExpectedAmountClp - remainingAmountClp).coerceAtLeast(0L)
        return if (paidAmount > 0L) LoanStatus.PARTIALLY_PAID else LoanStatus.PENDING
    }
}
