package com.luistureo.voicereminderapp.core.utils

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter as JavaDateTimeFormatter

object DateTimeFormatter {

    private val zoneId: ZoneId = ZoneId.systemDefault()
    private val storedDateFormatter = JavaDateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val storedTimeFormatter = JavaDateTimeFormatter.ofPattern("HH:mm")

    // Mantiene adaptadores JVM mientras existan consumidores Android de java.time.
    fun toLocalTime(epochMillis: Long): LocalTime {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(zoneId)
            .toLocalTime()
    }

    fun parseDate(value: String): LocalDate? {
        return runCatching {
            LocalDate.parse(value, storedDateFormatter)
        }.getOrNull()
    }

    fun parseTime(value: String): LocalTime? {
        return runCatching {
            LocalTime.parse(value, storedTimeFormatter)
        }.getOrNull()
    }
}
