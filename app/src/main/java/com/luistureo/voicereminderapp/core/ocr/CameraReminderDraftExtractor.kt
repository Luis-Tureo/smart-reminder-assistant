package com.luistureo.voicereminderapp.core.ocr

import android.graphics.Bitmap
import android.net.Uri
import com.luistureo.voicereminderapp.core.utils.DateTimeFormatter
import com.luistureo.voicereminderapp.domain.model.ReminderDraft
import com.luistureo.voicereminderapp.domain.model.ReminderSource
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

data class CameraReminderScanResult(
    val draft: ReminderDraft,
    val recognizedText: String,
    val hasDetectedReminderText: Boolean,
    val hasDetectedDate: Boolean,
    val hasDetectedTime: Boolean
)

class CameraReminderDraftExtractor(
    private val textRecognizer: LocalImageTextRecognizer
) {

    suspend fun extractFromBitmap(bitmap: Bitmap): CameraReminderScanResult {
        return buildResult(textRecognizer.recognizeFromBitmap(bitmap))
    }

    suspend fun extractFromUri(uri: Uri): CameraReminderScanResult {
        return buildResult(textRecognizer.recognizeFromUri(uri))
    }

    fun close() {
        textRecognizer.close()
    }

    private fun buildResult(recognizedText: String): CameraReminderScanResult {
        val lines = recognizedText
            .lines()
            .map(::cleanLine)
            .filter { it.isNotBlank() }

        val resolvedDate = extractDate(recognizedText)
        val resolvedTime = extractTime(recognizedText)
        val resolvedDetail = extractReminderDetail(lines)
        val resolvedTitle = resolvedDetail
            ?.substringBefore(".")
            ?.substringBefore(",")
            ?.take(48)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        return CameraReminderScanResult(
            draft = ReminderDraft(
                title = resolvedTitle,
                text = resolvedDetail,
                date = resolvedDate,
                time = resolvedTime,
                source = ReminderSource.CAMERA
            ),
            recognizedText = recognizedText.trim(),
            hasDetectedReminderText = !resolvedDetail.isNullOrBlank(),
            hasDetectedDate = !resolvedDate.isNullOrBlank(),
            hasDetectedTime = !resolvedTime.isNullOrBlank()
        )
    }

    private fun extractReminderDetail(lines: List<String>): String? {
        if (lines.isEmpty()) return null

        val prioritizedLines = lines
            .mapNotNull { line ->
                val cleanedLine = cleanPurposeLine(line)
                val score = scorePurposeLine(cleanedLine)
                cleanedLine.takeIf { it.isNotBlank() && score > 0 }?.let { scoredLine ->
                    score to scoredLine
                }
            }
            .sortedByDescending { it.first }
            .map { it.second }
            .distinct()

        val detail = prioritizedLines
            .take(3)
            .joinToString(separator = ". ")
            .trim()

        return detail.takeIf { it.isNotBlank() }
            ?: lines.joinToString(separator = ". ").trim().takeIf { it.isNotBlank() }
    }

    private fun extractDate(text: String): String? {
        val today = LocalDate.now()
        val normalizedText = normalize(text)
        val numericPattern = Regex("""(?<!\d)(\d{1,2})[\/\-.](\d{1,2})(?:[\/\-.](\d{2,4}))?(?!\d)""")
        val textualPattern = Regex(
            """(?<!\d)(\d{1,2})\s+de\s+([a-z]+)(?:\s+de\s+(\d{2,4}))?""",
            RegexOption.IGNORE_CASE
        )

        numericPattern.findAll(normalizedText).forEach { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return@forEach
            val month = match.groupValues[2].toIntOrNull() ?: return@forEach
            val year = resolveYear(
                rawYear = match.groupValues.getOrNull(3).orEmpty(),
                month = month,
                day = day,
                referenceDate = today
            )

            if (DateTimeFormatter.hasValidDate(year, month, day)) {
                return DateTimeFormatter.formatDate(day, month, year)
            }
        }

        textualPattern.findAll(normalizedText).forEach { match ->
            val day = match.groupValues[1].toIntOrNull() ?: return@forEach
            val month = monthNames[match.groupValues[2].lowercase(Locale.getDefault())] ?: return@forEach
            val year = resolveYear(
                rawYear = match.groupValues.getOrNull(3).orEmpty(),
                month = month,
                day = day,
                referenceDate = today
            )

            if (DateTimeFormatter.hasValidDate(year, month, day)) {
                return DateTimeFormatter.formatDate(day, month, year)
            }
        }

        return null
    }

    private fun extractTime(text: String): String? {
        val normalizedText = normalize(text)
        val timePatterns = listOf(
            Regex("""(?<!\d)(\d{1,2}):(\d{2})(?!\d)"""),
            Regex("""(?<!\d)(\d{1,2})\s*(am|pm)(?![a-z])""", RegexOption.IGNORE_CASE),
            Regex("""(?:a\s+las|a\s+la)\s+(\d{1,2})(?!\d)""", RegexOption.IGNORE_CASE),
            Regex("""(?<!\d)(\d{1,2})\s*(?:hrs|hr|h)(?![a-z])""", RegexOption.IGNORE_CASE)
        )

        timePatterns.forEach { pattern ->
            val match = pattern.find(normalizedText) ?: return@forEach
            when (match.groupValues.size) {
                3 -> {
                    val firstValue = match.groupValues[1].toIntOrNull() ?: return@forEach
                    val secondValue = match.groupValues[2]

                    if (secondValue.all { it.isDigit() }) {
                        val minute = secondValue.toIntOrNull() ?: return@forEach
                        if (DateTimeFormatter.hasValidTime(firstValue, minute)) {
                            return DateTimeFormatter.formatTime(firstValue, minute)
                        }
                    } else {
                        val resolvedHour = resolveAmPmHour(firstValue, secondValue) ?: return@forEach
                        if (DateTimeFormatter.hasValidTime(resolvedHour, 0)) {
                            return DateTimeFormatter.formatTime(resolvedHour, 0)
                        }
                    }
                }

                2 -> {
                    val hour = match.groupValues[1].toIntOrNull() ?: return@forEach
                    if (DateTimeFormatter.hasValidTime(hour, 0)) {
                        return DateTimeFormatter.formatTime(hour, 0)
                    }
                }
            }
        }

        return null
    }

    private fun cleanPurposeLine(line: String): String {
        return line
            .replace(Regex("""(?<!\d)(\d{1,2})[\/\-.](\d{1,2})(?:[\/\-.](\d{2,4}))?(?!\d)"""), " ")
            .replace(
                Regex("""(?<!\d)(\d{1,2})\s+de\s+([a-zA-ZáéíóúÁÉÍÓÚñÑ]+)(?:\s+de\s+(\d{2,4}))?"""),
                " "
            )
            .replace(Regex("""(?<!\d)(\d{1,2}):(\d{2})(?!\d)"""), " ")
            .replace(Regex("""(?<!\d)(\d{1,2})\s*(am|pm|hrs|hr|h)(?![a-z])""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', ':', '.', ',')
    }

    private fun scorePurposeLine(line: String): Int {
        if (line.isBlank()) return 0

        val normalizedLine = normalize(line)
        val hasLetters = normalizedLine.any { it.isLetter() }
        val isMostlyDigits = normalizedLine.count { it.isDigit() } > normalizedLine.length / 2
        val metadataKeywords = listOf(
            "fecha",
            "hora",
            "rut",
            "folio",
            "telefono",
            "direccion",
            "atencion",
            "doctor",
            "doctora",
            "paciente",
            "diagnostico"
        )
        val reminderKeywords = listOf(
            "cita",
            "consulta",
            "control",
            "examen",
            "medico",
            "medica",
            "reunion",
            "terapia",
            "cumple",
            "vacuna",
            "recordar",
            "llamar",
            "retirar"
        )

        var score = 0

        if (hasLetters) score += 2
        if (line.length in 6..80) score += 2
        if (reminderKeywords.any { normalizedLine.contains(it) }) score += 3
        if (metadataKeywords.any { normalizedLine == it || normalizedLine.startsWith("$it ") }) score -= 2
        if (isMostlyDigits) score -= 3

        return score
    }

    private fun resolveYear(
        rawYear: String,
        month: Int,
        day: Int,
        referenceDate: LocalDate
    ): Int {
        val explicitYear = rawYear.toIntOrNull()
        if (explicitYear != null) {
            return if (explicitYear < 100) 2000 + explicitYear else explicitYear
        }

        val currentYear = referenceDate.year
        val currentYearCandidate = runCatching {
            LocalDate.of(currentYear, month, day)
        }.getOrNull()

        return if (currentYearCandidate != null && currentYearCandidate.isBefore(referenceDate)) {
            currentYear + 1
        } else {
            currentYear
        }
    }

    private fun resolveAmPmHour(
        rawHour: Int,
        meridiem: String
    ): Int? {
        if (rawHour !in 1..12) return null

        return when (meridiem.lowercase(Locale.getDefault())) {
            "am" -> if (rawHour == 12) 0 else rawHour
            "pm" -> if (rawHour == 12) 12 else rawHour + 12
            else -> null
        }
    }

    private fun cleanLine(value: String): String {
        return value
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(value.lowercase(Locale.getDefault()), Normalizer.Form.NFD)
            .replace(Regex("""\p{InCombiningDiacriticalMarks}+"""), "")
    }

    companion object {
        private val monthNames = mapOf(
            "enero" to 1,
            "ene" to 1,
            "febrero" to 2,
            "feb" to 2,
            "marzo" to 3,
            "mar" to 3,
            "abril" to 4,
            "abr" to 4,
            "mayo" to 5,
            "may" to 5,
            "junio" to 6,
            "jun" to 6,
            "julio" to 7,
            "jul" to 7,
            "agosto" to 8,
            "ago" to 8,
            "septiembre" to 9,
            "setiembre" to 9,
            "sep" to 9,
            "set" to 9,
            "octubre" to 10,
            "oct" to 10,
            "noviembre" to 11,
            "nov" to 11,
            "diciembre" to 12,
            "dic" to 12
        )
    }
}
