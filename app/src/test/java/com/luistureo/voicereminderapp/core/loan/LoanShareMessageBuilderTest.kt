package com.luistureo.voicereminderapp.core.loan

import com.luistureo.voicereminderapp.domain.loan.model.Loan
import com.luistureo.voicereminderapp.domain.loan.model.LoanType
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LoanShareMessageBuilderTest {

    private val zoneId = ZoneId.systemDefault()

    @Test
    fun buildsEditableReminderForMoneyLentToMe() {
        val message = LoanShareMessageBuilder.build(loan(LoanType.MONEY_LENT_TO_ME), zoneId)

        assertTrue(message.contains("te recuerdo"))
        assertTrue(message.contains("$50.000"))
        assertTrue(message.contains("bicicleta"))
    }

    @Test
    fun buildsDifferentMessageForMoneyIOwe() {
        val message = LoanShareMessageBuilder.build(loan(LoanType.MONEY_I_OWE), zoneId)

        assertTrue(message.contains("tengo pendiente devolverte"))
        assertTrue(message.contains("$50.000"))
    }

    private fun loan(type: LoanType) = Loan(
        type = type,
        personName = "Ana",
        principalAmountClp = 50_000L,
        loanDateEpochMillis = date("2026-01-01"),
        dueDateEpochMillis = date("2026-01-15"),
        reason = "bicicleta",
        totalExpectedAmountClp = 50_000L,
        remainingAmountClp = 50_000L
    )

    private fun date(value: String): Long {
        return LocalDate.parse(value).atStartOfDay(zoneId).toInstant().toEpochMilli()
    }
}
