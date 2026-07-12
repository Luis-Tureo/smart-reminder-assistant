package com.luistureo.voicereminderapp.core.loan

import com.luistureo.voicereminderapp.domain.loan.model.Loan
import com.luistureo.voicereminderapp.domain.loan.model.LoanStatus
import java.time.LocalDate
import java.time.ZoneId

object LoanDerivedStatePolicy {
    fun reconcile(
        loan: Loan,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Loan {
        val totalExpected = LoanCalculator.calculateTotalExpectedAmount(
            principalAmountClp = loan.principalAmountClp,
            loanDateEpochMillis = loan.loanDateEpochMillis,
            dueDateEpochMillis = loan.dueDateEpochMillis,
            interestEnabled = loan.interestEnabled,
            monthlyInterestPercentage = loan.interestPercentage,
            zoneId = zoneId
        )
        val remaining = LoanCalculator.remainingAmount(totalExpected, loan.payments)
        val status = LoanStatusResolver.resolve(
            dueDateEpochMillis = loan.dueDateEpochMillis,
            totalExpectedAmountClp = totalExpected,
            remainingAmountClp = remaining,
            nowDate = today,
            zoneId = zoneId,
            canceled = loan.status == LoanStatus.CANCELED
        )
        val reconciled = loan.copy(
            totalExpectedAmountClp = totalExpected,
            remainingAmountClp = remaining,
            status = status
        )
        val installments = LoanCalculator.allocatePaymentsToInstallments(
            installments = LoanCalculator.buildInstallments(reconciled, zoneId),
            payments = reconciled.payments,
            today = today,
            zoneId = zoneId
        )
        return reconciled.copy(installments = installments)
    }
}
