package com.luistureo.voicereminderapp.core.loan

import com.luistureo.voicereminderapp.domain.loan.model.InstallmentStatus
import com.luistureo.voicereminderapp.domain.loan.model.Loan
import com.luistureo.voicereminderapp.domain.loan.model.LoanDraft
import com.luistureo.voicereminderapp.domain.loan.model.LoanInstallment
import com.luistureo.voicereminderapp.domain.loan.model.LoanPayment
import com.luistureo.voicereminderapp.domain.loan.model.LoanPaymentMode
import com.luistureo.voicereminderapp.domain.loan.model.LoanSummary
import com.luistureo.voicereminderapp.domain.loan.model.LoanType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

object LoanCalculator {
    val interestPresets: List<Double> = listOf(0.0, 1.0, 2.0)

    fun calculateTotalExpectedAmount(
        principalAmountClp: Long,
        loanDateEpochMillis: Long,
        dueDateEpochMillis: Long,
        interestEnabled: Boolean,
        monthlyInterestPercentage: Double,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        require(principalAmountClp >= 0L) { "El monto principal no puede ser negativo." }
        require(monthlyInterestPercentage.isFinite() && monthlyInterestPercentage >= 0.0) {
            "El porcentaje de interes no es valido."
        }
        if (!interestEnabled || monthlyInterestPercentage <= 0.0) {
            return principalAmountClp
        }

        val loanDate = toDate(loanDateEpochMillis, zoneId)
        val dueDate = toDate(dueDateEpochMillis, zoneId)
        val days = ChronoUnit.DAYS.between(loanDate, dueDate).coerceAtLeast(0L)
        val interest = BigDecimal.valueOf(principalAmountClp)
            .multiply(BigDecimal.valueOf(monthlyInterestPercentage))
            .multiply(BigDecimal.valueOf(days))
            .divide(BigDecimal.valueOf(3_000L), 0, RoundingMode.HALF_UP)
        return BigDecimal.valueOf(principalAmountClp)
            .add(interest)
            .longValueExact()
    }

    fun remainingAmount(totalExpectedAmountClp: Long, payments: List<LoanPayment>): Long {
        return (totalExpectedAmountClp - payments.sumOf { it.paidAmountClp }).coerceAtLeast(0L)
    }

    fun buildLoan(
        draft: LoanDraft,
        existingLoan: Loan? = null,
        payments: List<LoanPayment> = existingLoan?.payments.orEmpty(),
        nowEpochMillis: Long = System.currentTimeMillis()
    ): Loan {
        val totalExpected = calculateTotalExpectedAmount(
            principalAmountClp = draft.principalAmountClp,
            loanDateEpochMillis = draft.loanDateEpochMillis,
            dueDateEpochMillis = draft.dueDateEpochMillis,
            interestEnabled = draft.interestEnabled,
            monthlyInterestPercentage = draft.interestPercentage
        )
        require(payments.sumOf { it.paidAmountClp } <= totalExpected) {
            TOTAL_BELOW_PAYMENTS_MESSAGE
        }
        val remaining = remainingAmount(totalExpected, payments)
        val status = LoanStatusResolver.resolve(
            dueDateEpochMillis = draft.dueDateEpochMillis,
            totalExpectedAmountClp = totalExpected,
            remainingAmountClp = remaining
        )

        return Loan(
            id = draft.id,
            type = draft.type,
            personName = draft.personName.trim(),
            phoneOrContact = draft.phoneOrContact?.trim()?.takeIf { it.isNotBlank() },
            principalAmountClp = draft.principalAmountClp,
            loanDateEpochMillis = draft.loanDateEpochMillis,
            dueDateEpochMillis = draft.dueDateEpochMillis,
            reason = draft.reason.trim(),
            attachmentUri = draft.attachmentUri?.takeIf { it.isNotBlank() },
            paymentMode = draft.paymentMode,
            installmentCount = if (draft.paymentMode == LoanPaymentMode.INSTALLMENTS) {
                draft.installmentCount.coerceAtLeast(1)
            } else {
                0
            },
            interestEnabled = draft.interestEnabled,
            interestPercentage = if (draft.interestEnabled) draft.interestPercentage else 0.0,
            totalExpectedAmountClp = totalExpected,
            remainingAmountClp = remaining,
            notes = draft.notes?.trim()?.takeIf { it.isNotBlank() },
            createdAtEpochMillis = existingLoan?.createdAtEpochMillis ?: nowEpochMillis,
            updatedAtEpochMillis = nowEpochMillis,
            status = status,
            reminderSameDay = draft.reminderSameDay,
            reminderOneDayBefore = draft.reminderOneDayBefore,
            reminderThreeDaysBefore = draft.reminderThreeDaysBefore,
            customReminderAtEpochMillis = draft.customReminderAtEpochMillis,
            repeatAfterDueEveryDays = draft.repeatAfterDueEveryDays?.takeIf { it > 0 },
            payments = payments
        )
    }

