package com.luistureo.voicereminderapp.core.routine

import android.content.Context
import androidx.core.content.edit
import com.luistureo.voicereminderapp.domain.routine.model.RoutineChartType
import com.luistureo.voicereminderapp.domain.routine.model.RoutineStreakSettings
import com.luistureo.voicereminderapp.domain.routine.model.RoutineSuggestionSettings
import java.time.LocalDate

class RoutinePreferenceStore(context: Context) {
    data class PendingPostpone(
        val alarmType: RoutineAlarmType,
        val triggerAtEpochMillis: Long,
        val scheduledEpochDay: Long
    )

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun getPreferredName(): String? = preferences.getString(KEY_PREFERRED_NAME, null)
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    fun setPreferredName(name: String?) {
        preferences.edit {
            putString(
                KEY_PREFERRED_NAME,
                name?.trim()?.take(MAX_NAME_LENGTH)?.takeIf { it.isNotBlank() }
            )
        }
    }

    fun getPostponeMinutes(): Int = RoutinePostponePolicy.normalize(
        preferences.getInt(KEY_POSTPONE_MINUTES, RoutinePostponePolicy.DEFAULT_MINUTES)
    )

    fun setPostponeMinutes(minutes: Int) {
        preferences.edit {
            putInt(KEY_POSTPONE_MINUTES, RoutinePostponePolicy.normalize(minutes))
        }
    }

    fun getChartType(): RoutineChartType = runCatching {
        RoutineChartType.valueOf(
            preferences.getString(KEY_CHART_TYPE, RoutineChartType.BAR.name).orEmpty()
        )
    }.getOrDefault(RoutineChartType.BAR)

    fun setChartType(type: RoutineChartType) = preferences.edit {
        putString(KEY_CHART_TYPE, type.name)
    }

    fun getStreakSettings() = RoutineStreakSettings(
        countPartialDays = preferences.getBoolean(KEY_PARTIAL_STREAK, false),
        partialThresholdPercentage = preferences.getInt(KEY_PARTIAL_THRESHOLD, 80)
            .coerceIn(1, 100)
    )

    fun setStreakSettings(settings: RoutineStreakSettings) = preferences.edit {
        putBoolean(KEY_PARTIAL_STREAK, settings.countPartialDays)
        putInt(KEY_PARTIAL_THRESHOLD, settings.partialThresholdPercentage.coerceIn(1, 100))
    }

    fun getSuggestionSettings() = RoutineSuggestionSettings(
        enabled = preferences.getBoolean(KEY_SUGGESTIONS_ENABLED, true),
        preferredHour = preferences.getInt(KEY_SUGGESTION_HOUR, 18).coerceIn(0, 23),
        preferredMinute = preferences.getInt(KEY_SUGGESTION_MINUTE, 0).coerceIn(0, 59),
        showBubble = preferences.getBoolean(KEY_SUGGESTION_BUBBLE, true),
        speak = preferences.getBoolean(KEY_SUGGESTION_SPEAK, false)
    )

    fun setSuggestionSettings(settings: RoutineSuggestionSettings) = preferences.edit {
        putBoolean(KEY_SUGGESTIONS_ENABLED, settings.enabled)
        putInt(KEY_SUGGESTION_HOUR, settings.preferredHour.coerceIn(0, 23))
        putInt(KEY_SUGGESTION_MINUTE, settings.preferredMinute.coerceIn(0, 59))
        putBoolean(KEY_SUGGESTION_BUBBLE, settings.showBubble)
        putBoolean(KEY_SUGGESTION_SPEAK, settings.speak)
    }

    fun showBuiltInTemplates(): Boolean = preferences.getBoolean(KEY_SHOW_BUILT_INS, true)
    fun showPersonalTemplates(): Boolean = preferences.getBoolean(KEY_SHOW_PERSONAL, true)
    fun setTemplateVisibility(showBuiltIn: Boolean, showPersonal: Boolean) = preferences.edit {
        putBoolean(KEY_SHOW_BUILT_INS, showBuiltIn)
        putBoolean(KEY_SHOW_PERSONAL, showPersonal)
    }

    fun completionMessagesEnabled(): Boolean = preferences.getBoolean(KEY_COMPLETION_MESSAGES, true)
    fun partialMessagesEnabled(): Boolean = preferences.getBoolean(KEY_PARTIAL_MESSAGES, true)
    fun missedMessagesEnabled(): Boolean = preferences.getBoolean(KEY_MISSED_MESSAGES, true)
    fun setMotivationMessages(completed: Boolean, partial: Boolean, missed: Boolean) =
        preferences.edit {
            putBoolean(KEY_COMPLETION_MESSAGES, completed)
            putBoolean(KEY_PARTIAL_MESSAGES, partial)
            putBoolean(KEY_MISSED_MESSAGES, missed)
        }

    fun recordPostpone(routineId: Int, date: LocalDate = LocalDate.now()) = preferences.edit {
        val key = postponeCountKey(routineId, date.toEpochDay())
        putInt(key, preferences.getInt(key, 0) + 1)
    }

    fun recentPostponeCount(routineId: Int, today: LocalDate = LocalDate.now()): Int =
        (0L..6L).sumOf { offset ->
            preferences.getInt(postponeCountKey(routineId, today.minusDays(offset).toEpochDay()), 0)
        }

