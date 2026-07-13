package com.luistureo.voicereminderapp.core.nutrition

import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionChartRange
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionDailyStatistic
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionHydrationEntry
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMealStatus
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionPlan
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionStatistics
import java.time.LocalDate

object NutritionStatisticsCalculator {
    fun dateRange(range: NutritionChartRange, anchor: LocalDate): ClosedRange<LocalDate> = when (range) {
        NutritionChartRange.DAY -> anchor..anchor
        NutritionChartRange.WEEK -> anchor.minusDays(6)..anchor
        NutritionChartRange.MONTH -> anchor.minusDays(29)..anchor
    }

    fun calculate(
        range: NutritionChartRange,
        anchor: LocalDate,
        plans: List<NutritionPlan>,
        hydration: List<NutritionHydrationEntry>,
        shoppingItemsCompleted: Int
    ): NutritionStatistics {
        val dates = dateRange(range, anchor)
        val includedPlans = plans.filter { it.date in dates }
        val meals = includedPlans.flatMap { it.meals }
        val planned = meals.size
        val completed = meals.count { it.status == NutritionMealStatus.COMPLETED }
        val skipped = meals.count { it.status == NutritionMealStatus.SKIPPED }
        val daily = generateSequence(dates.start) { current ->
            current.plusDays(1).takeIf { !it.isAfter(dates.endInclusive) }
        }.map { date ->
            val dayMeals = includedPlans.firstOrNull { it.date == date }?.meals.orEmpty()
            NutritionDailyStatistic(
                date = date,
                plannedMeals = dayMeals.size,
                completedMeals = dayMeals.count { it.status == NutritionMealStatus.COMPLETED },
                hydrationMl = hydration.filter { it.date == date }.sumOf { it.amountMl }
            )
        }.toList()
        return NutritionStatistics(
            range = range,
            plannedMeals = planned,
            completedMeals = completed,
            skippedMeals = skipped,
            completionPercentage = if (planned == 0) 0 else completed * 100 / planned,
            hydrationMl = hydration.sumOf { it.amountMl },
            shoppingItemsCompleted = shoppingItemsCompleted,
            dailyValues = daily
        )
    }
}
