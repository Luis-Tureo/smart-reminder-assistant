package com.luistureo.voicereminderapp.domain.nutrition.usecase

import com.luistureo.voicereminderapp.domain.nutrition.model.DatedNutritionMeal
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionHydrationEntry
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMeal
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMealStatus
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionReminderType
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionShoppingItem
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionTemplate
import com.luistureo.voicereminderapp.domain.nutrition.model.ShoppingAddResult
import com.luistureo.voicereminderapp.domain.nutrition.repository.NutritionRepository
import java.time.LocalDate

class NutritionValidationException(message: String) : IllegalArgumentException(message)

class SaveNutritionMealUseCase(private val repository: NutritionRepository) {
    suspend operator fun invoke(date: LocalDate, meal: NutritionMeal): DatedNutritionMeal {
        val cleanName = meal.name.trim()
        if (cleanName.isBlank()) throw NutritionValidationException("Ingresa un nombre para la comida.")
        if (
            meal.reminderType == NutritionReminderType.MINUTES_BEFORE &&
            (meal.time == null || (meal.reminderMinutesBefore ?: 0) <= 0)
        ) {
            throw NutritionValidationException("Selecciona una hora y minutos válidos para el aviso.")
        }
        if (meal.reminderType == NutritionReminderType.EXACT_TIME && meal.time == null) {
            throw NutritionValidationException("Selecciona una hora para el aviso.")
        }
        if (
            meal.reminderType == NutritionReminderType.CUSTOM &&
            (meal.customReminderAtEpochMillis ?: 0L) <= System.currentTimeMillis()
        ) {
            throw NutritionValidationException("Selecciona un aviso personalizado futuro.")
        }
        return repository.saveMeal(date, meal.copy(name = cleanName))
    }
}
class ChangeNutritionMealStatusUseCase(private val repository: NutritionRepository) {
    suspend operator fun invoke(mealId: Int, status: NutritionMealStatus): DatedNutritionMeal? =
        repository.updateMealStatus(mealId, status)
}

class CopyNutritionDayUseCase(private val repository: NutritionRepository) {
    suspend operator fun invoke(source: LocalDate, target: LocalDate): List<DatedNutritionMeal> {
        if (source == target) throw NutritionValidationException("Selecciona un día diferente.")
        return repository.copyDay(source, target)
    }
}

class TrackHydrationUseCase(private val repository: NutritionRepository) {
    suspend operator fun invoke(date: LocalDate, amountMl: Int): NutritionHydrationEntry {
        if (amountMl !in 1..10_000) {
            throw NutritionValidationException("Ingresa una cantidad válida.")
        }
        return repository.addHydrationEntry(
            NutritionHydrationEntry(date = date, amountMl = amountMl)
        )
    }
}

class AddNutritionShoppingItemUseCase(private val repository: NutritionRepository) {
    suspend operator fun invoke(
        name: String,
        quantityOrNote: String?,
        category: String,
        increaseExisting: Boolean = false
    ): ShoppingAddResult {
        if (name.isBlank()) throw NutritionValidationException("Ingresa un producto.")
        return repository.addShoppingItem(
            NutritionShoppingItem(
                name = name.trim(),
                normalizedName = name.trim().lowercase(),
                quantityOrNote = quantityOrNote,
                category = category.ifBlank { "Otros" }
            ),
            increaseExisting
        )
    }
}

class ApplyNutritionTemplateUseCase(private val repository: NutritionRepository) {
    suspend operator fun invoke(
        template: NutritionTemplate,
        date: LocalDate
    ): List<DatedNutritionMeal> {
        if (template.meals.isEmpty()) {
            throw NutritionValidationException("La vista previa no contiene comidas para aplicar.")
        }
        return repository.applyTemplate(template, date)
    }
}
