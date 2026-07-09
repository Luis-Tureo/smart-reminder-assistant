package com.luistureo.voicereminderapp.domain.loan.usecase

import com.luistureo.voicereminderapp.domain.loan.repository.LoanRepository

class MarkLoanFullyPaidUseCase(
    private val repository: LoanRepository
) {
    suspend operator fun invoke(loanId: Int) = repository.markLoanFullyPaid(loanId)
}
