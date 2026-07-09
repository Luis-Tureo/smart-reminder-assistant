package com.luistureo.voicereminderapp.domain.loan.usecase

import com.luistureo.voicereminderapp.domain.loan.model.LoanDraft
import com.luistureo.voicereminderapp.domain.loan.repository.LoanRepository

class SaveLoanUseCase(
    private val repository: LoanRepository
) {
    suspend operator fun invoke(draft: LoanDraft) = repository.saveLoan(draft)
}
