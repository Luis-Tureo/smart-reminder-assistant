package com.luistureo.voicereminderapp.domain.loan.usecase

import com.luistureo.voicereminderapp.core.loan.LoanCalculator
import com.luistureo.voicereminderapp.domain.loan.model.Loan
import com.luistureo.voicereminderapp.domain.loan.model.LoanDraft
import com.luistureo.voicereminderapp.domain.loan.model.LoanPayment
import com.luistureo.voicereminderapp.domain.loan.model.LoanStatus
import com.luistureo.voicereminderapp.domain.loan.model.LoanType
import com.luistureo.voicereminderapp.domain.loan.repository.LoanRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LoanUseCaseTest {

    private val repository = FakeLoanRepository()
    private val saveLoan = SaveLoanUseCase(repository)
    private val getLoanById = GetLoanByIdUseCase(repository)
    private val addPayment = AddLoanPaymentUseCase(repository)
    private val markPaid = MarkLoanFullyPaidUseCase(repository)
    private val deleteLoan = DeleteLoanUseCase(repository)

    @Test
    fun createsEditsPaysAndDeletesLoan() = runBlocking {
        val created = saveLoan(draft(person = "Ana", amount = 100_000L))
        assertEquals("Ana", created.personName)

        val edited = saveLoan(draft(id = created.id, person = "Ana Maria", amount = 120_000L))
        assertEquals("Ana Maria", edited.personName)
        assertEquals(120_000L, edited.remainingAmountClp)

        val partial = addPayment(
            edited.id,
            LoanPayment(paidAmountClp = 20_000L, paymentDateEpochMillis = date("2026-01-15"))
        )
        assertEquals(100_000L, partial?.remainingAmountClp)

        val paid = markPaid(edited.id)
        assertEquals(LoanStatus.PAID, paid?.status)
        assertEquals(0L, paid?.remainingAmountClp)

        deleteLoan(edited.id)
        assertNull(getLoanById(edited.id))
    }

    private fun draft(
        id: Int = 0,
        person: String,
        amount: Long
    ) = LoanDraft(
        id = id,
        type = LoanType.MONEY_LENT_TO_ME,
        personName = person,
        principalAmountClp = amount,
        loanDateEpochMillis = date("2026-01-01"),
        dueDateEpochMillis = date("2026-02-01"),
        reason = "motivo"
    )

    private fun date(value: String): Long {
        return LocalDate.parse(value)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private class FakeLoanRepository : LoanRepository {
        private val loans = linkedMapOf<Int, Loan>()
        private var nextId = 1

        override suspend fun getLoans(): List<Loan> = loans.values.toList()

        override suspend fun getLoanById(loanId: Int): Loan? = loans[loanId]

        override suspend fun saveLoan(draft: LoanDraft): Loan {
            val id = draft.id.takeIf { it > 0 } ?: nextId++
            val existing = loans[id]
            val loan = LoanCalculator.buildLoan(
                draft.copy(id = id),
                existingLoan = existing,
                payments = existing?.payments.orEmpty()
            )
            loans[id] = loan
            return loan
        }

        override suspend fun addPayment(loanId: Int, payment: LoanPayment): Loan? {
            val loan = loans[loanId] ?: return null
            val payments = loan.payments + payment.copy(loanId = loanId)
            val remaining = LoanCalculator.remainingAmount(loan.totalExpectedAmountClp, payments)
            val status = if (remaining == 0L) LoanStatus.PAID else LoanStatus.PARTIALLY_PAID
            val updated = loan.copy(
                payments = payments,
                remainingAmountClp = remaining,
                status = status
            )
            loans[loanId] = updated
            return updated
        }

        override suspend fun markLoanFullyPaid(loanId: Int): Loan? {
            val loan = loans[loanId] ?: return null
            return addPayment(
                loanId,
                LoanPayment(
                    paidAmountClp = loan.remainingAmountClp,
                    paymentDateEpochMillis = System.currentTimeMillis()
                )
            )
        }

        override suspend fun deleteLoan(loanId: Int) {
            loans.remove(loanId)
        }
    }
}
