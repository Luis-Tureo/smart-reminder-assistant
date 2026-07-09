package com.luistureo.voicereminderapp.core.loan

import com.luistureo.voicereminderapp.domain.loan.model.Loan
import com.luistureo.voicereminderapp.domain.loan.model.LoanFilter
import com.luistureo.voicereminderapp.domain.loan.model.LoanSort
import com.luistureo.voicereminderapp.domain.loan.model.LoanStatus
import com.luistureo.voicereminderapp.domain.loan.model.LoanType
import org.junit.Assert.assertEquals
import org.junit.Test

class LoanFilterSorterTest {

    @Test
    fun filtersByMoneyLentMoneyOwedAndStatus() {
        val loans = listOf(
            loan("Ana", LoanType.MONEY_LENT_TO_ME, LoanStatus.PENDING, 30_000L),
            loan("Luis", LoanType.MONEY_I_OWE, LoanStatus.OVERDUE, 80_000L),
            loan("Marta", LoanType.MONEY_LENT_TO_ME, LoanStatus.PAID, 0L)
        )

        assertEquals(2, LoanFilterSorter.apply(loans, "", LoanFilter.LENT_TO_ME, LoanSort.DUE_DATE).size)
        assertEquals("Luis", LoanFilterSorter.apply(loans, "", LoanFilter.OVERDUE, LoanSort.DUE_DATE).single().personName)
        assertEquals("Marta", LoanFilterSorter.apply(loans, "mart", LoanFilter.ALL, LoanSort.DUE_DATE).single().personName)
    }

    @Test
    fun sortsByRemainingAmountAndNewest() {
        val loans = listOf(
            loan("A", LoanType.MONEY_LENT_TO_ME, LoanStatus.PENDING, 10_000L, created = 1L),
            loan("B", LoanType.MONEY_I_OWE, LoanStatus.PENDING, 90_000L, created = 2L)
        )

        assertEquals("B", LoanFilterSorter.apply(loans, "", LoanFilter.ALL, LoanSort.REMAINING_AMOUNT).first().personName)
        assertEquals("B", LoanFilterSorter.apply(loans, "", LoanFilter.ALL, LoanSort.NEWEST).first().personName)
    }

    private fun loan(
        person: String,
        type: LoanType,
        status: LoanStatus,
        remaining: Long,
        created: Long = 0L
    ) = Loan(
        type = type,
        personName = person,
        principalAmountClp = 100_000L,
        loanDateEpochMillis = 1L,
        dueDateEpochMillis = 2L,
        reason = "motivo",
        totalExpectedAmountClp = 100_000L,
        remainingAmountClp = remaining,
        createdAtEpochMillis = created,
        status = status
    )
}
