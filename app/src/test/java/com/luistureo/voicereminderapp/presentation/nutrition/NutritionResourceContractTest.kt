package com.luistureo.voicereminderapp.presentation.nutrition

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionResourceContractTest {
    @Test
    fun homeCardKeepsRequestedOrderAndOpensRealDashboard() {
        val layout = file("app/src/main/res/layout/activity_main.xml").readText()
        val strings = file("app/src/main/res/values/strings.xml").readText() +
            file("app/src/main/res/values/wellness_home_strings.xml").readText()
        val main = file(
            "app/src/main/java/com/luistureo/voicereminderapp/MainActivity.kt"
        ).readText()

        assertTrue(strings.contains("Mi día"))
        assertTrue(strings.contains("Organiza tus tareas, rutinas y compromisos"))
        assertTrue(layout.indexOf("cardCalendar") < layout.indexOf("cardLoan"))
        assertTrue(layout.indexOf("cardLoan") < layout.indexOf("cardDailyRoutines"))
        assertTrue(layout.indexOf("cardDailyRoutines") < layout.indexOf("cardNutrition"))
        assertTrue(layout.indexOf("cardNutrition") < layout.indexOf("cardRecovery"))
        assertTrue(main.contains("R.id.cardNutrition"))
        assertTrue(main.contains("Intent(this, NutritionDashboardActivity::class.java)"))
    }

    @Test
    fun manifestRegistersEveryWorkingScreenAndLocalReceiver() {
        val manifest = file("app/src/main/AndroidManifest.xml").readText()
        listOf(
            "NutritionDashboardActivity",
            "NutritionPlanningActivity",
            "NutritionMealEditorActivity",
            "NutritionHydrationActivity",
            "NutritionShoppingActivity",
            "NutritionTemplatesActivity",
            "NutritionTemplatePreviewActivity",
            "NutritionPreferencesActivity",
            "NutritionStatisticsActivity",
            "NutritionAlarmReceiver",
            "NutritionActionReceiver"
        ).forEach { name -> assertTrue("Falta $name", manifest.contains(name)) }
    }

    @Test
    fun layoutsExposeSafeFunctionalFlowsWithoutPlaceholders() {
        val dashboard = file("app/src/main/res/layout/activity_nutrition_dashboard.xml").readText()
        val editor = file("app/src/main/res/layout/activity_nutrition_meal_editor.xml").readText()
        val planning = file("app/src/main/res/layout/activity_nutrition_planning.xml").readText()
        val hydration = file("app/src/main/res/layout/activity_nutrition_hydration.xml").readText()
        val shopping = file("app/src/main/res/layout/activity_nutrition_shopping.xml").readText()
        val preview = file("app/src/main/res/layout/activity_nutrition_template_preview.xml").readText()
        val preferences = file("app/src/main/res/layout/activity_nutrition_preferences.xml").readText()
        val statistics = file("app/src/main/res/layout/activity_nutrition_statistics.xml").readText()
        val strings = file("app/src/main/res/values/nutrition_strings.xml").readText()

        assertTrue(dashboard.contains("@string/nutrition_disclaimer"))
        assertTrue(dashboard.contains("btnNutritionQuickAdd"))
        assertTrue(editor.contains("spinnerNutritionMealPeriod"))
        assertTrue(editor.contains("inputNutritionMealName"))
        assertTrue(editor.contains("checkNutritionAdditionalOptions"))
        assertTrue(editor.contains("groupNutritionMealAdditional"))
        assertTrue(planning.contains("spinnerNutritionPlanningMode"))
        assertTrue(planning.contains("btnNutritionCopyDay"))
        assertTrue(hydration.contains("nutrition_hydration_neutral"))
        assertTrue(hydration.contains("btnNutritionHydrationAddCustom"))
        assertTrue(shopping.contains("btnNutritionShoppingFromPlan"))
        assertTrue(shopping.contains("btnNutritionShoppingRemoveCompleted"))
        assertTrue(preview.contains("btnNutritionPreviewMealEdit").not())
        assertTrue(
            file("app/src/main/res/layout/item_nutrition_template_preview_meal.xml")
                .readText().contains("btnNutritionPreviewMealEdit")
        )
        assertTrue(preview.contains("btnApplyNutritionTemplate"))
        assertTrue(preferences.contains("inputNutritionAllergies"))
        assertTrue(preferences.contains("tvNutritionAllergyWarning"))
        assertTrue(preferences.contains("checkNutritionGoalMealTimes"))
        assertTrue(statistics.contains("NutritionChartView"))
        assertTrue(strings.contains("No reemplaza la orientación de profesionales de salud"))
        assertFalse(strings.contains("Fracasaste", ignoreCase = true))
    }

    @Test
    fun remindersCalculateAllModesAndCancelOnTerminalChanges() {
        val scheduler = file(
            "app/src/main/java/com/luistureo/voicereminderapp/core/nutrition/NutritionScheduler.kt"
        ).readText()
        val viewModel = file(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/nutrition/viewmodel/NutritionViewModel.kt"
        ).readText()
        val coordinator = file(
            "app/src/main/java/com/luistureo/voicereminderapp/core/alarm/AppScheduleCoordinator.kt"
        ).readText()

        assertTrue(scheduler.contains("NutritionReminderType.EXACT_TIME"))
        assertTrue(scheduler.contains("NutritionReminderType.MINUTES_BEFORE"))
        assertTrue(scheduler.contains("minusMinutes"))
        assertTrue(scheduler.contains("NutritionReminderType.CUSTOM"))
        assertTrue(scheduler.contains("cancelMeal(item.meal.id)"))
        assertTrue(scheduler.contains("cancelPostponedMeal"))
        assertTrue(scheduler.contains("cancelMealNotification"))
        assertTrue(viewModel.contains("scheduler.cancelMeal(item.meal.id)"))
        assertTrue(coordinator.contains("NutritionScheduler(appContext).syncAll(repository)"))
    }

    @Test
    fun disabledPeriodsCannotBeScheduledOrPostponed() {
        val scheduler = file(
            "app/src/main/java/com/luistureo/voicereminderapp/core/nutrition/NutritionScheduler.kt"
        ).readText()
        val viewModel = file(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/nutrition/viewmodel/NutritionViewModel.kt"
        ).readText()

        assertTrue(
            scheduler.countOccurrences("item.meal.period !in preferences.enabledMealPeriods") >= 2
        )
        assertTrue(scheduler.contains("fun syncAll(repository: NutritionRepository)"))
        assertFalse(viewModel.contains("syncMeal(saved, preferences.remindersEnabled)"))
        assertTrue(viewModel.contains("scheduler.syncMeal(saved, preferences)"))
    }

    @Test
    fun notificationAndPhotoPrivacyRemainLocal() {
        val notification = file(
            "app/src/main/java/com/luistureo/voicereminderapp/core/nutrition/NutritionNotificationHelper.kt"
        ).readText()
        val photos = file(
            "app/src/main/java/com/luistureo/voicereminderapp/core/nutrition/NutritionPhotoStore.kt"
        ).readText()
        val editor = file(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/nutrition/NutritionMealEditorActivity.kt"
        ).readText()
        val paths = file("app/src/main/res/xml/file_paths.xml").readText()

        assertTrue(notification.contains("if (privacyModeEnabled)"))
        assertTrue(notification.contains("nutrition_notification_private_text"))
        assertTrue(notification.contains("setPublicVersion"))
        assertTrue(photos.contains("context.filesDir"))
        assertTrue(photos.contains("FileProvider.getUriForFile"))
        assertTrue(photos.contains("MAX_BYTES"))
        assertTrue(photos.contains("deleteIfManaged"))
        assertTrue(paths.contains("<files-path"))
        assertTrue(paths.contains("nutrition_photos"))
        assertFalse(editor.contains("takePersistableUriPermission"))
    }

    @Test
    fun nutritionHasNoCalendarCloudNetworkAiOrSensitiveLoggingDependency() {
        val roots = listOf(
            file("app/src/main/java/com/luistureo/voicereminderapp/core/nutrition"),
            file("app/src/main/java/com/luistureo/voicereminderapp/domain/nutrition"),
            file("app/src/main/java/com/luistureo/voicereminderapp/presentation/nutrition")
        )
        val content = roots.flatMap { root -> root.walkTopDown().filter { it.extension == "kt" }.toList() }
            .joinToString("\n") { it.readText() }
        listOf(
            "core.calendar",
            "GoogleCalendar",
            "MicrosoftCalendar",
            "UnifiedCalendar",
            "okhttp",
            "https://",
            "http://",
            "Firebase",
            "RemoteAssistantTtsClient",
            "Log.d(",
            "Log.i("
        ).forEach { forbidden ->
            assertFalse("Dependencia prohibida: $forbidden", content.contains(forbidden))
        }
    }

    @Test
    fun allergiesGoalsAndPreferencesUseOnlyRoomLocalTables() {
        val entity = file(
            "app/src/main/java/com/luistureo/voicereminderapp/data/local/entity/NutritionEntities.kt"
        ).readText()
        val dao = file(
            "app/src/main/java/com/luistureo/voicereminderapp/data/local/dao/NutritionDao.kt"
        ).readText()
        val migration = file(
            "app/src/main/java/com/luistureo/voicereminderapp/data/local/database/ReminderDatabase.kt"
        ).readText()

        assertTrue(entity.contains("allergiesOrIntolerances"))
        assertTrue(entity.contains("organizationalGoals"))
        assertTrue(entity.contains("enabledMealPeriods"))
        assertTrue(dao.contains("nutrition_preferences"))
        assertTrue(dao.contains("savePreferences"))
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS nutrition_preferences"))
        assertFalse(migration.contains("fallbackToDestructiveMigration"))
    }

    private fun String.countOccurrences(value: String): Int =
        windowed(value.length, 1).count { it == value }

    private fun file(path: String): File = File(path).takeIf(File::exists)
        ?: File(path.removePrefix("app/"))
}
