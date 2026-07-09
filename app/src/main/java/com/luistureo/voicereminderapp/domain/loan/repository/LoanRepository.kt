package com.luistureo.voicereminderapp.domain.loan.repository

import com.luistureo.voicereminderapp.domain.loan.model.Loan
import com.luistureo.voicereminderapp.domain.loan.model.LoanDraft
import com.luistureo.voicereminderapp.domain.loan.model.LoanPayment

interface LoanRepository {
    suspend fun getLoans(): List<Loan>
    suspend fun getLoanById(loanId: Int): Loan?
    suspend fun saveLoan(draft: LoanDraft): Loan
    suspend fun addPayment(loanId: Int, payment: LoanPayment): Loan?
    suspend fun markLoanFullyPaid(loanId: Int): Loan?
    suspend fun deleteLoan(loanId: Int)
}
