package com.luistureo.voicereminderapp.core.nutrition

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.luistureo.voicereminderapp.data.repository.NutritionRepositoryProvider
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMealStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NutritionActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val mealId = intent.getIntExtra(EXTRA_MEAL_ID, 0)
        val action = intent.getStringExtra(EXTRA_ACTION)
            ?.let { runCatching { NutritionNotificationAction.valueOf(it) }.getOrNull() }
        if (mealId <= 0 || action == null) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(context.applicationContext, mealId, action)
            } catch (_: Exception) {
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handle(
        context: Context,
        mealId: Int,
        action: NutritionNotificationAction
    ) {
        val repository = NutritionRepositoryProvider.from(context)
        val scheduler = NutritionScheduler(context)
        when (action) {
            NutritionNotificationAction.COMPLETE -> {
                repository.updateMealStatus(mealId, NutritionMealStatus.COMPLETED)
                scheduler.cancelMeal(mealId)
            }
            NutritionNotificationAction.SKIP -> {
                repository.updateMealStatus(mealId, NutritionMealStatus.SKIPPED)
                scheduler.cancelMeal(mealId)
            }
            NutritionNotificationAction.POSTPONE -> {
                val preferences = repository.getPreferences()
                repository.getMeal(mealId)?.let { scheduler.schedulePostpone(it, preferences) }
                NutritionNotificationHelper(context).cancelMealNotification(mealId)
            }
        }
    }

    companion object {
        const val EXTRA_MEAL_ID = "extra_nutrition_action_meal_id"
        const val EXTRA_ACTION = "extra_nutrition_notification_action"
    }
}
