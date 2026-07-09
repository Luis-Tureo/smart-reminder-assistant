package com.luistureo.voicereminderapp.domain.loan.model

data class Loan(
    val id: Int = 0,
    val type: LoanType,
    val personName: String,
    val phoneOrContact: String? = null,
    val principalAmountClp: Long,
    val loanDateEpochMillis: Long,
    val dueDateEpochMillis: Long,
    val reason: String,
    val attachmentUri: String? = null,
    val paymentMode: LoanPaymentMode = LoanPaymentMode.SINGLE,
    val installmentCount: Int = 0,
    val interestEnabled: Boolean = false,
    val interestPercentage: Double = 0.0,
    val totalExpectedAmountClp: Long,
    val remainingAmountClp: Long,
    val notes: String? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val updatedAtEpochMillis: Long = System.currentTimeMillis(),
    val status: LoanStatus = LoanStatus.PENDING,
    val reminderSameDay: Boolean = false,
    val reminderOneDayBefore: Boolean = false,
    val reminderThreeDaysBefore: Boolean = false,
    val customReminderAtEpochMillis: Long? = null,
    val repeatAfterDueEveryDays: Int? = null,
    val payments: List<LoanPayment> = emptyList(),
    val installments: List<LoanInstallment> = emptyList()
) {
    val paidAmountClp: Long
        get() = payments.sumOf { it.paidAmountClp }

    val hasAttachment: Boolean
        get() = !attachmentUri.isNullOrBlank()
}
