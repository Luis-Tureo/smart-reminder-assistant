package com.luistureo.voicereminderapp.presentation.nutrition.state

import com.luistureo.voicereminderapp.domain.nutrition.model.DatedNutritionMeal
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionHydrationEntry
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionPlan
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionPreferences
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionShoppingItem
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionStatistics
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionTemplate
import java.time.LocalDate

data class NutritionMealListItem(
    val date: LocalDate,
    val meal: com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMeal
)

data class NutritionDashboardSummary(
    val plannedMeals: Int = 0,
    val completedMeals: Int = 0,
    val pendingReminders: Int = 0,
    val hydrationMl: Int = 0,
    val hydrationTargetMl: Int = 0
)

data class NutritionUiState(
    val isLoading: Boolean = false,
    val selectedDate: LocalDate = LocalDate.now(),
    val isWeeklyMode: Boolean = false,
    val plan: NutritionPlan = NutritionPlan(date = LocalDate.now()),
    val mealItems: List<NutritionMealListItem> = emptyList(),
    val selectedMeal: DatedNutritionMeal? = null,
    val hydrationEntries: List<NutritionHydrationEntry> = emptyList(),
    val shoppingItems: List<NutritionShoppingItem> = emptyList(),
    val duplicateShoppingCandidate: NutritionShoppingItem? = null,
    val templates: List<NutritionTemplate> = emptyList(),
    val selectedTemplate: NutritionTemplate? = null,
    val preferences: NutritionPreferences = NutritionPreferences(),
    val preferencesLoaded: Boolean = false,
    val statistics: NutritionStatistics? = null,
    val dashboardSummary: NutritionDashboardSummary = NutritionDashboardSummary(),
    val message: String? = null
)
