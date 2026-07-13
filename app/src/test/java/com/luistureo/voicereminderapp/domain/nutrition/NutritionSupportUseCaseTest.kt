package com.luistureo.voicereminderapp.domain.nutrition

import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionChartRange
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionDietaryStyle
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionGoal
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMeal
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMealPeriod
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMealStatus
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionPreferences
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionTemplate
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionTemplateMeal
import com.luistureo.voicereminderapp.domain.nutrition.usecase.AddNutritionShoppingItemUseCase
import com.luistureo.voicereminderapp.domain.nutrition.usecase.ApplyNutritionTemplateUseCase
import com.luistureo.voicereminderapp.domain.nutrition.usecase.NutritionValidationException
import com.luistureo.voicereminderapp.domain.nutrition.usecase.TrackHydrationUseCase
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionSupportUseCaseTest {
    private val today = LocalDate.of(2026, 7, 12)

    @Test
    fun hydrationAcceptsUserAmountAndRejectsInvalidValues() = runBlocking {
        val repository = FakeNutritionRepository()
        val useCase = TrackHydrationUseCase(repository)

        val entry = useCase(today, 375)
        assertEquals(375, entry.amountMl)
        assertEquals(1, repository.getHydrationEntries(today, today).size)

        assertThrows(NutritionValidationException::class.java) {
            runBlocking { useCase(today, 0) }
        }
        assertThrows(NutritionValidationException::class.java) {
            runBlocking { useCase(today, 10_001) }
        }
        Unit
    }

    @Test
    fun shoppingDeduplicationOffersIncreaseInsteadOfSilentDuplicate() = runBlocking {
        val repository = FakeNutritionRepository()
        val useCase = AddNutritionShoppingItemUseCase(repository)

        val first = useCase("Avena", "2", "Despensa")
        val duplicate = useCase(" avena ", "3", "Despensa")
        assertFalse(first.duplicateFound)
        assertTrue(duplicate.duplicateFound)
        assertEquals(1, repository.getShoppingItems().size)

        val increased = useCase("Avena", "3", "Despensa", increaseExisting = true)
        assertFalse(increased.duplicateFound)
        assertEquals("5", increased.item.quantityOrNote)
        assertEquals(1, repository.getShoppingItems().size)
    }

    @Test
    fun templatePreviewCanEditAndRemoveMealsBeforeApplying() = runBlocking {
        val repository = FakeNutritionRepository()
        val original = NutritionTemplate(
            id = 4,
            name = "Día editable",
            description = "Organización",
            preparationComplexity = "Baja",
            practicalBenefits = "Planificar",
            meals = listOf(
                NutritionTemplateMeal(
                    id = 1,
                    period = NutritionMealPeriod.BREAKFAST,
                    name = "Nombre original"
                ),
                NutritionTemplateMeal(
                    id = 2,
                    period = NutritionMealPeriod.LUNCH,
                    name = "Comida removida"
                )
            )
        )
        val preview = original.copy(
            meals = listOf(original.meals.first().copy(name = "Nombre editado"))
        )

        val applied = ApplyNutritionTemplateUseCase(repository)(preview, today)

        assertEquals(1, applied.size)
        assertEquals("Nombre editado", applied.single().meal.name)
        assertFalse(repository.getPlan(today).meals.any { it.name == "Comida removida" })
    }

    @Test
    fun allergiesGoalsAndDisabledPeriodsRoundTripLocally() = runBlocking {
        val repository = FakeNutritionRepository()
        val preferences = NutritionPreferences(
            dietaryStyle = NutritionDietaryStyle.VEGETARIAN,
            allergiesOrIntolerances = listOf("Ingrediente ingresado por la persona"),
            organizationalGoals = setOf(
                NutritionGoal.ORGANIZE_MEAL_TIMES,
                NutritionGoal.PLAN_SHOPPING
            ),
            enabledMealPeriods = setOf(
                NutritionMealPeriod.BREAKFAST,
                NutritionMealPeriod.LUNCH
            )
        )

        repository.savePreferences(preferences)
        val stored = repository.getPreferences()

        assertEquals(preferences.allergiesOrIntolerances, stored.allergiesOrIntolerances)
        assertEquals(preferences.organizationalGoals, stored.organizationalGoals)
        assertFalse(NutritionMealPeriod.DINNER in stored.enabledMealPeriods)
    }

    @Test
    fun statisticsCalculateDailyWeeklyAndMonthlyWithoutFoodScoring() = runBlocking {
        val repository = FakeNutritionRepository()
        val first = repository.saveMeal(
            today,
            NutritionMeal(name = "Desayuno", period = NutritionMealPeriod.BREAKFAST)
        )
        repository.updateMealStatus(first.meal.id, NutritionMealStatus.COMPLETED)
        repository.saveMeal(
            today.minusDays(3),
            NutritionMeal(name = "Almuerzo", period = NutritionMealPeriod.LUNCH)
        )
        TrackHydrationUseCase(repository)(today, 500)

        val day = repository.getStatistics(NutritionChartRange.DAY, today)
        val week = repository.getStatistics(NutritionChartRange.WEEK, today)
        val month = repository.getStatistics(NutritionChartRange.MONTH, today)

        assertEquals(1, day.plannedMeals)
        assertEquals(100, day.completionPercentage)
        assertEquals(2, week.plannedMeals)
        assertEquals(2, month.plannedMeals)
        assertEquals(500, week.hydrationMl)
    }
}
