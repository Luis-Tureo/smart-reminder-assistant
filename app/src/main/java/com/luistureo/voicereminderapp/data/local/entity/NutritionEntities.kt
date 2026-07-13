package com.luistureo.voicereminderapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "nutrition_plans",
    indices = [Index(value = ["dateEpochDay"], unique = true)]
)
data class NutritionPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateEpochDay: Long,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Entity(
    tableName = "nutrition_meals",
    foreignKeys = [
        ForeignKey(
            entity = NutritionPlanEntity::class,
            parentColumns = ["id"],
            childColumns = ["planId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("planId"),
        Index(value = ["planId", "period"]),
        Index(value = ["planId", "status"]),
        Index("customReminderAtEpochMillis")
    ]
)
data class NutritionMealEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val planId: Int,
    val name: String,
    val period: String,
    val optionalTimeMinutes: Int?,
    val foodsOrDishes: String?,
    val preparationNote: String?,
    val photoUri: String?,
    val reminderType: String,
    val reminderMinutesBefore: Int?,
    val customReminderAtEpochMillis: Long?,
    val status: String,
    val personalNotes: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Entity(
    tableName = "nutrition_hydration_entries",
    indices = [Index("dateEpochDay"), Index("loggedAtEpochMillis")]
)
data class HydrationEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateEpochDay: Long,
    val amountMl: Int,
    val loggedAtEpochMillis: Long
)

@Entity(
    tableName = "nutrition_shopping_items",
    indices = [
        Index(value = ["normalizedName", "archived"]),
        Index(value = ["checked", "archived"]),
        Index("checkedAtEpochDay")
    ]
)
data class ShoppingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val normalizedName: String,
    val quantityOrNote: String?,
    val category: String,
    val checked: Boolean,
    val archived: Boolean,
    val checkedAtEpochDay: Long?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Entity(
    tableName = "nutrition_templates",
    indices = [Index(value = ["builtInKey"], unique = true)]
)
data class NutritionTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String,
    val preparationComplexity: String,
    val practicalBenefits: String,
    val editable: Boolean,
    val builtIn: Boolean,
    val builtInKey: String?
)

@Entity(
    tableName = "nutrition_template_meals",
    foreignKeys = [
        ForeignKey(
            entity = NutritionTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("templateId"),
        Index(value = ["templateId", "orderPriority"], unique = true)
    ]
)
data class NutritionTemplateMealEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val templateId: Int,
    val period: String,
    val name: String,
    val foodsOrDishes: String?,
    val preparationNote: String?,
    val orderPriority: Int
)

@Entity(tableName = "nutrition_preferences")
data class NutritionPreferenceEntity(
    @PrimaryKey val id: Int = 1,
    val dietaryStyle: String,
    val dislikes: String,
    val exclusions: String,
    val allergiesOrIntolerances: String,
    val preferredFoods: String,
    val organizationalGoals: String,
    val enabledMealPeriods: String,
    val hydrationEnabled: Boolean,
    val hydrationTargetMl: Int,
    val hydrationContainerMl: Int,
    val hydrationReminderStartMinutes: Int?,
    val hydrationReminderEndMinutes: Int?,
    val hydrationReminderIntervalMinutes: Int?,
    val remindersEnabled: Boolean,
    val assistantVoiceEnabled: Boolean,
    val temporaryBubbleEnabled: Boolean,
    val preferredChartType: String,
    val privacyModeEnabled: Boolean,
    val updatedAtEpochMillis: Long
)