    fun buildInstallments(loan: Loan, zoneId: ZoneId = ZoneId.systemDefault()): List<LoanInstallment> {
        if (loan.paymentMode != LoanPaymentMode.INSTALLMENTS || loan.installmentCount <= 0) {
            return emptyList()
        }

        val count = loan.installmentCount
        val baseAmount = loan.totalExpectedAmountClp / count
        val remainder = loan.totalExpectedAmountClp % count
        val startDate = toDate(loan.loanDateEpochMillis, zoneId)
        val endDate = toDate(loan.dueDateEpochMillis, zoneId)
        val totalDays = ChronoUnit.DAYS.between(startDate, endDate).coerceAtLeast(count.toLong())
        val stepDays = ceil(totalDays.toDouble() / count.toDouble()).toLong().coerceAtLeast(1L)

        return (1..count).map { index ->
            val expected = baseAmount + if (index == count) remainder else 0L
            val dueDate = if (index == count) endDate else startDate.plusDays(stepDays * index)
            LoanInstallment(
                installmentNumber = index,
                dueDateEpochMillis = dueDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
                expectedAmountClp = expected
            )
        }
    }

    fun allocatePaymentsToInstallments(
        installments: List<LoanInstallment>,
        payments: List<LoanPayment>,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<LoanInstallment> {
        var remainingPaid = payments.sumOf { it.paidAmountClp }

        return installments.sortedBy { it.installmentNumber }.map { installment ->
            val paidForInstallment = remainingPaid.coerceAtMost(installment.expectedAmountClp)
            remainingPaid = (remainingPaid - paidForInstallment).coerceAtLeast(0L)

            val dueDate = toDate(installment.dueDateEpochMillis, zoneId)
            val status = when {
                paidForInstallment >= installment.expectedAmountClp -> InstallmentStatus.PAID
                paidForInstallment > 0L -> InstallmentStatus.PARTIALLY_PAID
                today.isAfter(dueDate) -> InstallmentStatus.OVERDUE
                else -> InstallmentStatus.PENDING
            }

            installment.copy(
                paidAmountClp = paidForInstallment,
                status = status
            )
        }
    }

    fun summary(loans: List<Loan>): LoanSummary {
        val activeLoans = loans.filter { it.status != com.luistureo.voicereminderapp.domain.loan.model.LoanStatus.CANCELED }
        return LoanSummary(
            totalLentToMeClp = activeLoans
                .filter { it.type == LoanType.MONEY_LENT_TO_ME }
                .sumOf { it.remainingAmountClp },
            totalIOweClp = activeLoans
                .filter { it.type == LoanType.MONEY_I_OWE }
                .sumOf { it.remainingAmountClp },
            totalRecoveredClp = activeLoans
                .filter { it.type == LoanType.MONEY_LENT_TO_ME }
                .sumOf { it.paidAmountClp },
            totalPendingClp = activeLoans.sumOf { it.remainingAmountClp },
            overdueCount = activeLoans.count {
                it.status == com.luistureo.voicereminderapp.domain.loan.model.LoanStatus.OVERDUE
            }
        )
    }

    private fun toDate(epochMillis: Long, zoneId: ZoneId): LocalDate {
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
    }

    const val TOTAL_BELOW_PAYMENTS_MESSAGE =
        "El total actualizado no puede ser menor que los pagos ya registrados."
}
