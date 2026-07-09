package com.luistureo.voicereminderapp.domain.loan.model

enum class LoanStatus(val label: String) {
    PENDING("Pendiente"),
    PARTIALLY_PAID("Pagado parcial"),
    PAID("Pagado completo"),
    OVERDUE("Vencido"),
    CANCELED("Cancelado")
}
