package com.luistureo.voicereminderapp.domain.nutrition.validation

import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionPreferences

object NutritionHydrationSettingsPolicy {
    fun requireValid(preferences: NutritionPreferences) {
        if (!preferences.hydrationEnabled) return
        require(preferences.hydrationTargetMl > 0) {
            "Configura una meta de hidratación mayor que cero."
        }
        require(preferences.hydrationContainerMl > 0) {
            "Configura un tamaño de recipiente válido."
        }
        val schedule = listOf(
            preferences.hydrationReminderStartMinutes,
            preferences.hydrationReminderEndMinutes,
            preferences.hydrationReminderIntervalMinutes
        )
        if (schedule.all { it == null }) return
        require(schedule.all { it != null }) {
            "Completa el inicio, fin e intervalo del recordatorio."
        }
        val start = requireNotNull(preferences.hydrationReminderStartMinutes)
        val end = requireNotNull(preferences.hydrationReminderEndMinutes)
        val interval = requireNotNull(preferences.hydrationReminderIntervalMinutes)
        require(start in 0..1439 && end in 0..1439 && start < end) {
            "La hora de término debe ser posterior a la hora de inicio."
        }
        require(interval in 15..720) {
            "Configura un intervalo entre 15 minutos y 12 horas."
        }
    }
}
