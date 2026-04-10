package com.luistureo.voicereminderapp.core.preference

import android.content.Context

data class NextDaySummaryTime(
    val hour: Int,
    val minute: Int
)

class NextDaySummaryPreferenceStore(
    context: Context
) {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    // Entrega la hora configurada para el resumen del dia siguiente.
    fun getSummaryTime(): NextDaySummaryTime {
        val storedHour = preferences.getInt(KEY_HOUR, DEFAULT_HOUR).coerceIn(0, 23)
        val storedMinute = preferences.getInt(KEY_MINUTE, DEFAULT_MINUTE).coerceIn(0, 59)
        return NextDaySummaryTime(hour = storedHour, minute = storedMinute)
    }

    // Guarda la hora elegida por el usuario.
    fun setSummaryTime(hour: Int, minute: Int) {
        preferences.edit()
            .putInt(KEY_HOUR, hour.coerceIn(0, 23))
            .putInt(KEY_MINUTE, minute.coerceIn(0, 59))
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "next_day_summary_preferences"
        private const val KEY_HOUR = "summary_hour"
        private const val KEY_MINUTE = "summary_minute"
        private const val DEFAULT_HOUR = 20
        private const val DEFAULT_MINUTE = 0
    }
}
