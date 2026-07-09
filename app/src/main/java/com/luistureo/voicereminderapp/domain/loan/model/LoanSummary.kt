package com.luistureo.voicereminderapp.domain.loan.model

data class LoanSummary(
    val totalLentToMeClp: Long = 0L,
    val totalIOweClp: Long = 0L,
    val totalRecoveredClp: Long = 0L,
    val totalPendingClp: Long = 0L,
    val overdueCount: Int = 0
)
