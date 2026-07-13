package com.luistureo.voicereminderapp.domain.nutrition

import com.luistureo.voicereminderapp.core.nutrition.NutritionStatisticsCalculator
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
import com.luistureo.voicereminderapp.domain.nutrition.repository.NutritionRepository
import java.time.LocalDate

internal class FakeNutritionRepository : NutritionRepository {
    private val mealsByDate = linkedMapOf<LocalDate, MutableList<NutritionMeal>>()
    val hydrationEntries = mutableListOf<NutritionHydrationEntry>()
    val shoppingItems = mutableListOf<NutritionShoppingItem>()
    val templates = mutableListOf<NutritionTemplate>()
    var preferences = NutritionPreferences()
    private var nextMealId = 1
    private var nextHydrationId = 1
    private var nextShoppingId = 1

    override suspend fun getPlan(date: LocalDate): NutritionPlan = NutritionPlan(
        id = date.toEpochDay().toInt(),
        date = date,
        meals = mealsByDate[date].orEmpty().toList()
    )

    override suspend fun getPlans(startDate: LocalDate, endDate: LocalDate): List<NutritionPlan> =
        mealsByDate.keys.filter { !it.isBefore(startDate) && !it.isAfter(endDate) }
            .sorted().map { getPlan(it) }

    override suspend fun getMeal(mealId: Int): DatedNutritionMeal? = mealsByDate.entries
        .firstNotNullOfOrNull { (date, meals) ->
            meals.firstOrNull { it.id == mealId }?.let { DatedNutritionMeal(date, it) }
        }

    override suspend fun saveMeal(date: LocalDate, meal: NutritionMeal): DatedNutritionMeal {
        val id = meal.id.takeIf { it > 0 } ?: nextMealId++
        val stored = meal.copy(id = id, planId = date.toEpochDay().toInt())
        mealsByDate.values.forEach { list -> list.removeAll { it.id == id } }
        mealsByDate.getOrPut(date, ::mutableListOf).add(stored)
        return DatedNutritionMeal(date, stored)
    }

    override suspend fun deleteMeal(mealId: Int) {
        mealsByDate.values.forEach { it.removeAll { meal -> meal.id == mealId } }
    }

    override suspend fun duplicateMeal(mealId: Int): DatedNutritionMeal? {
        val source = getMeal(mealId) ?: return null
        return saveMeal(
            source.date,
            source.meal.copy(id = 0, name = "${source.meal.name} (copia)", status = NutritionMealStatus.PLANNED)
        )
    }

    override suspend fun moveMeal(mealId: Int, targetDate: LocalDate): DatedNutritionMeal? {
        val source = getMeal(mealId) ?: return null
        deleteMeal(mealId)
        return saveMeal(targetDate, source.meal.copy(status = NutritionMealStatus.PLANNED))
    }

    override suspend fun copyDay(
        sourceDate: LocalDate,
        targetDate: LocalDate
    ): List<DatedNutritionMeal> = getPlan(sourceDate).meals.map {
        saveMeal(targetDate, it.copy(id = 0, status = NutritionMealStatus.PLANNED))
    }

    override suspend fun updateMealStatus(
        mealId: Int,
        status: NutritionMealStatus
    ): DatedNutritionMeal? {
        val item = getMeal(mealId) ?: return null
        return saveMeal(item.date, item.meal.copy(status = status))
    }

    override suspend fun addHydrationEntry(
        entry: NutritionHydrationEntry
    ): NutritionHydrationEntry = entry.copy(id = nextHydrationId++).also(hydrationEntries::add)

    override suspend fun getHydrationEntries(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<NutritionHydrationEntry> = hydrationEntries.filter {
        !it.date.isBefore(startDate) && !it.date.isAfter(endDate)
    }

    override suspend fun deleteHydrationEntry(entryId: Int) {
        hydrationEntries.removeAll { it.id == entryId }
    }

    override suspend fun getShoppingItems(): List<NutritionShoppingItem> =
        shoppingItems.filterNot { it.archived }

    override suspend fun addShoppingItem(
        item: NutritionShoppingItem,
        increaseExisting: Boolean
    ): ShoppingAddResult {
        val normalized = item.name.trim().lowercase()
        val existing = shoppingItems.firstOrNull {
            it.normalizedName == normalized && !it.archived
        }
        if (existing != null && !increaseExisting) {
            return ShoppingAddResult(existing, duplicateFound = true)
        }
        if (existing != null) {
            val index = shoppingItems.indexOf(existing)
            val current = existing.quantityOrNote?.toIntOrNull()
            val added = item.quantityOrNote?.toIntOrNull()
            val updated = existing.copy(
                quantityOrNote = if (current != null && added != null) {
                    (current + added).toString()
                } else {
                    listOfNotNull(existing.quantityOrNote, item.quantityOrNote).joinToString(" + ")
                }
            )
            shoppingItems[index] = updated
            return ShoppingAddResult(updated, duplicateFound = false)
        }
        val inserted = item.copy(id = nextShoppingId++, normalizedName = normalized)
        shoppingItems += inserted
        return ShoppingAddResult(inserted, duplicateFound = false)
    }

    override suspend fun updateShoppingItem(item: NutritionShoppingItem) {
        shoppingItems.replaceAll { if (it.id == item.id) item else it }
    }

    override suspend fun setShoppingItemChecked(itemId: Int, checked: Boolean) {
        shoppingItems.replaceAll {
            if (it.id == itemId) it.copy(checked = checked) else it
        }
    }

    override suspend fun archiveCompletedShoppingItems() {
        shoppingItems.replaceAll { if (it.checked) it.copy(archived = true) else it }
    }

    override suspend fun clearShoppingList() {
        shoppingItems.clear()
    }

    override suspend fun addShoppingFromPlan(date: LocalDate): Int {
        var added = 0
        getPlan(date).meals.flatMap { meal ->
            meal.foodsOrDishes.orEmpty().split(',').map(String::trim)
        }.filter(String::isNotBlank).forEach { name ->
            val result = addShoppingItem(
                NutritionShoppingItem(name = name, normalizedName = name.lowercase())
            )
            if (!result.duplicateFound) added++
        }
        return added
    }

    override suspend fun initializeBuiltInTemplates(): Boolean = false
    override suspend fun getTemplates(): List<NutritionTemplate> = templates.toList()
    override suspend fun getTemplate(templateId: Int): NutritionTemplate? =
        templates.firstOrNull { it.id == templateId }

    override suspend fun saveTemplate(template: NutritionTemplate): Int {
        val id = template.id.takeIf { it > 0 } ?: templates.size + 1
        templates.removeAll { it.id == id }
        templates += template.copy(id = id)
        return id
    }

    override suspend fun applyTemplate(
        template: NutritionTemplate,
        date: LocalDate
    ): List<DatedNutritionMeal> = template.meals.map {
        saveMeal(date, NutritionMeal(name = it.name, period = it.period))
    }

    override suspend fun getPreferences(): NutritionPreferences = preferences

    override suspend fun savePreferences(preferences: NutritionPreferences) {
        this.preferences = preferences
    }

    override suspend fun getStatistics(
        range: NutritionChartRange,
        anchorDate: LocalDate
    ): NutritionStatistics {
        val dates = NutritionStatisticsCalculator.dateRange(range, anchorDate)
        return NutritionStatisticsCalculator.calculate(
            range = range,
            anchor = anchorDate,
            plans = getPlans(dates.start, dates.endInclusive),
            hydration = getHydrationEntries(dates.start, dates.endInclusive),
            shoppingItemsCompleted = shoppingItems.count { it.checked }
        )
    }
}
