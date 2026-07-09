package com.luistureo.voicereminderapp.domain.loan.model

enum class InstallmentStatus(val label: String) {
    PENDING("Pendiente"),
    PARTIALLY_PAID("Pago parcial"),
    PAID("Pagada"),
    OVERDUE("Vencida")
}
