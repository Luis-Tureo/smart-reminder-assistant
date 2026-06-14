package com.luistureo.voicereminderapp.core.nlp

import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class VoiceReminderParseResult(
    val reminderText: String? = null,
    val date: LocalDate? = null,
    val time: VoiceReminderParsedTime? = null,
    val isUrgent: Boolean = false,
    val hasRecurringRequest: Boolean = false,
    val invalidTimeMessage: String? = null
)

data class VoiceReminderParsedTime(
    val hour: Int,
    val minute: Int,
    val isAmbiguous: Boolean
)

object VoiceReminderParser {

    fun parse(
        input: String,
        referenceDateTime: LocalDateTime = LocalDateTime.now()
    ): VoiceReminderParseResult {
        val normalizedInput = normalize(input)
        val relativeDateTime = parseRelativeDateTime(normalizedInput, referenceDateTime)
        val parsedDate = relativeDateTime?.toLocalDate()
            ?: parseDate(normalizedInput, referenceDateTime.toLocalDate())
        val parsedTimeResult = relativeDateTime?.toLocalTime()?.let {
            TimeParseResult.Valid(VoiceReminderParsedTime(it.hour, it.minute, isAmbiguous = false))
        } ?: parseTime(normalizedInput)

        return VoiceReminderParseResult(
            reminderText = cleanReminderText(input),
            date = parsedDate,
            time = (parsedTimeResult as? TimeParseResult.Valid)?.time,
            isUrgent = VoiceReminderLanguageHelper.containsUrgentSignal(input),
            hasRecurringRequest = VoiceReminderLanguageHelper.containsRecurringRequest(input),
            invalidTimeMessage = (parsedTimeResult as? TimeParseResult.Invalid)?.message
        )
    }

    fun parseTime(input: String): TimeParseResult {
        val normalizedInput = replaceNumberWords(normalize(input))

        if (containsWholeWord(normalizedInput, "mediodia")) {
            return TimeParseResult.Valid(VoiceReminderParsedTime(12, 0, isAmbiguous = false))
        }

        if (containsWholeWord(normalizedInput, "medianoche")) {
            return TimeParseResult.Valid(VoiceReminderParsedTime(0, 0, isAmbiguous = false))
        }

        findTimeCandidate(normalizedInput)?.let { candidate ->
            return candidate.toParsedTime()
        }

        return TimeParseResult.Missing
    }

    private fun parseDate(
        text: String,
        referenceDate: LocalDate
    ): LocalDate? {
        return when {
            containsWholeWord(text, "pasado manana") -> referenceDate.plusDays(2)
            containsWholeWord(text, "manana") -> referenceDate.plusDays(1)
            containsWholeWord(text, "hoy") -> referenceDate
            else -> parseExplicitDate(text, referenceDate)
                ?: parseWeekday(text, referenceDate)
                ?: parseDayNumber(text, referenceDate)
        }
    }

    private fun parseRelativeDateTime(
        text: String,
        referenceDateTime: LocalDateTime
    ): LocalDateTime? {
        val minutes = Regex("\\ben\\s+(\\d{1,3})\\s+minutos?\\b")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()

        if (minutes != null) {
            return referenceDateTime.plusMinutes(minutes)
        }

        val hours = Regex("\\ben\\s+(\\d{1,2})\\s+horas?\\b")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()

        return hours?.let(referenceDateTime::plusHours)
    }

    private fun parseExplicitDate(
        text: String,
        referenceDate: LocalDate
    ): LocalDate? {
        Regex("\\b(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?\\b")
            .find(text)
            ?.let { match ->
                val day = match.groupValues[1].toIntOrNull() ?: return@let null
                val month = match.groupValues[2].toIntOrNull() ?: return@let null
                val year = resolveYear(match.groupValues.getOrNull(3).orEmpty(), day, month, referenceDate)
                return buildFutureDate(day, month, year, referenceDate)
            }

        Regex("\\b(?:el\\s+)?(\\d{1,2})\\s+de\\s+([a-z]+)(?:\\s+de\\s+(\\d{2,4}))?\\b")
            .find(text)
            ?.let { match ->
                val day = match.groupValues[1].toIntOrNull() ?: return@let null
                val month = monthNames[match.groupValues[2]] ?: return@let null
                val year = resolveYear(match.groupValues.getOrNull(3).orEmpty(), day, month, referenceDate)
                return buildFutureDate(day, month, year, referenceDate)
            }

        return null
    }

