package com.luistureo.voicereminderapp.data.repository

import com.luistureo.voicereminderapp.core.loan.LoanCalculator
import com.luistureo.voicereminderapp.core.loan.LoanStatusResolver
import com.luistureo.voicereminderapp.data.local.dao.LoanDao
import com.luistureo.voicereminderapp.data.mapper.toDomain
import com.luistureo.voicereminderapp.data.mapper.toEntity
import com.luistureo.voicereminderapp.domain.loan.model.Loan
import com.luistureo.voicereminderapp.domain.loan.model.LoanDraft
import com.luistureo.voicereminderapp.domain.loan.model.LoanPayment
import com.luistureo.voicereminderapp.domain.loan.model.LoanStatus
import com.luistureo.voicereminderapp.domain.loan.repository.LoanRepository

class LoanRepositoryImpl(
    private val loanDao: LoanDao
) : LoanRepository {

    override suspend fun getLoans(): List<Loan> {
        return loanDao.getLoans().map { entity ->
            val payments = loanDao.getPaymentsForLoan(entity.id).map { it.toDomain() }
            val installments = loanDao.getInstallmentsForLoan(entity.id).map { it.toDomain() }
            refreshCalculatedState(entity.toDomain(payments, installments))
        }
    }

    override suspend fun getLoanById(loanId: Int): Loan? {
        val entity = loanDao.getLoanById(loanId) ?: return null
        val payments = loanDao.getPaymentsForLoan(loanId).map { it.toDomain() }
        val installments = loanDao.getInstallmentsForLoan(loanId).map { it.toDomain() }
        return refreshCalculatedState(entity.toDomain(payments, installments))
    }

    override suspend fun saveLoan(draft: LoanDraft): Loan {
        val existingLoan = draft.id.takeIf { it > 0 }?.let { getLoanById(it) }
        val loan = LoanCalculator.buildLoan(draft, existingLoan)
        val savedId = if (loan.id == 0) {
            loanDao.insertLoan(loan.toEntity()).toInt()
        } else {
            loanDao.updateLoan(loan.toEntity())
            loan.id
        }

        val savedLoan = requireNotNull(getLoanById(savedId))
        rebuildInstallments(savedLoan)
        return requireNotNull(getLoanById(savedId))
    }

    override suspend fun addPayment(loanId: Int, payment: LoanPayment): Loan? {
        val loan = getLoanById(loanId) ?: return null
        val amount = payment.paidAmountClp.coerceAtMost(loan.remainingAmountClp)
        if (amount <= 0L) return loan

        loanDao.insertPayment(
            payment.copy(
                loanId = loanId,
                paidAmountClp = amount
            ).toEntity(loanId)
        )

        return recalculateLoan(loanId)
    }

    override suspend fun markLoanFullyPaid(loanId: Int): Loan? {
        val loan = getLoanById(loanId) ?: return null
        if (loan.remainingAmountClp <= 0L) return loan

        loanDao.insertPayment(
            LoanPayment(
                loanId = loanId,
                paidAmountClp = loan.remainingAmountClp,
                paymentDateEpochMillis = System.currentTimeMillis(),
                note = "Pago completo"
            ).toEntity(loanId)
        )

        return recalculateLoan(loanId)
    }

    override suspend fun deleteLoan(loanId: Int) {
        loanDao.getLoanById(loanId)?.let { entity ->
            loanDao.deleteLoan(entity)
        }
    }

    private suspend fun recalculateLoan(loanId: Int): Loan? {
        val loan = getLoanById(loanId) ?: return null
        val remaining = LoanCalculator.remainingAmount(loan.totalExpectedAmountClp, loan.payments)
        val status = LoanStatusResolver.resolve(
            dueDateEpochMillis = loan.dueDateEpochMillis,
            totalExpectedAmountClp = loan.totalExpectedAmountClp,
            remainingAmountClp = remaining,
            canceled = loan.status == LoanStatus.CANCELED
        )
        val updated = loan.copy(
            remainingAmountClp = remaining,
            status = status,
            updatedAtEpochMillis = System.currentTimeMillis()
        )
        loanDao.updateLoan(updated.toEntity())
        rebuildInstallments(updated.copy(status = status, remainingAmountClp = remaining))
        return getLoanById(loanId)
    }

    private suspend fun rebuildInstallments(loan: Loan) {
        loanDao.deleteInstallmentsForLoan(loan.id)
        val installments = LoanCalculator.allocatePaymentsToInstallments(
            installments = LoanCalculator.buildInstallments(loan),
            payments = loan.payments
        ).map { it.toEntity(loan.id) }
        if (installments.isNotEmpty()) {
            loanDao.insertInstallments(installments)
        }
    }

    private fun refreshCalculatedState(loan: Loan): Loan {
        val remaining = LoanCalculator.remainingAmount(loan.totalExpectedAmountClp, loan.payments)
        val status = LoanStatusResolver.resolve(
            dueDateEpochMillis = loan.dueDateEpochMillis,
            totalExpectedAmountClp = loan.totalExpectedAmountClp,
            remainingAmountClp = remaining,
            canceled = loan.status == LoanStatus.CANCELED
        )
        val installments = LoanCalculator.allocatePaymentsToInstallments(
            installments = loan.installments,
            payments = loan.payments
        )
        return loan.copy(
            remainingAmountClp = remaining,
            status = status,
            installments = installments
        )
    }
}
