package com.luistureo.voicereminderapp.presentation.loan.state

import com.luistureo.voicereminderapp.domain.loan.model.Loan
import com.luistureo.voicereminderapp.domain.loan.model.LoanFilter
import com.luistureo.voicereminderapp.domain.loan.model.LoanSort
import com.luistureo.voicereminderapp.domain.loan.model.LoanSummary

data class LoanUiState(
    val loans: List<Loan> = emptyList(),
    val visibleLoans: List<Loan> = emptyList(),
    val selectedLoan: Loan? = null,
    val summary: LoanSummary = LoanSummary(),
    val query: String = "",
    val filter: LoanFilter = LoanFilter.ALL,
    val sort: LoanSort = LoanSort.DUE_DATE,
    val isLoading: Boolean = false,
    val message: String? = null
)
