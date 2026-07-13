package com.luistureo.voicereminderapp.data.mapper

import com.luistureo.voicereminderapp.data.local.dao.NutritionPlanWithMealsEntity
import com.luistureo.voicereminderapp.data.local.dao.NutritionTemplateWithMealsEntity
import com.luistureo.voicereminderapp.data.local.entity.HydrationEntryEntity
import com.luistureo.voicereminderapp.data.local.entity.NutritionMealEntity
import com.luistureo.voicereminderapp.data.local.entity.NutritionPreferenceEntity
import com.luistureo.voicereminderapp.data.local.entity.NutritionTemplateEntity
import com.luistureo.voicereminderapp.data.local.entity.NutritionTemplateMealEntity
import com.luistureo.voicereminderapp.data.local.entity.ShoppingItemEntity
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionChartType
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionDietaryStyle
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionHydrationEntry
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionGoal
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMeal
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMealPeriod
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMealStatus
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionPlan
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionPreferences
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionReminderType
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionShoppingItem
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionTemplate
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionTemplateMeal
import java.time.LocalDate
import java.time.LocalTime

private const val LIST_SEPARATOR = '\u001F'

fun NutritionMeal.toEntity() = NutritionMealEntity(
    id = id,
    planId = planId,
    name = name.trim(),
    period = period.name,
    optionalTimeMinutes = time?.toSecondOfDay()?.div(60),
    foodsOrDishes = foodsOrDishes.cleanOptional(),
    preparationNote = preparationNote.cleanOptional(),
    photoUri = photoUri.cleanOptional(),
    reminderType = reminderType.name,
    reminderMinutesBefore = reminderMinutesBefore,
    customReminderAtEpochMillis = customReminderAtEpochMillis,
    status = status.name,
    personalNotes = personalNotes.cleanOptional(),
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)

fun NutritionMealEntity.toDomain() = NutritionMeal(
    id = id,
    planId = planId,
    name = name,
    period = enumValueOrDefault(period, NutritionMealPeriod.BREAKFAST),
    time = optionalTimeMinutes?.let { LocalTime.ofSecondOfDay(it.toLong() * 60L) },
    foodsOrDishes = foodsOrDishes,
    preparationNote = preparationNote,
    photoUri = photoUri,
    reminderType = enumValueOrDefault(reminderType, NutritionReminderType.NONE),
    reminderMinutesBefore = reminderMinutesBefore,
    customReminderAtEpochMillis = customReminderAtEpochMillis,
    status = enumValueOrDefault(status, NutritionMealStatus.PLANNED),
    personalNotes = personalNotes,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)

fun NutritionPlanWithMealsEntity.toDomain() = NutritionPlan(
    id = plan.id,
    date = LocalDate.ofEpochDay(plan.dateEpochDay),
    meals = meals.map { it.toDomain() }.sortedWith(
        compareBy<NutritionMeal> { it.period.ordinal }.thenBy { it.time }.thenBy { it.id }
    ),
    createdAtEpochMillis = plan.createdAtEpochMillis,
    updatedAtEpochMillis = plan.updatedAtEpochMillis
)

fun NutritionHydrationEntry.toEntity() = HydrationEntryEntity(
    id = id,
    dateEpochDay = date.toEpochDay(),
    amountMl = amountMl,
    loggedAtEpochMillis = loggedAtEpochMillis
)

fun HydrationEntryEntity.toDomain() = NutritionHydrationEntry(
    id = id,
    date = LocalDate.ofEpochDay(dateEpochDay),
    amountMl = amountMl,
    loggedAtEpochMillis = loggedAtEpochMillis
)

fun NutritionShoppingItem.toEntity() = ShoppingItemEntity(
    id = id,
    name = name.trim(),
    normalizedName = normalizedName,
    quantityOrNote = quantityOrNote.cleanOptional(),
    category = category,
    checked = checked,
    archived = archived,
    checkedAtEpochDay = checkedAtEpochDay,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)

fun ShoppingItemEntity.toDomain() = NutritionShoppingItem(
    id = id,
    name = name,
    normalizedName = normalizedName,
    quantityOrNote = quantityOrNote,
    category = category,
    checked = checked,
    archived = archived,
    checkedAtEpochDay = checkedAtEpochDay,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis
)

fun NutritionTemplate.toEntity() = NutritionTemplateEntity(
    id = id,
    name = name,
    description = description,
    preparationComplexity = preparationComplexity,
    practicalBenefits = practicalBenefits,
    editable = editable,
    builtIn = builtIn,
    builtInKey = builtInKey
)

