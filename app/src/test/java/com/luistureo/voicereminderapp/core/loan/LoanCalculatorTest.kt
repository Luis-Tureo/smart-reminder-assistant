package com.luistureo.voicereminderapp.core.loan

import com.luistureo.voicereminderapp.domain.loan.model.InstallmentStatus
import com.luistureo.voicereminderapp.domain.loan.model.Loan
import com.luistureo.voicereminderapp.domain.loan.model.LoanDraft
import com.luistureo.voicereminderapp.domain.loan.model.LoanPayment
import com.luistureo.voicereminderapp.domain.loan.model.LoanPaymentMode
import com.luistureo.voicereminderapp.domain.loan.model.LoanStatus
import com.luistureo.voicereminderapp.domain.loan.model.LoanType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LoanCalculatorTest {

    private val zoneId = ZoneId.systemDefault()

    @Test
    fun clpFormattingUsesChileanThousandsWithoutDecimals() {
        assertEquals("$1.234.567", ClpFormatter.format(1_234_567L))
        assertEquals(1234567L, ClpFormatter.parse("$1.234.567"))
    }

    @Test
    fun interestIsDisabledByDefaultWhenCreatingLoan() {
        val loan = LoanCalculator.buildLoan(draft())

        assertFalse(loan.interestEnabled)
        assertEquals(100_000L, loan.totalExpectedAmountClp)
        assertEquals(100_000L, loan.remainingAmountClp)
    }

    @Test
    fun simpleMonthlyInterestIsNotCompounded() {
        val total = LoanCalculator.calculateTotalExpectedAmount(
            principalAmountClp = 100_000L,
            loanDateEpochMillis = date("2026-01-01"),
            dueDateEpochMillis = date("2026-04-01"),
            interestEnabled = true,
            monthlyInterestPercentage = 2.0,
            zoneId = zoneId
        )

        assertEquals(106_000L, total)
    }

    @Test
    fun simpleInterestRoundsExactClpOnlyAtTheFinalResult() {
        val total = LoanCalculator.calculateTotalExpectedAmount(
            principalAmountClp = 99_999L,
            loanDateEpochMillis = date("2026-01-01"),
            dueDateEpochMillis = date("2026-01-16"),
            interestEnabled = true,
            monthlyInterestPercentage = 1.25,
            zoneId = zoneId
        )

        assertEquals(100_624L, total)
    }

    @Test
    fun editingCannotReduceExpectedTotalBelowRecordedPayments() {
        val existing = LoanCalculator.buildLoan(draft())
        val paid = LoanPayment(
            paidAmountClp = 80_000L,
            paymentDateEpochMillis = date("2026-02-01")
        )
        val reducedDraft = draft().copy(principalAmountClp = 70_000L)

        val error = assertThrows(IllegalArgumentException::class.java) {
            LoanCalculator.buildLoan(reducedDraft, existing, listOf(paid))
        }

        assertEquals(LoanCalculator.TOTAL_BELOW_PAYMENTS_MESSAGE, error.message)
    }

    @Test
    fun derivedStateUsesPrincipalInterestAndPaymentsInsteadOfStoredCaches() {
        val payment = LoanPayment(
            paidAmountClp = 20_000L,
            paymentDateEpochMillis = date("2026-02-01")
        )
        val stale = LoanCalculator.buildLoan(draft()).copy(
            totalExpectedAmountClp = 1L,
            remainingAmountClp = 1L,
            status = LoanStatus.PAID,
            payments = listOf(payment)
        )

        val reconciled = LoanDerivedStatePolicy.reconcile(
            stale,
            today = LocalDate.of(2026, 2, 1),
            zoneId = zoneId
        )

        assertEquals(100_000L, reconciled.totalExpectedAmountClp)
        assertEquals(80_000L, reconciled.remainingAmountClp)
        assertEquals(LoanStatus.PARTIALLY_PAID, reconciled.status)
    }

    @Test
    fun partialAndFullPaymentsRecalculateRemainingBalance() {
        val payments = listOf(
            LoanPayment(paidAmountClp = 25_000L, paymentDateEpochMillis = date("2026-01-10")),
            LoanPayment(paidAmountClp = 75_000L, paymentDateEpochMillis = date("2026-01-12"))
        )

        assertEquals(75_000L, LoanCalculator.remainingAmount(100_000L, payments.take(1)))
        assertEquals(0L, LoanCalculator.remainingAmount(100_000L, payments))
    }

    @Test
    fun statusCoversPendingPartialPaidAndOverdue() {
        val due = date("2026-07-20")

        assertEquals(
            LoanStatus.PENDING,
            LoanStatusResolver.resolve(due, 100_000L, 100_000L, LocalDate.of(2026, 7, 9), zoneId)
        )
        assertEquals(
            LoanStatus.PARTIALLY_PAID,
            LoanStatusResolver.resolve(due, 100_000L, 60_000L, LocalDate.of(2026, 7, 9), zoneId)
        )
        assertEquals(
            LoanStatus.PAID,
            LoanStatusResolver.resolve(due, 100_000L, 0L, LocalDate.of(2026, 7, 9), zoneId)
        )
        assertEquals(
            LoanStatus.OVERDUE,
            LoanStatusResolver.resolve(due, 100_000L, 60_000L, LocalDate.of(2026, 7, 21), zoneId)
        )
    }

    @Test
    fun installmentModeBuildsInstallmentsAndAllocatesPayments() {
        val loan = LoanCalculator.buildLoan(
            draft(
                paymentMode = LoanPaymentMode.INSTALLMENTS,
                installmentCount = 3
            )
        )
        val installments = LoanCalculator.buildInstallments(loan)
        val allocated = LoanCalculator.allocatePaymentsToInstallments(
            installments,
            listOf(LoanPayment(paidAmountClp = 50_000L, paymentDateEpochMillis = date("2026-01-15"))),
            today = LocalDate.of(2026, 1, 20),
            zoneId = zoneId
        )

        assertEquals(3, installments.size)
        assertEquals(InstallmentStatus.PAID, allocated.first().status)
        assertTrue(allocated[1].paidAmountClp > 0L)
    }

    @Test
    fun summarySeparatesMoneyLentAndMoneyOwed() {
        val loans = listOf(
            loan(type = LoanType.MONEY_LENT_TO_ME, remaining = 80_000L, payments = listOf(20_000L)),
            loan(type = LoanType.MONEY_I_OWE, remaining = 50_000L)
        )
        val summary = LoanCalculator.summary(loans)

        assertEquals(80_000L, summary.totalLentToMeClp)
        assertEquals(50_000L, summary.totalIOweClp)
        assertEquals(20_000L, summary.totalRecoveredClp)
        assertEquals(130_000L, summary.totalPendingClp)
    }

    @Test
    fun attachmentMetadataIsKeptLocalAsUriString() {
        val loan = LoanCalculator.buildLoan(
            draft(attachmentUri = "content://local/receipt.jpg")
        )

        assertTrue(loan.hasAttachment)
        assertEquals("content://local/receipt.jpg", loan.attachmentUri)
    }

    private fun draft(
        paymentMode: LoanPaymentMode = LoanPaymentMode.SINGLE,
        installmentCount: Int = 0,
        attachmentUri: String? = null
    ) = LoanDraft(
        type = LoanType.MONEY_LENT_TO_ME,
        personName = "Ana",
        principalAmountClp = 100_000L,
        loanDateEpochMillis = date("2026-01-01"),
        dueDateEpochMillis = date("2026-04-01"),
        reason = "almuerzo",
        paymentMode = paymentMode,
        installmentCount = installmentCount,
        attachmentUri = attachmentUri
    )

    private fun loan(
        type: LoanType,
        remaining: Long,
        payments: List<Long> = emptyList()
    ) = Loan(
        type = type,
        personName = "Persona",
        principalAmountClp = 100_000L,
        loanDateEpochMillis = date("2026-01-01"),
        dueDateEpochMillis = date("2026-04-01"),
        reason = "motivo",
        totalExpectedAmountClp = 100_000L,
        remainingAmountClp = remaining,
        payments = payments.map {
            LoanPayment(paidAmountClp = it, paymentDateEpochMillis = date("2026-02-01"))
        }
    )

    private fun date(value: String): Long {
        return LocalDate.parse(value).atStartOfDay(zoneId).toInstant().toEpochMilli()
    }
}
