package com.luistureo.voicereminderapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "loan_payments",
    foreignKeys = [
        ForeignKey(
            entity = LoanEntity::class,
            parentColumns = ["id"],
            childColumns = ["loanId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("loanId")]
)
data class LoanPaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val loanId: Int,
    val paidAmountClp: Long,
    val paymentDateEpochMillis: Long,
    val note: String?,
    val attachmentUri: String?,
    val createdAtEpochMillis: Long
)
