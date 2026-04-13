package com.luistureo.voicereminderapp.core.utils

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object DateTimeFormatter {

    private val zoneId: ZoneId = ZoneId.systemDefault()

    // Mantiene adaptadores JVM mientras existan consumidores Android de java.time.
    fun toLocalTime(epochMillis: Long): LocalTime {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(zoneId)
            .toLocalTime()
    }

    fun parseDate(value: String): LocalDate? {
        val parsedDate = DateTimeFormatterCore.parseDateParts(value) ?: return null
        return runCatching {
            LocalDate.of(parsedDate.year, parsedDate.month, parsedDate.day)
        }.getOrNull()
    }

    fun parseTime(value: String): LocalTime? {
        val parsedTime = DateTimeFormatterCore.parseTimeParts(value) ?: return null
        return runCatching {
            LocalTime.of(parsedTime.hour, parsedTime.minute)
        }.getOrNull()
    }
}
