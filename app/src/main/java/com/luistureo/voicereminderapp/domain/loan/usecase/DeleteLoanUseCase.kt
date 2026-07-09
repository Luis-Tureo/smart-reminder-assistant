package com.luistureo.voicereminderapp.domain.loan.usecase

import com.luistureo.voicereminderapp.domain.loan.repository.LoanRepository

class DeleteLoanUseCase(
    private val repository: LoanRepository
) {
    suspend operator fun invoke(loanId: Int) = repository.deleteLoan(loanId)
}