fun NutritionTemplateMeal.toEntity() = NutritionTemplateMealEntity(
    id = id,
    templateId = templateId,
    period = period.name,
    name = name,
    foodsOrDishes = foodsOrDishes.cleanOptional(),
    preparationNote = preparationNote.cleanOptional(),
    orderPriority = orderPriority
)

fun NutritionTemplateWithMealsEntity.toDomain() = NutritionTemplate(
    id = template.id,
    name = template.name,
    description = template.description,
    preparationComplexity = template.preparationComplexity,
    practicalBenefits = template.practicalBenefits,
    editable = template.editable,
    builtIn = template.builtIn,
    builtInKey = template.builtInKey,
    meals = meals.sortedBy { it.orderPriority }.map { meal ->
        NutritionTemplateMeal(
            id = meal.id,
            templateId = meal.templateId,
            period = enumValueOrDefault(meal.period, NutritionMealPeriod.BREAKFAST),
            name = meal.name,
            foodsOrDishes = meal.foodsOrDishes,
            preparationNote = meal.preparationNote,
            orderPriority = meal.orderPriority
        )
    }
)

fun NutritionPreferences.toEntity() = NutritionPreferenceEntity(
    id = id,
    dietaryStyle = dietaryStyle.name,
    dislikes = dislikes.toStoredList(),
    exclusions = exclusions.toStoredList(),
    allergiesOrIntolerances = allergiesOrIntolerances.toStoredList(),
    preferredFoods = preferredFoods.toStoredList(),
    organizationalGoals = organizationalGoals.joinToString(LIST_SEPARATOR.toString()) { it.name },
    enabledMealPeriods = enabledMealPeriods.joinToString(LIST_SEPARATOR.toString()) { it.name },
    hydrationEnabled = hydrationEnabled,
    hydrationTargetMl = hydrationTargetMl,
    hydrationContainerMl = hydrationContainerMl,
    hydrationReminderStartMinutes = hydrationReminderStartMinutes,
    hydrationReminderEndMinutes = hydrationReminderEndMinutes,
    hydrationReminderIntervalMinutes = hydrationReminderIntervalMinutes,
    remindersEnabled = remindersEnabled,
    assistantVoiceEnabled = assistantVoiceEnabled,
    temporaryBubbleEnabled = temporaryBubbleEnabled,
    preferredChartType = preferredChartType.name,
    privacyModeEnabled = privacyModeEnabled,
    updatedAtEpochMillis = updatedAtEpochMillis
)

fun NutritionPreferenceEntity.toDomain() = NutritionPreferences(
    id = id,
    dietaryStyle = enumValueOrDefault(dietaryStyle, NutritionDietaryStyle.NO_PREFERENCE),
    dislikes = dislikes.fromStoredList(),
    exclusions = exclusions.fromStoredList(),
    allergiesOrIntolerances = allergiesOrIntolerances.fromStoredList(),
    preferredFoods = preferredFoods.fromStoredList(),
    organizationalGoals = organizationalGoals.fromStoredList()
        .mapNotNull { value -> NutritionGoal.entries.firstOrNull { it.name == value } }
        .toSet(),
    enabledMealPeriods = enabledMealPeriods.fromStoredList()
        .mapNotNull { value -> NutritionMealPeriod.entries.firstOrNull { it.name == value } }
        .toSet(),
    hydrationEnabled = hydrationEnabled,
    hydrationTargetMl = hydrationTargetMl,
    hydrationContainerMl = hydrationContainerMl,
    hydrationReminderStartMinutes = hydrationReminderStartMinutes,
    hydrationReminderEndMinutes = hydrationReminderEndMinutes,
    hydrationReminderIntervalMinutes = hydrationReminderIntervalMinutes,
    remindersEnabled = remindersEnabled,
    assistantVoiceEnabled = assistantVoiceEnabled,
    temporaryBubbleEnabled = temporaryBubbleEnabled,
    preferredChartType = enumValueOrDefault(preferredChartType, NutritionChartType.BARS),
    privacyModeEnabled = privacyModeEnabled,
    updatedAtEpochMillis = updatedAtEpochMillis
)

private fun List<String>.toStoredList(): String = map(String::trim)
    .filter(String::isNotBlank)
    .distinctBy(String::lowercase)
    .joinToString(LIST_SEPARATOR.toString())

private fun String.fromStoredList(): List<String> = split(LIST_SEPARATOR)
    .map(String::trim)
    .filter(String::isNotBlank)

private fun String?.cleanOptional(): String? = this?.trim()?.takeIf(String::isNotBlank)

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default
