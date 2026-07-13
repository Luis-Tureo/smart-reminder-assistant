package com.luistureo.voicereminderapp.presentation.nutrition

import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMealPeriod
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMealStatus
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionReminderType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object NutritionUiFormatter {
    private val dateFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.forLanguageTag("es-CL"))

    fun period(period: NutritionMealPeriod): String = when (period) {
        NutritionMealPeriod.BREAKFAST -> "Desayuno"
        NutritionMealPeriod.MORNING_SNACK -> "Colación de mañana"
        NutritionMealPeriod.LUNCH -> "Almuerzo"
        NutritionMealPeriod.AFTERNOON_SNACK -> "Colación de tarde"
        NutritionMealPeriod.DINNER -> "Cena"
    }

    fun status(status: NutritionMealStatus): String = when (status) {
        NutritionMealStatus.PLANNED -> "Planificada"
        NutritionMealStatus.COMPLETED -> "Realizada"
        NutritionMealStatus.SKIPPED -> "Omitida"
    }

    fun reminder(type: NutritionReminderType): String = when (type) {
        NutritionReminderType.NONE -> "Sin aviso"
        NutritionReminderType.EXACT_TIME -> "A la hora indicada"
        NutritionReminderType.MINUTES_BEFORE -> "Minutos antes"
        NutritionReminderType.CUSTOM -> "Aviso personalizado"
    }

    fun date(date: LocalDate): String = date.format(dateFormatter)
}

