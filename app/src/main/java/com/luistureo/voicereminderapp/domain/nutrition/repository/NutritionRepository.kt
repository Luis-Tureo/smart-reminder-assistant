package com.luistureo.voicereminderapp.domain.nutrition.repository

import com.luistureo.voicereminderapp.domain.nutrition.model.DatedNutritionMeal
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionChartRange
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionHydrationEntry
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMeal
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMealStatus
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionPlan
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionPreferences
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionShoppingItem
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionStatistics
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionTemplate
import com.luistureo.voicereminderapp.domain.nutrition.model.ShoppingAddResult
import java.time.LocalDate

interface NutritionRepository {
    suspend fun getPlan(date: LocalDate): NutritionPlan
    suspend fun getPlans(startDate: LocalDate, endDate: LocalDate): List<NutritionPlan>
    suspend fun getMeal(mealId: Int): DatedNutritionMeal?
    suspend fun saveMeal(date: LocalDate, meal: NutritionMeal): DatedNutritionMeal
    suspend fun deleteMeal(mealId: Int)
    suspend fun duplicateMeal(mealId: Int): DatedNutritionMeal?
    suspend fun moveMeal(mealId: Int, targetDate: LocalDate): DatedNutritionMeal?
    suspend fun copyDay(sourceDate: LocalDate, targetDate: LocalDate): List<DatedNutritionMeal>
    suspend fun updateMealStatus(mealId: Int, status: NutritionMealStatus): DatedNutritionMeal?

    suspend fun addHydrationEntry(entry: NutritionHydrationEntry): NutritionHydrationEntry
    suspend fun getHydrationEntries(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<NutritionHydrationEntry>
    suspend fun deleteHydrationEntry(entryId: Int)

    suspend fun getShoppingItems(): List<NutritionShoppingItem>
    suspend fun addShoppingItem(
        item: NutritionShoppingItem,
        increaseExisting: Boolean = false
    ): ShoppingAddResult
    suspend fun updateShoppingItem(item: NutritionShoppingItem)
    suspend fun setShoppingItemChecked(itemId: Int, checked: Boolean)
    suspend fun archiveCompletedShoppingItems()
    suspend fun clearShoppingList()
    suspend fun addShoppingFromPlan(date: LocalDate): Int

    suspend fun initializeBuiltInTemplates(): Boolean
    suspend fun getTemplates(): List<NutritionTemplate>
    suspend fun getTemplate(templateId: Int): NutritionTemplate?
    suspend fun saveTemplate(template: NutritionTemplate): Int
    suspend fun applyTemplate(template: NutritionTemplate, date: LocalDate): List<DatedNutritionMeal>

    suspend fun getPreferences(): NutritionPreferences
    suspend fun savePreferences(preferences: NutritionPreferences)
    suspend fun getStatistics(
        range: NutritionChartRange,
        anchorDate: LocalDate
    ): NutritionStatistics
}
