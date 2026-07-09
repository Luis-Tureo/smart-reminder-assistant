package com.luistureo.voicereminderapp.domain.loan.model

enum class LoanSort(val label: String) {
    DUE_DATE("Vencimiento"),
    NEWEST("Mas recientes"),
    HIGHEST_AMOUNT("Mayor monto"),
    REMAINING_AMOUNT("Saldo pendiente")
}
