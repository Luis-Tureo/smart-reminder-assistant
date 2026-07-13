package com.luistureo.voicereminderapp.domain.nutrition

import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMeal
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMealPeriod
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMealStatus
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionReminderType
import com.luistureo.voicereminderapp.domain.nutrition.usecase.CopyNutritionDayUseCase
import com.luistureo.voicereminderapp.domain.nutrition.usecase.NutritionValidationException
import com.luistureo.voicereminderapp.domain.nutrition.usecase.SaveNutritionMealUseCase
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionMealAndPlanningUseCaseTest {
    private val date = LocalDate.of(2026, 7, 12)

    @Test
    fun minimumQuickSaveRequiresOnlyPeriodAndName() = runBlocking {
        val repository = FakeNutritionRepository()
        val saved = SaveNutritionMealUseCase(repository)(
            date,
            NutritionMeal(name = "Almuerzo sencillo", period = NutritionMealPeriod.LUNCH)
        )

        assertTrue(saved.meal.id > 0)
        assertEquals(date, saved.date)
        assertEquals(NutritionMealPeriod.LUNCH, saved.meal.period)
        assertNull(saved.meal.time)
        assertNull(saved.meal.foodsOrDishes)
        assertEquals(NutritionReminderType.NONE, saved.meal.reminderType)
    }

    @Test
    fun optionalMealTimeIsPreservedWithoutCreatingReminder() = runBlocking {
        val repository = FakeNutritionRepository()
        val saved = SaveNutritionMealUseCase(repository)(
            date,
            NutritionMeal(
                name = "Desayuno",
                period = NutritionMealPeriod.BREAKFAST,
                time = LocalTime.of(8, 15)
            )
        )

        assertEquals(LocalTime.of(8, 15), saved.meal.time)
        assertEquals(NutritionReminderType.NONE, saved.meal.reminderType)
    }

    @Test
    fun reminderValidationRejectsMissingBaseTimeAndPastCustomAlarm() {
        val repository = FakeNutritionRepository()
        assertThrows(NutritionValidationException::class.java) {
            runBlocking {
                SaveNutritionMealUseCase(repository)(
                    date,
                    NutritionMeal(
                        name = "Cena",
                        period = NutritionMealPeriod.DINNER,
                        reminderType = NutritionReminderType.MINUTES_BEFORE,
                        reminderMinutesBefore = 15
                    )
                )
            }
        }
        assertThrows(NutritionValidationException::class.java) {
            runBlocking {
                SaveNutritionMealUseCase(repository)(
                    date,
                    NutritionMeal(
                        name = "Cena",
                        period = NutritionMealPeriod.DINNER,
                        reminderType = NutritionReminderType.CUSTOM,
                        customReminderAtEpochMillis = 1L
                    )
                )
            }
        }
    }

    @Test
    fun dailyAndWeeklyPlansSupportStatusCopyDuplicateAndMove() = runBlocking {
        val repository = FakeNutritionRepository()
        val save = SaveNutritionMealUseCase(repository)
        val first = save(
            date,
            NutritionMeal(name = "Desayuno", period = NutritionMealPeriod.BREAKFAST)
        )
        save(
            date.plusDays(2),
            NutritionMeal(name = "Almuerzo", period = NutritionMealPeriod.LUNCH)
        )

        assertEquals(1, repository.getPlan(date).meals.size)
        assertEquals(2, repository.getPlans(date, date.plusDays(6)).sumOf { it.meals.size })

        repository.updateMealStatus(first.meal.id, NutritionMealStatus.COMPLETED)
        assertEquals(NutritionMealStatus.COMPLETED, repository.getMeal(first.meal.id)?.meal?.status)

        val copied = CopyNutritionDayUseCase(repository)(date, date.plusDays(1))
        assertEquals(1, copied.size)
        assertEquals(NutritionMealStatus.PLANNED, copied.single().meal.status)

        val duplicated = repository.duplicateMeal(first.meal.id)
        assertTrue(duplicated?.meal?.id != first.meal.id)
        assertEquals(2, repository.getPlan(date).meals.size)

        val moved = repository.moveMeal(first.meal.id, date.plusDays(3))
        assertEquals(date.plusDays(3), moved?.date)
        assertEquals(NutritionMealStatus.PLANNED, moved?.meal?.status)
    }
}
