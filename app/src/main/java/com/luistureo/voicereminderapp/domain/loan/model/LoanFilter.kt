package com.luistureo.voicereminderapp.domain.loan.model

enum class LoanFilter(val label: String) {
    ALL("Todos"),
    LENT_TO_ME("Me deben"),
    I_OWE("Yo debo"),
    PENDING("Pendientes"),
    PARTIALLY_PAID("Pagados parcial"),
    PAID("Pagados"),
    OVERDUE("Vencidos")
}
