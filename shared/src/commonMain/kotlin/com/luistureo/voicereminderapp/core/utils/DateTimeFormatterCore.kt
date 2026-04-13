package com.luistureo.voicereminderapp.core.utils

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

// Contiene la parte multiplataforma del formateo y conversion de fechas.
object DateTimeFormatterCore {

    private val timeZone: TimeZone = TimeZone.currentSystemDefault()

    fun formatDate(day: Int, month: Int, year: Int): String {
        return buildString {
            append(day.toTwoDigits())
            append('/')
            append(month.toTwoDigits())
            append('/')
            append(year.toString().padStart(length = 4, padChar = '0'))
        }
    }

    fun formatTime(hour: Int, minute: Int): String {
        return buildString {
            append(hour.toTwoDigits())
            append(':')
            append(minute.toTwoDigits())
        }
    }

    fun buildTriggerTimeMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): Long {
        val localDateTime = LocalDateTime(
            year = year,
            monthNumber = month,
            dayOfMonth = day,
            hour = hour,
            minute = minute
        )

        return localDateTime.toInstant(timeZone).toEpochMilliseconds()
    }

    fun parseDateTimeToEpochMillis(
        date: String,
        time: String
    ): Long? {
        return runCatching {
            val parsedDate = parseDateParts(date) ?: return null
            val parsedTime = parseTimeParts(time) ?: return null

            LocalDateTime(
                year = parsedDate.year,
                monthNumber = parsedDate.month,
                dayOfMonth = parsedDate.day,
                hour = parsedTime.hour,
                minute = parsedTime.minute
            ).toInstant(timeZone).toEpochMilliseconds()
        }.getOrNull()
    }

    fun formatDateFromEpoch(epochMillis: Long): String {
        val localDateTime = Instant.fromEpochMilliseconds(epochMillis)
            .toLocalDateTime(timeZone)

        return formatDate(
            day = localDateTime.dayOfMonth,
            month = localDateTime.monthNumber,
            year = localDateTime.year
        )
    }

    fun formatTimeFromEpoch(epochMillis: Long): String {
        val localDateTime = Instant.fromEpochMilliseconds(epochMillis)
            .toLocalDateTime(timeZone)

        return formatTime(
            hour = localDateTime.hour,
            minute = localDateTime.minute
        )
    }

    fun hasValidDate(year: Int, month: Int, day: Int): Boolean {
        return runCatching {
            LocalDate(
                year = year,
                monthNumber = month,
                dayOfMonth = day
            )
        }.isSuccess
    }

    fun hasValidTime(hour: Int, minute: Int): Boolean {
        return runCatching {
            LocalTime(
                hour = hour,
                minute = minute
            )
        }.isSuccess
    }

    fun parseDateParts(value: String): DateInputParts? {
        val parts = value.split('/')
        if (parts.size != 3) return null

        val day = parts[0].takeIf { it.length == 2 }?.toIntOrNull() ?: return null
        val month = parts[1].takeIf { it.length == 2 }?.toIntOrNull() ?: return null
        val year = parts[2].takeIf { it.length == 4 }?.toIntOrNull() ?: return null

        if (!hasValidDate(year = year, month = month, day = day)) {
            return null
        }

        return DateInputParts(
            day = day,
            month = month,
            year = year
        )
    }

    fun parseTimeParts(value: String): TimeInputParts? {
        val parts = value.split(':')
        if (parts.size != 2) return null

        val hour = parts[0].takeIf { it.length == 2 }?.toIntOrNull() ?: return null
        val minute = parts[1].takeIf { it.length == 2 }?.toIntOrNull() ?: return null

        if (!hasValidTime(hour = hour, minute = minute)) {
            return null
        }

        return TimeInputParts(
            hour = hour,
            minute = minute
        )
    }
}

private fun Int.toTwoDigits(): String = toString().padStart(length = 2, padChar = '0')
