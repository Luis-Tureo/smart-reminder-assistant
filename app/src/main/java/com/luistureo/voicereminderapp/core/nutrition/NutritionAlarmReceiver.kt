package com.luistureo.voicereminderapp.core.nutrition

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.luistureo.voicereminderapp.data.repository.NutritionRepositoryProvider
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMealStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NutritionAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(EXTRA_ALARM_KIND)
            ?.let { runCatching { NutritionAlarmKind.valueOf(it) }.getOrNull() }
            ?: return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (kind) {
                    NutritionAlarmKind.MEAL -> deliverMeal(context.applicationContext, intent)
                    NutritionAlarmKind.HYDRATION -> deliverHydration(context.applicationContext)
                }
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun deliverMeal(context: Context, intent: Intent) {
        val mealId = intent.getIntExtra(EXTRA_MEAL_ID, 0)
        if (mealId <= 0) return
        val repository = NutritionRepositoryProvider.from(context)
        val item = repository.getMeal(mealId) ?: return
        val expectedUpdatedAt = intent.getLongExtra(EXTRA_EXPECTED_UPDATED_AT, Long.MIN_VALUE)
        val preferences = repository.getPreferences()
        if (
            item.meal.status != NutritionMealStatus.PLANNED ||
            item.meal.updatedAtEpochMillis != expectedUpdatedAt
        ) {
            NutritionScheduler(context).syncMeal(item, preferences)
            return
        }
        NutritionNotificationHelper(context).showMealNotification(
            item,
            privacyModeEnabled = preferences.privacyModeEnabled
        )
    }

    private suspend fun deliverHydration(context: Context) {
        val repository = NutritionRepositoryProvider.from(context)
        val preferences = repository.getPreferences()
        if (!preferences.hydrationEnabled || !preferences.remindersEnabled) return
        NutritionNotificationHelper(context).showHydrationReminder()
        NutritionScheduler(context).scheduleNextHydrationReminder(preferences)
    }

    companion object {
        const val EXTRA_ALARM_KIND = "extra_nutrition_alarm_kind"
        const val EXTRA_MEAL_ID = "extra_nutrition_meal_id"
        const val EXTRA_EXPECTED_UPDATED_AT = "extra_nutrition_expected_updated_at"
    }
}
