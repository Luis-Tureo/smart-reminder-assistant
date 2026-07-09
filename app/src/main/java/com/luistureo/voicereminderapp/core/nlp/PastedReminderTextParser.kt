package com.luistureo.voicereminderapp.core.nlp

import android.util.Log
import com.luistureo.voicereminderapp.BuildConfig
import com.luistureo.voicereminderapp.domain.model.ReminderRecurrence
import com.luistureo.voicereminderapp.domain.model.ReminderRecurrenceUnit
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime

enum class PastedReminderDateOrigin {
    TEXT,
    SELECTED_CALENDAR_DAY,
    USER_SELECTED,
    MISSING
}

data class PastedReminderParseResult(
    val title: String,
    val detail: String,
    val date: LocalDate?,
    val time: VoiceReminderParsedTime?,
    val dateOrigin: PastedReminderDateOrigin,
    val isUrgent: Boolean,
    val recurrence: ReminderRecurrence?,
    val hasUnclearDate: Boolean,
    val hasUnclearTime: Boolean
) {
    val requiresDate: Boolean
        get() = date == null

    val requiresTime: Boolean
        get() = time == null

    val canConfirm: Boolean
        get() = detail.isNotBlank() && date != null && time != null
}

object PastedReminderTextParser {
    fun parse(
        input: String,
        selectedCalendarDay: LocalDate? = null,
        referenceDateTime: LocalDateTime = LocalDateTime.now()
    ): PastedReminderParseResult {
        ReminderTextParserLogger.parseStarted(
            characterCount = input.length,
            hasSelectedDay = selectedCalendarDay != null
        )
        val parsed = VoiceReminderParser.parse(input, referenceDateTime)
        val normalized = normalize(input)
        val hasDateSignal = DATE_SIGNAL.containsMatchIn(normalized)
        val hasTimeSignal = TIME_SIGNAL.containsMatchIn(normalized)
        val parsedDate = parsed.date
        val resolvedDate = when {
            parsedDate != null -> parsedDate
            hasDateSignal -> null
            else -> selectedCalendarDay
        }
        val dateOrigin = when {
            parsedDate != null -> PastedReminderDateOrigin.TEXT
            resolvedDate != null -> PastedReminderDateOrigin.SELECTED_CALENDAR_DAY
            else -> PastedReminderDateOrigin.MISSING
        }
        val timeIsExplicit = EXPLICIT_TIME_SIGNAL.containsMatchIn(normalized)
        val resolvedTime = parsed.time?.takeUnless { time ->
            time.isAmbiguous && !timeIsExplicit
        }
        val detail = parsed.reminderText.orEmpty().trim()
        val result = PastedReminderParseResult(
            title = ReminderContentCleaner.buildTitle(detail).orEmpty(),
            detail = detail,
            date = resolvedDate,
            time = resolvedTime,
            dateOrigin = dateOrigin,
            isUrgent = parsed.isUrgent,
            recurrence = detectRecurrence(normalized),
            hasUnclearDate = hasDateSignal && parsedDate == null,
            hasUnclearTime = parsed.invalidTimeMessage != null ||
                    (hasTimeSignal && resolvedTime == null)
        )
        if (result.requiresDate) ReminderTextParserLogger.missingDate(result.hasUnclearDate)
        if (result.requiresTime) ReminderTextParserLogger.missingTime(result.hasUnclearTime)
        ReminderTextParserLogger.parseSuccess(result)
        return result
    }

    private fun detectRecurrence(text: String): ReminderRecurrence? {
        return when {
            Regex("\\b(cada dia|todos los dias|diari[oa])\\b").containsMatchIn(text) ->
                ReminderRecurrence(ReminderRecurrenceUnit.DAY)
            Regex("\\b(cada semana|semanal(?:mente)?|todas las semanas)\\b")
                .containsMatchIn(text) -> ReminderRecurrence(ReminderRecurrenceUnit.WEEK)
            Regex("\\b(cada mes|mensual(?:mente)?)\\b").containsMatchIn(text) ->
                ReminderRecurrence(ReminderRecurrenceUnit.MONTH)
            Regex("\\b(cada ano|anual(?:mente)?)\\b").containsMatchIn(text) ->
                ReminderRecurrence(ReminderRecurrenceUnit.YEAR)
            else -> null
        }
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private val DATE_SIGNAL = Regex(
        "\\b(hoy|manana|lunes|martes|miercoles|jueves|viernes|sabado|domingo|" +
                "\\d{1,2}[/-]\\d{1,2}|\\d{1,2}\\s+de\\s+[a-z]+)\\b"
    )
    private val TIME_SIGNAL = Regex(
        "\\b(a\\s+las?|\\d{1,2}:\\d{1,2}|mediodia|medianoche|\\d{1,2}\\s*(?:am|pm|h))\\b"
    )
    private val EXPLICIT_TIME_SIGNAL = Regex(
        "\\b(a\\s+las?|\\d{1,2}:\\d{1,2}|mediodia|medianoche|\\d{1,2}\\s*(?:am|pm|h))\\b"
    )
}

object ReminderTextParserLogger {
    const val TAG = "ReminderTextParser"

    fun parseStarted(characterCount: Int, hasSelectedDay: Boolean) {
        safeLog("parse_started chars=$characterCount selectedDay=$hasSelectedDay")
    }

    fun parseSuccess(result: PastedReminderParseResult) {
        safeLog(
            "parse_success hasTitle=${result.title.isNotBlank()} " +
                    "hasDate=${result.date != null} hasTime=${result.time != null} " +
                    "urgent=${result.isUrgent} recurring=${result.recurrence != null}"
        )
    }

    fun missingDate(unclear: Boolean) = safeLog("missing_date unclear=$unclear")

    fun missingTime(unclear: Boolean) = safeLog("missing_time unclear=$unclear")

    fun confirmationShown() = safeLog("confirmation_shown")

    fun saved() = safeLog("saved")

    fun cancelled() = safeLog("cancelled")

    private fun safeLog(message: String) {
        if (!BuildConfig.DEBUG) return
        runCatching { Log.d(TAG, message) }
    }
}
