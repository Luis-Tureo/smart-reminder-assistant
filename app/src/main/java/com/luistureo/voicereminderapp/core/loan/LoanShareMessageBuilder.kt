package com.luistureo.voicereminderapp.core.loan

import com.luistureo.voicereminderapp.domain.loan.model.Loan
import com.luistureo.voicereminderapp.domain.loan.model.LoanType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object LoanShareMessageBuilder {
    private val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun build(loan: Loan, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val amount = ClpFormatter.format(loan.remainingAmountClp)
        return when (loan.type) {
            LoanType.MONEY_LENT_TO_ME -> {
                val dueDate = Instant.ofEpochMilli(loan.dueDateEpochMillis)
                    .atZone(zoneId)
                    .toLocalDate()
                    .format(formatter)
                "Hola, te recuerdo que esta pendiente la devolucion de $amount por ${loan.reason}. La fecha acordada era $dueDate."
            }

            LoanType.MONEY_I_OWE -> {
                "Hola, te aviso que tengo pendiente devolverte $amount por ${loan.reason}."
            }
        }
    }
}
