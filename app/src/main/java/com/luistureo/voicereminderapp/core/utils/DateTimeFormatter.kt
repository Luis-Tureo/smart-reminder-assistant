package com.luistureo.voicereminderapp.core.utils

import java.util.Calendar

object DateTimeFormatter {

    // Formatea la fecha en formato dd/MM/yyyy
    fun formatDate(day: Int, month: Int, year: Int): String {
        val monthNumber = month + 1
        return String.format("%02d/%02d/%04d", day, monthNumber, year)
    }

    // Formatea la hora en formato HH:mm
    fun formatTime(hour: Int, minute: Int): String {
        return String.format("%02d:%02d", hour, minute)
    }

    // Construye el tiempo exacto en milisegundos para programar la alarma
    fun buildTriggerTimeMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    // Verifica si la fecha es válida
    fun hasValidDate(year: Int, month: Int, day: Int): Boolean {
        return year != -1 && month != -1 && day != -1
    }

    // Verifica si la hora es válida
    fun hasValidTime(hour: Int, minute: Int): Boolean {
        return hour != -1 && minute != -1
    }
}