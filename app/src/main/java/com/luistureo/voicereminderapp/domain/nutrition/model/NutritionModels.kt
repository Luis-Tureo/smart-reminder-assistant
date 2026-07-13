package com.luistureo.voicereminderapp.domain.nutrition.model

import java.time.LocalDate
import java.time.LocalTime

enum class NutritionMealPeriod {
    BREAKFAST,
    MORNING_SNACK,
    LUNCH,
    AFTERNOON_SNACK,
    DINNER
}

enum class NutritionMealStatus {
    PLANNED,
    COMPLETED,
    SKIPPED
}

enum class NutritionReminderType {
    NONE,
    EXACT_TIME,
    MINUTES_BEFORE,
    CUSTOM
}

enum class NutritionDietaryStyle {
    NO_PREFERENCE,
    VEGETARIAN,
    VEGAN
}

enum class NutritionChartRange {
    DAY,
    WEEK,
    MONTH
}

enum class NutritionChartType {
    BARS,
    PERCENTAGES
}

enum class NutritionGoal {
    ORGANIZE_MEAL_TIMES,
    REMEMBER_MEALS,
    IMPROVE_HYDRATION,
    PREPARE_AHEAD,
    INCREASE_VARIETY,
    REDUCE_FORGOTTEN_MEALS,
    PLAN_SHOPPING
}

data class NutritionPlan(
    val id: Int = 0,
    val date: LocalDate,
    val meals: List<NutritionMeal> = emptyList(),
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
)

data class NutritionMeal(
    val id: Int = 0,
    val planId: Int = 0,
    val name: String,
    val period: NutritionMealPeriod,
    val time: LocalTime? = null,
    val foodsOrDishes: String? = null,
    val preparationNote: String? = null,
    val photoUri: String? = null,
    val reminderType: NutritionReminderType = NutritionReminderType.NONE,
    val reminderMinutesBefore: Int? = null,
    val customReminderAtEpochMillis: Long? = null,
    val status: NutritionMealStatus = NutritionMealStatus.PLANNED,
    val personalNotes: String? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
)

data class DatedNutritionMeal(
    val date: LocalDate,
    val meal: NutritionMeal
)

data class NutritionHydrationEntry(
    val id: Int = 0,
    val date: LocalDate,
    val amountMl: Int,
    val loggedAtEpochMillis: Long = System.currentTimeMillis()
)

data class NutritionShoppingItem(
    val id: Int = 0,
    val name: String,
    val normalizedName: String,
    val quantityOrNote: String? = null,
    val category: String = "Otros",
    val checked: Boolean = false,
    val archived: Boolean = false,
    val checkedAtEpochDay: Long? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
)

data class NutritionTemplate(
    val id: Int = 0,
    val name: String,
    val description: String,
    val preparationComplexity: String,
    val practicalBenefits: String,
    val editable: Boolean = true,
    val builtIn: Boolean = true,
    val builtInKey: String? = null,
    val meals: List<NutritionTemplateMeal> = emptyList()
)

data class NutritionTemplateMeal(
    val id: Int = 0,
    val templateId: Int = 0,
    val period: NutritionMealPeriod,
    val name: String,
    val foodsOrDishes: String? = null,
    val preparationNote: String? = null,
    val orderPriority: Int = 0
)

data class NutritionPreferences(
    val id: Int = SINGLETON_ID,
    val dietaryStyle: NutritionDietaryStyle = NutritionDietaryStyle.NO_PREFERENCE,
    val dislikes: List<String> = emptyList(),
    val exclusions: List<String> = emptyList(),
    val allergiesOrIntolerances: List<String> = emptyList(),
    val preferredFoods: List<String> = emptyList(),
    val organizationalGoals: Set<NutritionGoal> = emptySet(),
    val enabledMealPeriods: Set<NutritionMealPeriod> = NutritionMealPeriod.entries.toSet(),
    val hydrationEnabled: Boolean = false,
    val hydrationTargetMl: Int = 0,
    val hydrationContainerMl: Int = 250,
    val hydrationReminderStartMinutes: Int? = null,
    val hydrationReminderEndMinutes: Int? = null,
    val hydrationReminderIntervalMinutes: Int? = null,
    val remindersEnabled: Boolean = true,
    val assistantVoiceEnabled: Boolean = false,
    val temporaryBubbleEnabled: Boolean = true,
    val preferredChartType: NutritionChartType = NutritionChartType.BARS,
    val privacyModeEnabled: Boolean = true,
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

data class NutritionStatistics(
    val range: NutritionChartRange,
    val plannedMeals: Int,
    val completedMeals: Int,
    val skippedMeals: Int,
    val completionPercentage: Int,
    val hydrationMl: Int,
    val shoppingItemsCompleted: Int,
    val dailyValues: List<NutritionDailyStatistic>
)

data class NutritionDailyStatistic(
    val date: LocalDate,
    val plannedMeals: Int,
    val completedMeals: Int,
    val hydrationMl: Int
)

data class ShoppingAddResult(
    val item: NutritionShoppingItem,
    val duplicateFound: Boolean
)
