package com.luistureo.voicereminderapp.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.luistureo.voicereminderapp.data.local.entity.HydrationEntryEntity
import com.luistureo.voicereminderapp.data.local.entity.NutritionMealEntity
import com.luistureo.voicereminderapp.data.local.entity.NutritionPlanEntity
import com.luistureo.voicereminderapp.data.local.entity.NutritionPreferenceEntity
import com.luistureo.voicereminderapp.data.local.entity.NutritionTemplateEntity
import com.luistureo.voicereminderapp.data.local.entity.NutritionTemplateMealEntity
import com.luistureo.voicereminderapp.data.local.entity.ShoppingItemEntity

data class NutritionPlanWithMealsEntity(
    @Embedded val plan: NutritionPlanEntity,
    @Relation(parentColumn = "id", entityColumn = "planId")
    val meals: List<NutritionMealEntity>
)

data class NutritionTemplateWithMealsEntity(
    @Embedded val template: NutritionTemplateEntity,
    @Relation(parentColumn = "id", entityColumn = "templateId")
    val meals: List<NutritionTemplateMealEntity>
)

@Dao
abstract class NutritionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertPlan(plan: NutritionPlanEntity): Long

    @Query("SELECT * FROM nutrition_plans WHERE dateEpochDay = :dateEpochDay LIMIT 1")
    abstract suspend fun getPlanEntity(dateEpochDay: Long): NutritionPlanEntity?

    @Query("SELECT * FROM nutrition_plans WHERE id = :planId LIMIT 1")
    abstract suspend fun getPlanEntityById(planId: Int): NutritionPlanEntity?

    @Transaction
    @Query("SELECT * FROM nutrition_plans WHERE dateEpochDay = :dateEpochDay LIMIT 1")
    abstract suspend fun getPlan(dateEpochDay: Long): NutritionPlanWithMealsEntity?

    @Transaction
    @Query(
        "SELECT * FROM nutrition_plans WHERE dateEpochDay BETWEEN :startEpochDay AND :endEpochDay " +
            "ORDER BY dateEpochDay ASC"
    )
    abstract suspend fun getPlansBetween(
        startEpochDay: Long,
        endEpochDay: Long
    ): List<NutritionPlanWithMealsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertMeal(meal: NutritionMealEntity): Long

    @Update
    abstract suspend fun updateMeal(meal: NutritionMealEntity)

    @Query("SELECT * FROM nutrition_meals WHERE id = :mealId LIMIT 1")
    abstract suspend fun getMeal(mealId: Int): NutritionMealEntity?

    @Query("DELETE FROM nutrition_meals WHERE id = :mealId")
    abstract suspend fun deleteMeal(mealId: Int)

    @Query(
        "SELECT nutrition_meals.* FROM nutrition_meals " +
            "INNER JOIN nutrition_plans ON nutrition_plans.id = nutrition_meals.planId " +
            "WHERE nutrition_plans.dateEpochDay BETWEEN :startEpochDay AND :endEpochDay " +
            "ORDER BY nutrition_plans.dateEpochDay ASC, nutrition_meals.optionalTimeMinutes ASC, nutrition_meals.id ASC"
    )
    abstract suspend fun getMealsBetween(
        startEpochDay: Long,
        endEpochDay: Long
    ): List<NutritionMealEntity>

    @Query(
        "UPDATE nutrition_meals SET status = :status, updatedAtEpochMillis = :updatedAt " +
            "WHERE id = :mealId"
    )
    abstract suspend fun updateMealStatus(mealId: Int, status: String, updatedAt: Long)

    @Insert
    abstract suspend fun insertHydrationEntry(entry: HydrationEntryEntity): Long

    @Query(
        "SELECT * FROM nutrition_hydration_entries WHERE dateEpochDay BETWEEN :startEpochDay AND :endEpochDay " +
            "ORDER BY loggedAtEpochMillis DESC"
    )
    abstract suspend fun getHydrationEntriesBetween(
        startEpochDay: Long,
        endEpochDay: Long
    ): List<HydrationEntryEntity>

    @Query("DELETE FROM nutrition_hydration_entries WHERE id = :entryId")
    abstract suspend fun deleteHydrationEntry(entryId: Int)

    @Query(
        "SELECT * FROM nutrition_shopping_items WHERE archived = 0 " +
            "ORDER BY checked ASC, category ASC, createdAtEpochMillis ASC"
    )
    abstract suspend fun getActiveShoppingItems(): List<ShoppingItemEntity>

    @Query(
        "SELECT * FROM nutrition_shopping_items WHERE normalizedName = :normalizedName " +
            "AND archived = 0 LIMIT 1"
    )
    abstract suspend fun findActiveShoppingItem(normalizedName: String): ShoppingItemEntity?

    @Insert
    abstract suspend fun insertShoppingItem(item: ShoppingItemEntity): Long

    @Update
    abstract suspend fun updateShoppingItem(item: ShoppingItemEntity)

    @Query(
        "UPDATE nutrition_shopping_items SET archived = 1, updatedAtEpochMillis = :updatedAt " +
            "WHERE checked = 1 AND archived = 0"
    )
    abstract suspend fun archiveCompletedShoppingItems(updatedAt: Long)

    @Query("DELETE FROM nutrition_shopping_items WHERE archived = 0 AND checked = 0")
    abstract suspend fun deleteActiveUncheckedShoppingItems()

    @Query(
        "UPDATE nutrition_shopping_items SET archived = 1, updatedAtEpochMillis = :updatedAt " +
            "WHERE archived = 0 AND checked = 1"
    )
    abstract suspend fun archiveActiveCheckedShoppingItems(updatedAt: Long)

    @Query(
        "SELECT COUNT(*) FROM nutrition_shopping_items WHERE checked = 1 " +
            "AND checkedAtEpochDay BETWEEN :startEpochDay AND :endEpochDay"
    )
    abstract suspend fun countCompletedShoppingItems(
        startEpochDay: Long,
        endEpochDay: Long
    ): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTemplate(template: NutritionTemplateEntity): Long

    @Update
    abstract suspend fun updateTemplate(template: NutritionTemplateEntity)

    @Insert
    abstract suspend fun insertTemplateMeals(meals: List<NutritionTemplateMealEntity>)

    @Query("DELETE FROM nutrition_template_meals WHERE templateId = :templateId")
    abstract suspend fun deleteTemplateMeals(templateId: Int)

    @Transaction
    @Query("SELECT * FROM nutrition_templates ORDER BY builtIn DESC, name COLLATE NOCASE ASC")
    abstract suspend fun getTemplates(): List<NutritionTemplateWithMealsEntity>

    @Transaction
    @Query("SELECT * FROM nutrition_templates WHERE id = :templateId LIMIT 1")
    abstract suspend fun getTemplate(templateId: Int): NutritionTemplateWithMealsEntity?

    @Query("SELECT COUNT(*) FROM nutrition_templates WHERE builtIn = 1")
    abstract suspend fun countBuiltInTemplates(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun savePreferences(preferences: NutritionPreferenceEntity)

    @Query("SELECT * FROM nutrition_preferences WHERE id = 1 LIMIT 1")
    abstract suspend fun getPreferences(): NutritionPreferenceEntity?

    @Transaction
    open suspend fun ensurePlan(dateEpochDay: Long, now: Long): NutritionPlanEntity {
        insertPlan(
            NutritionPlanEntity(
                dateEpochDay = dateEpochDay,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now
            )
        )
        return requireNotNull(getPlanEntity(dateEpochDay))
    }

    @Transaction
    open suspend fun saveMealForDate(
        dateEpochDay: Long,
        meal: NutritionMealEntity,
        now: Long
    ): Int {
        val plan = ensurePlan(dateEpochDay, now)
        val stored = meal.copy(
            planId = plan.id,
            updatedAtEpochMillis = now,
            createdAtEpochMillis = meal.createdAtEpochMillis.takeIf { it > 0L } ?: now
        )
        return if (stored.id == 0) {
            insertMeal(stored).toInt()
        } else {
            updateMeal(stored)
            stored.id
        }
    }

    @Transaction
    open suspend fun duplicateMeal(mealId: Int, now: Long): Int? {
        val meal = getMeal(mealId) ?: return null
        return insertMeal(
            meal.copy(
                id = 0,
                name = "${meal.name} (copia)",
                status = "PLANNED",
                customReminderAtEpochMillis = null,
                reminderType = if (meal.reminderType == "CUSTOM") "NONE" else meal.reminderType,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now
            )
        ).toInt()
    }

    @Transaction
    open suspend fun moveMeal(mealId: Int, targetEpochDay: Long, now: Long): Boolean {
        val meal = getMeal(mealId) ?: return false
        val targetPlan = ensurePlan(targetEpochDay, now)
        updateMeal(
            meal.copy(
                planId = targetPlan.id,
                status = "PLANNED",
                customReminderAtEpochMillis = null,
                reminderType = if (meal.reminderType == "CUSTOM") "NONE" else meal.reminderType,
                updatedAtEpochMillis = now
            )
        )
        return true
    }

    @Transaction
    open suspend fun copyDay(sourceEpochDay: Long, targetEpochDay: Long, now: Long): List<Int> {
        if (sourceEpochDay == targetEpochDay) return emptyList()
        val source = getPlan(sourceEpochDay) ?: return emptyList()
        val target = ensurePlan(targetEpochDay, now)
        return source.meals.map { meal ->
            insertMeal(
                meal.copy(
                    id = 0,
                    planId = target.id,
                    status = "PLANNED",
                    customReminderAtEpochMillis = null,
                    reminderType = if (meal.reminderType == "CUSTOM") "NONE" else meal.reminderType,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now
                )
            ).toInt()
        }
    }

    @Transaction
    open suspend fun clearActiveShoppingList(now: Long) {
        archiveActiveCheckedShoppingItems(now)
        deleteActiveUncheckedShoppingItems()
    }

    @Transaction
    open suspend fun saveTemplateGraph(
        template: NutritionTemplateEntity,
        meals: List<NutritionTemplateMealEntity>
    ): Int {
        val id = if (template.id == 0) {
            insertTemplate(template).toInt()
        } else {
            updateTemplate(template)
            template.id
        }
        deleteTemplateMeals(id)
        if (meals.isNotEmpty()) {
            insertTemplateMeals(meals.map { it.copy(id = 0, templateId = id) })
        }
        return id
    }
}
