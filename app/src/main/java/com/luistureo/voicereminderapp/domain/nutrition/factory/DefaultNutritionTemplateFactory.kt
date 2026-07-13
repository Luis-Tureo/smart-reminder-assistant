package com.luistureo.voicereminderapp.domain.nutrition.factory

import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMealPeriod
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionTemplate
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionTemplateMeal

object DefaultNutritionTemplateFactory {
    fun create(): List<NutritionTemplate> = listOf(
        template(
            key = "organized_day",
            name = "Día organizado de comidas",
            description = "Distribuye opciones de comida durante un día sin exigir completar todos los períodos.",
            complexity = "Baja",
            benefits = "Facilita revisar con anticipación qué preparar y qué comprar.",
            meals = listOf(
                meal(NutritionMealPeriod.BREAKFAST, "Desayuno a elección"),
                meal(NutritionMealPeriod.LUNCH, "Almuerzo planificado"),
                meal(NutritionMealPeriod.DINNER, "Cena sencilla")
            )
        ),
        template(
            key = "simple_breakfasts",
            name = "Desayunos sencillos",
            description = "Organiza ideas editables para comenzar el día.",
            complexity = "Baja",
            benefits = "Reduce decisiones de último momento.",
            meals = listOf(meal(NutritionMealPeriod.BREAKFAST, "Desayuno sencillo"))
        ),
        template(
            key = "quick_snacks",
            name = "Colaciones rápidas",
            description = "Agrupa colaciones opcionales que puedes adaptar o eliminar.",
            complexity = "Baja",
            benefits = "Ayuda a preparar opciones con anticipación.",
            meals = listOf(
                meal(NutritionMealPeriod.MORNING_SNACK, "Colación de mañana"),
                meal(NutritionMealPeriod.AFTERNOON_SNACK, "Colación de tarde")
            )
        ),
        template(
            key = "weekly_prep",
            name = "Preparación semanal",
            description = "Organiza preparaciones base para combinar durante la semana.",
            complexity = "Media",
            benefits = "Permite anticipar tareas de cocina y compras.",
            meals = listOf(
                meal(NutritionMealPeriod.LUNCH, "Preparación base para almuerzo"),
                meal(NutritionMealPeriod.DINNER, "Preparación base para cena")
            )
        ),
        template(
            key = "basic_vegetarian",
            name = "Alimentación vegetariana básica",
            description = "Propone una estructura vegetariana editable sin afirmar que sea adecuada para necesidades médicas.",
            complexity = "Media",
            benefits = "Sirve como punto de partida organizativo.",
            meals = listOf(
                meal(NutritionMealPeriod.BREAKFAST, "Desayuno vegetariano"),
                meal(NutritionMealPeriod.LUNCH, "Almuerzo vegetariano"),
                meal(NutritionMealPeriod.DINNER, "Cena vegetariana")
            )
        ),
        template(
            key = "few_preparations",
            name = "Plan con pocas preparaciones",
            description = "Reutiliza preparaciones elegidas por la persona en más de una comida.",
            complexity = "Baja",
            benefits = "Simplifica la organización de días ocupados.",
            meals = listOf(
                meal(NutritionMealPeriod.LUNCH, "Preparación principal"),
                meal(NutritionMealPeriod.DINNER, "Segunda porción o alternativa")
            )
        ),
        template(
            key = "little_time",
            name = "Comidas para días con poco tiempo",
            description = "Reserva espacios para opciones rápidas elegidas por ti.",
            complexity = "Baja",
            benefits = "Hace visible qué comidas conviene dejar decididas.",
            meals = listOf(
                meal(NutritionMealPeriod.BREAKFAST, "Opción rápida de desayuno"),
                meal(NutritionMealPeriod.LUNCH, "Opción rápida de almuerzo"),
                meal(NutritionMealPeriod.DINNER, "Opción rápida de cena")
            )
        )
    )

    private fun template(
        key: String,
        name: String,
        description: String,
        complexity: String,
        benefits: String,
        meals: List<NutritionTemplateMeal>
    ) = NutritionTemplate(
        name = name,
        description = description,
        preparationComplexity = complexity,
        practicalBenefits = benefits,
        editable = true,
        builtIn = true,
        builtInKey = key,
        meals = meals.mapIndexed { index, meal -> meal.copy(orderPriority = index) }
    )

    private fun meal(period: NutritionMealPeriod, name: String) = NutritionTemplateMeal(
        period = period,
        name = name
    )
}
