package com.luistureo.voicereminderapp.core.loan

import com.luistureo.voicereminderapp.domain.loan.model.Loan
import com.luistureo.voicereminderapp.domain.loan.model.LoanFilter
import com.luistureo.voicereminderapp.domain.loan.model.LoanSort
import com.luistureo.voicereminderapp.domain.loan.model.LoanStatus
import com.luistureo.voicereminderapp.domain.loan.model.LoanType

object LoanFilterSorter {
    fun apply(
        loans: List<Loan>,
        query: String,
        filter: LoanFilter,
        sort: LoanSort
    ): List<Loan> {
        val normalizedQuery = query.trim().lowercase()
        return loans
            .filter { loan -> matchesQuery(loan, normalizedQuery) }
            .filter { loan -> matchesFilter(loan, filter) }
            .let { filtered -> sort(filtered, sort) }
    }

    private fun matchesQuery(loan: Loan, query: String): Boolean {
        if (query.isBlank()) return true
        val searchable = listOf(
            loan.personName,
            loan.phoneOrContact.orEmpty(),
            loan.reason,
            loan.notes.orEmpty()
        ).joinToString(" ").lowercase()
        return searchable.contains(query)
    }

    private fun matchesFilter(loan: Loan, filter: LoanFilter): Boolean {
        return when (filter) {
            LoanFilter.ALL -> true
            LoanFilter.LENT_TO_ME -> loan.type == LoanType.MONEY_LENT_TO_ME
            LoanFilter.I_OWE -> loan.type == LoanType.MONEY_I_OWE
            LoanFilter.PENDING -> loan.status == LoanStatus.PENDING
            LoanFilter.PARTIALLY_PAID -> loan.status == LoanStatus.PARTIALLY_PAID
            LoanFilter.PAID -> loan.status == LoanStatus.PAID
            LoanFilter.OVERDUE -> loan.status == LoanStatus.OVERDUE
        }
    }

    private fun sort(loans: List<Loan>, sort: LoanSort): List<Loan> {
        return when (sort) {
            LoanSort.DUE_DATE -> loans.sortedWith(compareBy<Loan> { it.dueDateEpochMillis }.thenByDescending { it.id })
            LoanSort.NEWEST -> loans.sortedWith(compareByDescending<Loan> { it.createdAtEpochMillis }.thenByDescending { it.id })
            LoanSort.HIGHEST_AMOUNT -> loans.sortedByDescending { it.totalExpectedAmountClp }
            LoanSort.REMAINING_AMOUNT -> loans.sortedByDescending { it.remainingAmountClp }
        }
    }
}