    private fun parseWeekday(
        text: String,
        referenceDate: LocalDate
    ): LocalDate? {
        val requestedDay = weekdays.entries.firstOrNull { (name, _) ->
            containsWholeWord(text, name)
        }?.value ?: return null
        val currentDay = referenceDate.dayOfWeek.value
        var daysUntil = (requestedDay - currentDay + 7) % 7

        if (daysUntil == 0) {
            daysUntil = 7
        }

        return referenceDate.plusDays(daysUntil.toLong())
    }

    private fun parseDayNumber(
        text: String,
        referenceDate: LocalDate
    ): LocalDate? {
        val day = Regex("\\b(?:el\\s+dia|el|dia)\\s+(\\d{1,2})\\b")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: return null

        if (day !in 1..31) return null

        val thisMonth = buildFutureDate(day, referenceDate.monthValue, referenceDate.year, referenceDate)
            ?: return null

        return if (thisMonth.isBefore(referenceDate)) {
            val nextMonth = referenceDate.plusMonths(1)
            buildFutureDate(day, nextMonth.monthValue, nextMonth.year, referenceDate)
        } else {
            thisMonth
        }
    }

    private fun buildFutureDate(
        day: Int,
        month: Int,
        year: Int,
        referenceDate: LocalDate
    ): LocalDate? {
        val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return null
        return if (date.isBefore(referenceDate) && year == referenceDate.year) {
            runCatching { LocalDate.of(year + 1, month, day) }.getOrNull()
        } else {
            date
        }
    }

    private fun resolveYear(
        rawYear: String,
        day: Int,
        month: Int,
        referenceDate: LocalDate
    ): Int {
        val explicitYear = rawYear.toIntOrNull()
        if (explicitYear != null) {
            return if (explicitYear < 100) 2000 + explicitYear else explicitYear
        }

        val currentYearCandidate = runCatching {
            LocalDate.of(referenceDate.year, month, day)
        }.getOrNull()

        return if (currentYearCandidate != null && currentYearCandidate.isBefore(referenceDate)) {
            referenceDate.year + 1
        } else {
            referenceDate.year
        }
    }

    private fun findTimeCandidate(text: String): TimeCandidate? {
        val patterns = listOf(
            Regex("\\b(?:a\\s+las|a\\s+la|las|la)?\\s*(\\d{1,2})\\s+y\\s+(media|cuarto|\\d{1,2})\\b"),
            Regex("\\b(?:a\\s+las|a\\s+la|las|la)?\\s*(\\d{1,2})\\s+de\\s+la\\s+(manana|tarde|noche|madrugada)\\b"),
            Regex("\\b(?:a\\s+las|a\\s+la|las|la)\\s+(\\d{1,2})(?::(\\d{1,2}))?\\s*(am|pm)?\\b"),
            Regex("\\b(\\d{1,2}):(\\d{1,2})\\s*(am|pm)?\\b"),
            Regex("\\b(\\d{1,2})\\s*(am|pm)\\b"),
            Regex("\\b(\\d{1,2})\\s+horas?\\b"),
            Regex("\\b(\\d{1,2})\\s*h\\b"),
            Regex("^(\\d{1,2})$")
        )

        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            val rawHour = match.groupValues.getOrNull(1)?.toIntOrNull() ?: continue
            val minuteGroup = match.groupValues.getOrNull(2).orEmpty()
            val meridiem = match.groupValues.getOrNull(3).orEmpty().takeIf { it == "am" || it == "pm" }
            val minute = when {
                minuteGroup == "media" -> 30
                minuteGroup == "cuarto" -> 15
                minuteGroup.isBlank() ||
                        minuteGroup == "am" ||
                        minuteGroup == "pm" ||
                        minuteGroup == "manana" ||
                        minuteGroup == "tarde" ||
                        minuteGroup == "noche" ||
                        minuteGroup == "madrugada" -> 0
                else -> minuteGroup.toIntOrNull() ?: return TimeCandidate(rawHour, -1, meridiem, text)
            }
            val resolvedMeridiem = meridiem
                ?: minuteGroup.takeIf { it == "am" || it == "pm" }
                ?: extractPeriod(text)

            return TimeCandidate(
                rawHour = rawHour,
                minute = minute,
                period = resolvedMeridiem,
                source = text
            )
        }

