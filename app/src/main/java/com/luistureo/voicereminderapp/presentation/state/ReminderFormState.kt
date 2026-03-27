package com.luistureo.voicereminderapp.presentation.state

// Estado del formulario de recordatorio (solo datos, sin textos UI)
data class ReminderFormState(
    val text: String = "",
    val selectedYear: Int = -1,
    val selectedMonth: Int = -1,
    val selectedDay: Int = -1,
    val selectedHour: Int = -1,
    val selectedMinute: Int = -1
)