package com.luistureo.voicereminderapp.domain.model

// Modelo limpio de negocio
data class Reminder(
    val id: Int = 0,
    val title: String,
    val detail: String,
    val date: String,
    val time: String,
    val isCompleted: Boolean = false,
    val type: ReminderType = ReminderType.DEFAULT
)
