package com.luistureo.voicereminderapp.core.utils

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter as JavaDateTimeFormatter
import java.util.Locale

object DateTimeFormatter {

    private val zoneId: ZoneId = ZoneId.systemDefault()
    private val storedDateFormatter = JavaDateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val storedTimeFormatter = JavaDateTimeFormatter.ofPattern("HH:mm")

    // Formatea la fecha en formato dd/MM/yyyy.
    fun formatDate(day: Int, month: Int, year: Int): String {
        return String.format(Locale.ROOT, "%02d/%02d/%04d", day, month, year)
    }

    // Formatea la hora en formato HH:mm.
    fun formatTime(hour: Int, minute: Int): String {
        return String.format(Locale.ROOT, "%02d:%02d", hour, minute)
    }

    // Convierte la fecha y hora seleccionadas a milisegundos.
    fun buildTriggerTimeMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): Long {
        val dateTime = LocalDateTime.of(year, month, day, hour, minute)
        return dateTime.atZone(zoneId).toInstant().toEpochMilli()
    }

    // Convierte fecha y hora persistidas a un instante absoluto.
    fun parseDateTimeToEpochMillis(
        date: String,
        time: String
    ): Long? {
        return runCatching {
            val parsedDate = LocalDate.parse(date, storedDateFormatter)
            val parsedTime = LocalTime.parse(time, storedTimeFormatter)
            LocalDateTime.of(parsedDate, parsedTime)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

    fun formatDateFromEpoch(epochMillis: Long): String {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(zoneId)
            .toLocalDate()
            .format(storedDateFormatter)
    }

    fun formatTimeFromEpoch(epochMillis: Long): String {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(zoneId)
            .toLocalTime()
            .format(storedTimeFormatter)
    }

    fun toLocalDate(epochMillis: Long): LocalDate {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(zoneId)
            .toLocalDate()
    }

    fun toLocalTime(epochMillis: Long): LocalTime {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(zoneId)
            .toLocalTime()
    }

    fun toLocalDateTime(epochMillis: Long): LocalDateTime {
        return Instant.ofEpochMilli(epochMillis)
            .atZone(zoneId)
            .toLocalDateTime()
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

    // Verifica si la fecha es valida.
    fun hasValidDate(year: Int, month: Int, day: Int): Boolean {
        return runCatching {
            LocalDate.of(year, month, day)
        }.isSuccess
    }

    // Verifica si la hora es valida.
    fun hasValidTime(hour: Int, minute: Int): Boolean {
        return runCatching {
            LocalTime.of(hour, minute)
        }.isSuccess
    }
}
