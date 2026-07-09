package com.luistureo.voicereminderapp.domain.loan.model

data class LoanInstallment(
    val id: Int = 0,
    val loanId: Int = 0,
    val installmentNumber: Int,
    val dueDateEpochMillis: Long,
    val expectedAmountClp: Long,
    val paidAmountClp: Long = 0L,
    val status: InstallmentStatus = InstallmentStatus.PENDING
)
