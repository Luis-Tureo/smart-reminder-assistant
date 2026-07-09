package com.luistureo.voicereminderapp.domain.loan.model

data class LoanPayment(
    val id: Int = 0,
    val loanId: Int = 0,
    val paidAmountClp: Long,
    val paymentDateEpochMillis: Long,
    val note: String? = null,
    val attachmentUri: String? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)
