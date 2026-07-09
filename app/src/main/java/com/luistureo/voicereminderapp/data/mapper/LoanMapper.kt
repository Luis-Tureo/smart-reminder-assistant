package com.luistureo.voicereminderapp.data.mapper

import com.luistureo.voicereminderapp.data.local.entity.LoanEntity
import com.luistureo.voicereminderapp.data.local.entity.LoanInstallmentEntity
import com.luistureo.voicereminderapp.data.local.entity.LoanPaymentEntity
import com.luistureo.voicereminderapp.domain.loan.model.InstallmentStatus
import com.luistureo.voicereminderapp.domain.loan.model.Loan
import com.luistureo.voicereminderapp.domain.loan.model.LoanInstallment
import com.luistureo.voicereminderapp.domain.loan.model.LoanPayment
import com.luistureo.voicereminderapp.domain.loan.model.LoanPaymentMode
import com.luistureo.voicereminderapp.domain.loan.model.LoanStatus
import com.luistureo.voicereminderapp.domain.loan.model.LoanType

fun LoanEntity.toDomain(
    payments: List<LoanPayment> = emptyList(),
    installments: List<LoanInstallment> = emptyList()
): Loan {
    return Loan(
        id = id,
        type = enumValueOrDefault(type, LoanType.MONEY_LENT_TO_ME),
        personName = personName,
        phoneOrContact = phoneOrContact,
        principalAmountClp = principalAmountClp,
        loanDateEpochMillis = loanDateEpochMillis,
        dueDateEpochMillis = dueDateEpochMillis,
        reason = reason,
        attachmentUri = attachmentUri,
        paymentMode = enumValueOrDefault(paymentMode, LoanPaymentMode.SINGLE),
        installmentCount = installmentCount,
        interestEnabled = interestEnabled,
        interestPercentage = interestPercentage,
        totalExpectedAmountClp = totalExpectedAmountClp,
        remainingAmountClp = remainingAmountClp,
        notes = notes,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        status = enumValueOrDefault(status, LoanStatus.PENDING),
        reminderSameDay = reminderSameDay,
        reminderOneDayBefore = reminderOneDayBefore,
        reminderThreeDaysBefore = reminderThreeDaysBefore,
        customReminderAtEpochMillis = customReminderAtEpochMillis,
        repeatAfterDueEveryDays = repeatAfterDueEveryDays,
        payments = payments,
        installments = installments
    )
}

fun Loan.toEntity(): LoanEntity {
    return LoanEntity(
        id = id,
        type = type.name,
        personName = personName,
        phoneOrContact = phoneOrContact,
        principalAmountClp = principalAmountClp,
        loanDateEpochMillis = loanDateEpochMillis,
        dueDateEpochMillis = dueDateEpochMillis,
        reason = reason,
        attachmentUri = attachmentUri,
        paymentMode = paymentMode.name,
        installmentCount = installmentCount,
        interestEnabled = interestEnabled,
        interestPercentage = interestPercentage,
        totalExpectedAmountClp = totalExpectedAmountClp,
        remainingAmountClp = remainingAmountClp,
        notes = notes,
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
        status = status.name,
        reminderSameDay = reminderSameDay,
        reminderOneDayBefore = reminderOneDayBefore,
        reminderThreeDaysBefore = reminderThreeDaysBefore,
        customReminderAtEpochMillis = customReminderAtEpochMillis,
        repeatAfterDueEveryDays = repeatAfterDueEveryDays
    )
}

fun LoanPaymentEntity.toDomain(): LoanPayment {
    return LoanPayment(
        id = id,
        loanId = loanId,
        paidAmountClp = paidAmountClp,
        paymentDateEpochMillis = paymentDateEpochMillis,
        note = note,
        attachmentUri = attachmentUri,
        createdAtEpochMillis = createdAtEpochMillis
    )
}

fun LoanPayment.toEntity(targetLoanId: Int = loanId): LoanPaymentEntity {
    return LoanPaymentEntity(
        id = id,
        loanId = targetLoanId,
        paidAmountClp = paidAmountClp,
        paymentDateEpochMillis = paymentDateEpochMillis,
        note = note,
        attachmentUri = attachmentUri,
        createdAtEpochMillis = createdAtEpochMillis
    )
}

fun LoanInstallmentEntity.toDomain(): LoanInstallment {
    return LoanInstallment(
        id = id,
        loanId = loanId,
        installmentNumber = installmentNumber,
        dueDateEpochMillis = dueDateEpochMillis,
        expectedAmountClp = expectedAmountClp,
        paidAmountClp = paidAmountClp,
        status = enumValueOrDefault(status, InstallmentStatus.PENDING)
    )
}

fun LoanInstallment.toEntity(targetLoanId: Int = loanId): LoanInstallmentEntity {
    return LoanInstallmentEntity(
        id = id,
        loanId = targetLoanId,
        installmentNumber = installmentNumber,
        dueDateEpochMillis = dueDateEpochMillis,
        expectedAmountClp = expectedAmountClp,
        paidAmountClp = paidAmountClp,
        status = status.name
    )
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T {
    return runCatching { enumValueOf<T>(value) }.getOrDefault(default)
}