        return null
    }

    private fun TimeCandidate.toParsedTime(): TimeParseResult {
        if (minute !in 0..59) {
            return TimeParseResult.Invalid("Esa hora no me calza. Dime una hora valida, por ejemplo 15:00.")
        }

        val resolvedHour = when (period) {
            "am" -> when (rawHour) {
                in 1..11 -> rawHour
                12 -> 0
                else -> return TimeParseResult.Invalid("Esa hora no me calza. Usa una hora como 8 am o 20:30.")
            }

            "pm" -> when (rawHour) {
                in 1..11 -> rawHour + 12
                12 -> 12
                13 -> return TimeParseResult.Invalid("Esa hora no me calza. Si quieres la tarde, di 1 pm o 13:00.")
                in 14..23 -> rawHour
                else -> return TimeParseResult.Invalid("Esa hora no me calza. Usa una hora como 3 pm o 15:00.")
            }

            "manana" -> when (rawHour) {
                in 1..11 -> rawHour
                12 -> 0
                in 13..23 -> rawHour
                else -> return TimeParseResult.Invalid("Esa hora no me calza. Dime una hora valida.")
            }

            "tarde" -> when (rawHour) {
                in 1..8 -> rawHour + 12
                12 -> 12
                in 14..20 -> rawHour
                else -> return TimeParseResult.Invalid("Esa hora no me calza. Dime una hora valida.")
            }

            "noche" -> when (rawHour) {
                in 1..11 -> rawHour + 12
                in 20..23 -> rawHour
                else -> return TimeParseResult.Invalid("Esa hora no me calza. Dime una hora valida.")
            }

            "madrugada" -> when (rawHour) {
                in 1..6 -> rawHour
                12 -> 0
                else -> return TimeParseResult.Invalid("Esa hora no me calza. Dime una hora valida.")
            }

            else -> rawHour.takeIf { it in 0..23 }
                ?: return TimeParseResult.Invalid("Esa hora no me calza. Dime una hora valida, por ejemplo 15:00.")
        }

        val isAmbiguous = period == null && rawHour in 1..12 && !source.contains(":") &&
                !source.contains("horas") &&
                !source.contains(" h")

        return TimeParseResult.Valid(
            VoiceReminderParsedTime(
                hour = resolvedHour,
                minute = minute,
                isAmbiguous = isAmbiguous
            )
        )
    }

    fun cleanReminderText(input: String): String? {
        return ReminderContentCleaner.cleanDetail(input)
    }

    private fun extractPeriod(text: String): String? {
        return when {
            containsWholeWord(text, "madrugada") -> "madrugada"
            text.contains("de la manana") || containsWholeWord(text, "am") -> "manana"
            containsWholeWord(text, "tarde") -> "tarde"
            containsWholeWord(text, "noche") || containsWholeWord(text, "pm") -> "noche"
            else -> null
        }
    }

    private fun replaceNumberWords(input: String): String {
        var output = input

        numberWords.entries
            .sortedByDescending { it.key.length }
            .forEach { (word, value) ->
                output = output.replace(Regex("\\b${Regex.escape(word)}\\b"), value.toString())
            }

        return output
    }

    private fun normalize(text: String): String {
        return Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(",", " ")
            .replace(";", " ")
            .replace(".", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun containsWholeWord(text: String, value: String): Boolean {
        return Regex("\\b${Regex.escape(value)}\\b").containsMatchIn(text)
    }

    sealed class TimeParseResult {
        data class Valid(val time: VoiceReminderParsedTime) : TimeParseResult()
        data class Invalid(val message: String) : TimeParseResult()
        data object Missing : TimeParseResult()
    }

    private data class TimeCandidate(
        val rawHour: Int,
        val minute: Int,
        val period: String?,
        val source: String
    )

    private val weekdays = mapOf(
        "lunes" to 1,
        "martes" to 2,
        "miercoles" to 3,
        "jueves" to 4,
        "viernes" to 5,
        "sabado" to 6,
        "domingo" to 7
    )

    private val monthNames = mapOf(
        "enero" to 1,
        "febrero" to 2,
        "marzo" to 3,
        "abril" to 4,
        "mayo" to 5,
        "junio" to 6,
        "julio" to 7,
        "agosto" to 8,
        "septiembre" to 9,
        "setiembre" to 9,
        "octubre" to 10,
        "noviembre" to 11,
        "diciembre" to 12
    )

    private val numberWords = mapOf(
        "cero" to 0,
        "una" to 1,
        "uno" to 1,
        "un" to 1,
        "dos" to 2,
        "tres" to 3,
        "cuatro" to 4,
        "cinco" to 5,
        "seis" to 6,
        "siete" to 7,
        "ocho" to 8,
        "nueve" to 9,
        "diez" to 10,
        "once" to 11,
        "doce" to 12,
        "trece" to 13,
        "catorce" to 14,
        "quince" to 15,
        "dieciseis" to 16,
        "diecisiete" to 17,
        "dieciocho" to 18,
        "diecinueve" to 19,
        "veinte y tres" to 23,
        "veinte y dos" to 22,
        "veinte y uno" to 21,
        "veintiuno" to 21,
        "veintidos" to 22,
        "veintitres" to 23,
        "veinte" to 20
    )
}
