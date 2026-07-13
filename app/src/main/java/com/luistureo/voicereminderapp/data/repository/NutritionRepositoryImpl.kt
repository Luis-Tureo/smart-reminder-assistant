package com.luistureo.voicereminderapp.data.repository

import com.luistureo.voicereminderapp.core.nutrition.NutritionStatisticsCalculator
import com.luistureo.voicereminderapp.data.local.dao.NutritionDao
import com.luistureo.voicereminderapp.data.mapper.toDomain
import com.luistureo.voicereminderapp.data.mapper.toEntity
import com.luistureo.voicereminderapp.domain.nutrition.factory.DefaultNutritionTemplateFactory
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
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

class NutritionRepositoryImpl(
    private val nutritionDao: NutritionDao,
    private val nowProvider: () -> Long = System::currentTimeMillis
) : NutritionRepository {
    override suspend fun getPlan(date: LocalDate): NutritionPlan =
        nutritionDao.getPlan(date.toEpochDay())?.toDomain() ?: NutritionPlan(date = date)

    override suspend fun getPlans(startDate: LocalDate, endDate: LocalDate): List<NutritionPlan> =
        nutritionDao.getPlansBetween(startDate.toEpochDay(), endDate.toEpochDay()).map { it.toDomain() }

    override suspend fun getMeal(mealId: Int): DatedNutritionMeal? {
        val meal = nutritionDao.getMeal(mealId) ?: return null
        val plan = nutritionDao.getPlanEntityById(meal.planId) ?: return null
        return DatedNutritionMeal(LocalDate.ofEpochDay(plan.dateEpochDay), meal.toDomain())
    }

    override suspend fun saveMeal(date: LocalDate, meal: NutritionMeal): DatedNutritionMeal {
        val id = nutritionDao.saveMealForDate(date.toEpochDay(), meal.toEntity(), nowProvider())
        return requireNotNull(getMeal(id))
    }

    override suspend fun deleteMeal(mealId: Int) = nutritionDao.deleteMeal(mealId)

    override suspend fun duplicateMeal(mealId: Int): DatedNutritionMeal? =
        nutritionDao.duplicateMeal(mealId, nowProvider())?.let { getMeal(it) }

    override suspend fun moveMeal(mealId: Int, targetDate: LocalDate): DatedNutritionMeal? {
        if (!nutritionDao.moveMeal(mealId, targetDate.toEpochDay(), nowProvider())) return null
        return getMeal(mealId)
    }

    override suspend fun copyDay(
        sourceDate: LocalDate,
        targetDate: LocalDate
    ): List<DatedNutritionMeal> = nutritionDao.copyDay(
        sourceDate.toEpochDay(),
        targetDate.toEpochDay(),
        nowProvider()
    ).mapNotNull { getMeal(it) }

    override suspend fun updateMealStatus(
        mealId: Int,
        status: NutritionMealStatus
    ): DatedNutritionMeal? {
        nutritionDao.updateMealStatus(mealId, status.name, nowProvider())
        return getMeal(mealId)
    }

    override suspend fun addHydrationEntry(
        entry: NutritionHydrationEntry
    ): NutritionHydrationEntry {
        val id = nutritionDao.insertHydrationEntry(entry.toEntity()).toInt()
        return entry.copy(id = id)
    }

    override suspend fun getHydrationEntries(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<NutritionHydrationEntry> = nutritionDao.getHydrationEntriesBetween(
        startDate.toEpochDay(),
        endDate.toEpochDay()
    ).map { it.toDomain() }

    override suspend fun deleteHydrationEntry(entryId: Int) =
        nutritionDao.deleteHydrationEntry(entryId)

    override suspend fun getShoppingItems(): List<NutritionShoppingItem> =
        nutritionDao.getActiveShoppingItems().map { it.toDomain() }

    override suspend fun addShoppingItem(
        item: NutritionShoppingItem,
        increaseExisting: Boolean
    ): ShoppingAddResult {
        val normalized = normalizeShoppingName(item.name)
        require(normalized.isNotBlank())
        val existing = nutritionDao.findActiveShoppingItem(normalized)?.toDomain()
        if (existing != null && !increaseExisting) {
            return ShoppingAddResult(existing, duplicateFound = true)
        }
        if (existing != null) {
            val updated = existing.copy(
                quantityOrNote = combineQuantity(existing.quantityOrNote, item.quantityOrNote),
                checked = false,
                checkedAtEpochDay = null,
                updatedAtEpochMillis = nowProvider()
            )
            nutritionDao.updateShoppingItem(updated.toEntity())
            return ShoppingAddResult(updated, duplicateFound = false)
        }
        val toInsert = item.copy(
            normalizedName = normalized,
            checked = false,
            archived = false,
            createdAtEpochMillis = nowProvider(),
            updatedAtEpochMillis = nowProvider()
        )
        val id = nutritionDao.insertShoppingItem(toInsert.toEntity()).toInt()
        return ShoppingAddResult(toInsert.copy(id = id), duplicateFound = false)
    }

    override suspend fun updateShoppingItem(item: NutritionShoppingItem) {
        nutritionDao.updateShoppingItem(
            item.copy(
                normalizedName = normalizeShoppingName(item.name),
                updatedAtEpochMillis = nowProvider()
            ).toEntity()
        )
    }

    override suspend fun setShoppingItemChecked(itemId: Int, checked: Boolean) {
        val item = nutritionDao.getActiveShoppingItems().firstOrNull { it.id == itemId } ?: return
        nutritionDao.updateShoppingItem(
            item.copy(
                checked = checked,
                checkedAtEpochDay = LocalDate.now().toEpochDay().takeIf { checked },
                updatedAtEpochMillis = nowProvider()
            )
        )
    }

    override suspend fun archiveCompletedShoppingItems() =
        nutritionDao.archiveCompletedShoppingItems(nowProvider())

    override suspend fun clearShoppingList() = nutritionDao.clearActiveShoppingList(nowProvider())

    override suspend fun addShoppingFromPlan(date: LocalDate): Int {
        val values = getPlan(date).meals.flatMap { meal ->
            meal.foodsOrDishes.orEmpty().split(',', ';', '\n').map(String::trim)
        }.filter(String::isNotBlank)
        var added = 0
        values.distinctBy(::normalizeShoppingName).forEach { name ->
            val result = addShoppingItem(
                NutritionShoppingItem(
                    name = name,
                    normalizedName = normalizeShoppingName(name),
                    category = "Desde planificación"
                )
            )
            if (!result.duplicateFound) added++
        }
        return added
    }

    override suspend fun initializeBuiltInTemplates(): Boolean {
        if (nutritionDao.countBuiltInTemplates() >= BUILT_IN_TEMPLATE_COUNT) return false
        DefaultNutritionTemplateFactory.create().forEach { saveTemplate(it) }
        return true
    }

    override suspend fun getTemplates(): List<NutritionTemplate> =
        nutritionDao.getTemplates().map { it.toDomain() }

    override suspend fun getTemplate(templateId: Int): NutritionTemplate? =
        nutritionDao.getTemplate(templateId)?.toDomain()

    override suspend fun saveTemplate(template: NutritionTemplate): Int =
        nutritionDao.saveTemplateGraph(
            template.toEntity(),
            template.meals.map { it.toEntity() }
        )

    override suspend fun applyTemplate(
        template: NutritionTemplate,
        date: LocalDate
    ): List<DatedNutritionMeal> = template.meals.sortedBy { it.orderPriority }.map { item ->
        saveMeal(
            date,
            NutritionMeal(
                name = item.name,
                period = item.period,
                foodsOrDishes = item.foodsOrDishes,
                preparationNote = item.preparationNote
            )
        )
    }

    override suspend fun getPreferences(): NutritionPreferences =
        nutritionDao.getPreferences()?.toDomain() ?: NutritionPreferences()

    override suspend fun savePreferences(preferences: NutritionPreferences) {
        nutritionDao.savePreferences(
            preferences.copy(updatedAtEpochMillis = nowProvider()).toEntity()
        )
    }

    override suspend fun getStatistics(
        range: NutritionChartRange,
        anchorDate: LocalDate
    ): NutritionStatistics {
        val dateRange = NutritionStatisticsCalculator.dateRange(range, anchorDate)
        val plans = getPlans(dateRange.start, dateRange.endInclusive)
        val hydration = getHydrationEntries(dateRange.start, dateRange.endInclusive)
        val shopping = nutritionDao.countCompletedShoppingItems(
            dateRange.start.toEpochDay(),
            dateRange.endInclusive.toEpochDay()
        )
        return NutritionStatisticsCalculator.calculate(
            range,
            anchorDate,
            plans,
            hydration,
            shopping
        )
    }

    private fun normalizeShoppingName(value: String): String = Normalizer
        .normalize(value.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .replace("\\s+".toRegex(), " ")

    private fun combineQuantity(current: String?, added: String?): String? {
        val currentNumber = current?.trim()?.toIntOrNull()
        val addedNumber = added?.trim()?.toIntOrNull()
        if (currentNumber != null && addedNumber != null) return (currentNumber + addedNumber).toString()
        return listOfNotNull(current?.trim(), added?.trim())
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(" + ")
            .takeIf(String::isNotBlank)
    }

    private companion object {
        const val BUILT_IN_TEMPLATE_COUNT = 7
    }
}
