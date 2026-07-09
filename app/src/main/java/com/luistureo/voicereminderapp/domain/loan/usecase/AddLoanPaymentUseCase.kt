package com.luistureo.voicereminderapp.domain.loan.usecase

import com.luistureo.voicereminderapp.domain.loan.model.LoanPayment
import com.luistureo.voicereminderapp.domain.loan.repository.LoanRepository

class AddLoanPaymentUseCase(
    private val repository: LoanRepository
) {
    suspend operator fun invoke(loanId: Int, payment: LoanPayment) =
        repository.addPayment(loanId, payment)
}
