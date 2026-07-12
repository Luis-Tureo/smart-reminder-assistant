package com.luistureo.voicereminderapp.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.luistureo.voicereminderapp.data.local.entity.LoanEntity
import com.luistureo.voicereminderapp.data.local.entity.LoanInstallmentEntity
import com.luistureo.voicereminderapp.data.local.entity.LoanPaymentEntity

@Dao
interface LoanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoan(loan: LoanEntity): Long

    @Update
    suspend fun updateLoan(loan: LoanEntity)

    @Delete
    suspend fun deleteLoan(loan: LoanEntity)

    @Query("SELECT * FROM loan_records ORDER BY updatedAtEpochMillis DESC, id DESC")
    suspend fun getLoans(): List<LoanEntity>

    @Query("SELECT * FROM loan_records WHERE id = :loanId LIMIT 1")
    suspend fun getLoanById(loanId: Int): LoanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: LoanPaymentEntity): Long

    @Query("SELECT COALESCE(SUM(paidAmountClp), 0) FROM loan_payments WHERE loanId = :loanId")
    suspend fun getPaidAmountForLoan(loanId: Int): Long

    @Query("SELECT * FROM loan_payments WHERE loanId = :loanId ORDER BY paymentDateEpochMillis DESC, id DESC")
    suspend fun getPaymentsForLoan(loanId: Int): List<LoanPaymentEntity>

    @Query("DELETE FROM loan_payments WHERE loanId = :loanId")
    suspend fun deletePaymentsForLoan(loanId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstallments(installments: List<LoanInstallmentEntity>)

    @Query("SELECT * FROM loan_installments WHERE loanId = :loanId ORDER BY installmentNumber ASC")
    suspend fun getInstallmentsForLoan(loanId: Int): List<LoanInstallmentEntity>

    @Query("DELETE FROM loan_installments WHERE loanId = :loanId")
    suspend fun deleteInstallmentsForLoan(loanId: Int)

    @Transaction
    suspend fun insertPaymentWithinBalance(payment: LoanPaymentEntity): Long? {
        val loan = getLoanById(payment.loanId) ?: return null
        val available = (loan.totalExpectedAmountClp - getPaidAmountForLoan(payment.loanId))
            .coerceAtLeast(0L)
        val acceptedAmount = payment.paidAmountClp.coerceAtMost(available)
        if (acceptedAmount <= 0L) return null
        return insertPayment(payment.copy(paidAmountClp = acceptedAmount))
    }

    @Transaction
    suspend fun repairDerivedState(
        loan: LoanEntity,
        installments: List<LoanInstallmentEntity>
    ) {
        updateLoan(loan)
        deleteInstallmentsForLoan(loan.id)
        if (installments.isNotEmpty()) insertInstallments(installments)
    }
}
