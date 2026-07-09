package com.luistureo.voicereminderapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "loan_records")
data class LoanEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val type: String,
    val personName: String,
    val phoneOrContact: String?,
    val principalAmountClp: Long,
    val loanDateEpochMillis: Long,
    val dueDateEpochMillis: Long,
    val reason: String,
    val attachmentUri: String?,
    val paymentMode: String,
    val installmentCount: Int,
    val interestEnabled: Boolean,
    val interestPercentage: Double,
    val interestMode: String = "SIMPLE",
    val interestPeriod: String = "MONTHLY",
    val totalExpectedAmountClp: Long,
    val remainingAmountClp: Long,
    val notes: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val status: String,
    val reminderSameDay: Boolean,
    val reminderOneDayBefore: Boolean,
    val reminderThreeDaysBefore: Boolean,
    val customReminderAtEpochMillis: Long?,
    val repeatAfterDueEveryDays: Int?
)
