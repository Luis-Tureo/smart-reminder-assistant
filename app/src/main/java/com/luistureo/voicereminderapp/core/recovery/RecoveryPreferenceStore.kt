package com.luistureo.voicereminderapp.core.recovery

import android.content.Context
import androidx.core.content.edit

enum class RecoveryNotificationTextMode { DISCREET, FULL }

class RecoveryPreferenceStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun selectedGoalId(): Int = preferences.getInt(KEY_SELECTED_GOAL_ID, 0)
    fun setSelectedGoalId(goalId: Int) = preferences.edit { putInt(KEY_SELECTED_GOAL_ID, goalId) }

    fun notificationTextMode(): RecoveryNotificationTextMode = runCatching {
        RecoveryNotificationTextMode.valueOf(
            preferences.getString(KEY_NOTIFICATION_TEXT, null)
                ?: RecoveryNotificationTextMode.DISCREET.name
        )
    }.getOrDefault(RecoveryNotificationTextMode.DISCREET)

    fun setNotificationTextMode(mode: RecoveryNotificationTextMode) = preferences.edit {
        putString(KEY_NOTIFICATION_TEXT, mode.name)
    }

    fun voiceEnabled(): Boolean = preferences.getBoolean(KEY_VOICE, false)
    fun setVoiceEnabled(enabled: Boolean) = preferences.edit { putBoolean(KEY_VOICE, enabled) }

    fun bubbleEnabled(): Boolean = preferences.getBoolean(KEY_BUBBLE, true)
    fun setBubbleEnabled(enabled: Boolean) = preferences.edit { putBoolean(KEY_BUBBLE, enabled) }

    fun milestonesEnabled(): Boolean = preferences.getBoolean(KEY_MILESTONES, true)
    fun setMilestonesEnabled(enabled: Boolean) = preferences.edit {
        putBoolean(KEY_MILESTONES, enabled)
    }

    fun statisticsEnabled(): Boolean = preferences.getBoolean(KEY_STATISTICS, true)
    fun setStatisticsEnabled(enabled: Boolean) = preferences.edit {
        putBoolean(KEY_STATISTICS, enabled)
    }

    companion object {
        private const val PREFERENCES_NAME = "recovery_display_preferences"
        private const val KEY_SELECTED_GOAL_ID = "selected_goal_id"
        private const val KEY_NOTIFICATION_TEXT = "notification_text_mode"
        private const val KEY_VOICE = "assistant_voice_enabled"
        private const val KEY_BUBBLE = "assistant_bubble_enabled"
        private const val KEY_MILESTONES = "milestones_enabled"
        private const val KEY_STATISTICS = "statistics_enabled"
    }
}