    fun getPendingPostpones(routineId: Int): List<PendingPostpone> {
        val prefix = pendingPostponePrefix(routineId)
        val stored = preferences.all.mapNotNull { (key, value) ->
            if (!key.startsWith(prefix)) return@mapNotNull null
            val type = key.removePrefix(prefix)
                .let { runCatching { RoutineAlarmType.valueOf(it) }.getOrNull() }
                ?.takeIf { it.isSnoozed }
                ?: return@mapNotNull null
            parsePendingPostpone(type, value as? String)
        }
        val legacy = parseLegacyPendingPostpone(
            preferences.getString(legacyPendingPostponeKey(routineId), null)
        )
        return (stored + listOfNotNull(legacy)).distinctBy { it.alarmType }
    }

    fun setPendingPostpone(
        routineId: Int,
        alarmType: RoutineAlarmType,
        triggerAtEpochMillis: Long,
        scheduledEpochDay: Long
    ) {
        require(alarmType.isSnoozed)
        preferences.edit {
            remove(legacyPendingPostponeKey(routineId))
            putString(
                pendingPostponeKey(routineId, alarmType),
                "$triggerAtEpochMillis|$scheduledEpochDay"
            )
        }
    }

    fun clearPendingPostpone(routineId: Int, alarmType: RoutineAlarmType) {
        preferences.edit { remove(pendingPostponeKey(routineId, alarmType)) }
    }

    fun clearPendingPostpones(routineId: Int) {
        val keys = preferences.all.keys.filter { it.startsWith(pendingPostponePrefix(routineId)) }
        preferences.edit {
            keys.forEach(::remove)
            remove(legacyPendingPostponeKey(routineId))
        }
    }

    fun getPendingDayClose(routineId: Int): Long? = preferences
        .getLong(pendingDayCloseKey(routineId), NO_EPOCH_DAY)
        .takeUnless { it == NO_EPOCH_DAY }

    fun setPendingDayClose(routineId: Int, scheduledEpochDay: Long) {
        preferences.edit { putLong(pendingDayCloseKey(routineId), scheduledEpochDay) }
    }

    fun clearPendingDayClose(routineId: Int, scheduledEpochDay: Long? = null) {
        if (
            scheduledEpochDay != null &&
            getPendingDayClose(routineId) != scheduledEpochDay
        ) return
        preferences.edit { remove(pendingDayCloseKey(routineId)) }
    }

    private fun parsePendingPostpone(
        alarmType: RoutineAlarmType,
        value: String?
    ): PendingPostpone? {
        val parts = value?.split('|') ?: return null
        if (parts.size != 2) return null
        val trigger = parts[0].toLongOrNull()?.takeIf { it > 0L } ?: return null
        val scheduledEpochDay = parts[1].toLongOrNull() ?: return null
        return PendingPostpone(alarmType, trigger, scheduledEpochDay)
    }

    private fun parseLegacyPendingPostpone(value: String?): PendingPostpone? {
        val parts = value?.split('|') ?: return null
        if (parts.size != 2) return null
        val type = runCatching { RoutineAlarmType.valueOf(parts[0]) }.getOrNull()
            ?.takeIf { it.isSnoozed }
            ?: return null
        val trigger = parts[1].toLongOrNull()?.takeIf { it > 0L } ?: return null
        val triggerDay = java.time.Instant.ofEpochMilli(trigger)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .toEpochDay()
        return PendingPostpone(type, trigger, triggerDay)
    }

    private fun pendingPostponePrefix(routineId: Int) = "pending_postpone_${routineId}_"

    private fun pendingPostponeKey(routineId: Int, alarmType: RoutineAlarmType) =
        "${pendingPostponePrefix(routineId)}${alarmType.name}"

    private fun legacyPendingPostponeKey(routineId: Int) = "pending_postpone_$routineId"

    private fun pendingDayCloseKey(routineId: Int) = "pending_day_close_$routineId"
    private fun postponeCountKey(routineId: Int, epochDay: Long) =
        "postpone_count_${routineId}_$epochDay"

    companion object {
        private const val PREFERENCES_NAME = "routine_assistant_preferences"
        private const val KEY_PREFERRED_NAME = "preferred_name"
        private const val KEY_POSTPONE_MINUTES = "postpone_minutes"
        private const val KEY_CHART_TYPE = "statistics_chart_type"
        private const val KEY_PARTIAL_STREAK = "streak_count_partial"
        private const val KEY_PARTIAL_THRESHOLD = "streak_partial_threshold"
        private const val KEY_SUGGESTIONS_ENABLED = "suggestions_enabled"
        private const val KEY_SUGGESTION_HOUR = "suggestion_hour"
        private const val KEY_SUGGESTION_MINUTE = "suggestion_minute"
        private const val KEY_SUGGESTION_BUBBLE = "suggestion_bubble"
        private const val KEY_SUGGESTION_SPEAK = "suggestion_speak"
        private const val KEY_SHOW_BUILT_INS = "templates_show_built_in"
        private const val KEY_SHOW_PERSONAL = "templates_show_personal"
        private const val KEY_COMPLETION_MESSAGES = "messages_completed"
        private const val KEY_PARTIAL_MESSAGES = "messages_partial"
        private const val KEY_MISSED_MESSAGES = "messages_missed"
        private const val MAX_NAME_LENGTH = 40
        private const val NO_EPOCH_DAY = Long.MIN_VALUE
    }
}
