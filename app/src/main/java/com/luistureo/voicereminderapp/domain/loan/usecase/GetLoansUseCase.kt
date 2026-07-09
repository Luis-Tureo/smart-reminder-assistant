package com.luistureo.voicereminderapp.domain.loan.usecase

import com.luistureo.voicereminderapp.domain.loan.repository.LoanRepository

class GetLoansUseCase(
    private val repository: LoanRepository
) {
    suspend operator fun invoke() = repository.getLoans()
}
