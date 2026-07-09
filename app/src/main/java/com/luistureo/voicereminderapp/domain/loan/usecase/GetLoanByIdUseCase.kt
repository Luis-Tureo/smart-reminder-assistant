package com.luistureo.voicereminderapp.domain.loan.usecase

import com.luistureo.voicereminderapp.domain.loan.repository.LoanRepository

class GetLoanByIdUseCase(
    private val repository: LoanRepository
) {
    suspend operator fun invoke(loanId: Int) = repository.getLoanById(loanId)
}
