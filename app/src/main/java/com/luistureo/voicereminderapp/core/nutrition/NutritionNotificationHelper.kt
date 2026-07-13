package com.luistureo.voicereminderapp.core.nutrition

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.domain.nutrition.model.DatedNutritionMeal
import com.luistureo.voicereminderapp.presentation.nutrition.NutritionHydrationActivity
import com.luistureo.voicereminderapp.presentation.nutrition.NutritionMealEditorActivity

class NutritionNotificationHelper(context: Context) {
    private val appContext = context.applicationContext

    fun showMealNotification(item: DatedNutritionMeal, privacyModeEnabled: Boolean = true) {
        createChannel()
        if (!hasPermission()) return
        val contentIntent = mealContentIntent(item.meal.id)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(appContext.getString(R.string.nutrition_notification_meal_title))
            .setContentText(
                if (privacyModeEnabled) {
                    appContext.getString(R.string.nutrition_notification_private_text)
                } else {
                    item.meal.name
                }
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    if (privacyModeEnabled) {
                        appContext.getString(R.string.nutrition_notification_private_text)
                    } else {
                        item.meal.name
                    }
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(
                android.R.drawable.ic_menu_view,
                appContext.getString(R.string.nutrition_notification_view),
                contentIntent
            )
            .addAction(
                android.R.drawable.checkbox_on_background,
                appContext.getString(R.string.nutrition_notification_complete),
                actionIntent(item.meal.id, NutritionNotificationAction.COMPLETE)
            )
            .addAction(
                android.R.drawable.ic_lock_idle_alarm,
                appContext.getString(R.string.nutrition_notification_postpone),
                actionIntent(item.meal.id, NutritionNotificationAction.POSTPONE)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                appContext.getString(R.string.nutrition_notification_skip),
                actionIntent(item.meal.id, NutritionNotificationAction.SKIP)
            )
            .setPublicVersion(
                NotificationCompat.Builder(appContext, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle(appContext.getString(R.string.nutrition_module_title))
                    .setContentText(appContext.getString(R.string.nutrition_notification_private_text))
                    .setContentIntent(contentIntent)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .build()
            )
            .build()
        notifySafely(mealNotificationId(item.meal.id), notification)
    }

    fun showHydrationReminder() {
        createChannel()
        if (!hasPermission()) return
        val intent = Intent(appContext, NutritionHydrationActivity::class.java).apply {
            data = "voicereminder://nutrition/hydration".toUri()
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            appContext,
            HYDRATION_CONTENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(appContext.getString(R.string.nutrition_hydration_title))
            .setContentText(appContext.getString(R.string.nutrition_hydration_reminder_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        notifySafely(HYDRATION_NOTIFICATION_ID, notification)
    }

    fun cancelMealNotification(mealId: Int) {
        NotificationManagerCompat.from(appContext).cancel(mealNotificationId(mealId))
    }

    private fun mealContentIntent(mealId: Int): PendingIntent {
        val intent = Intent(appContext, NutritionMealEditorActivity::class.java).apply {
            data = "voicereminder://nutrition/meal/$mealId".toUri()
            putExtra(NutritionMealEditorActivity.EXTRA_MEAL_ID, mealId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            appContext,
            CONTENT_REQUEST_CODE_OFFSET + mealId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun actionIntent(mealId: Int, action: NutritionNotificationAction): PendingIntent {
        val intent = Intent(appContext, NutritionActionReceiver::class.java).apply {
            data = "voicereminder://nutrition/action/$mealId/${action.name}".toUri()
            putExtra(NutritionActionReceiver.EXTRA_MEAL_ID, mealId)
            putExtra(NutritionActionReceiver.EXTRA_ACTION, action.name)
        }
        return PendingIntent.getBroadcast(
            appContext,
            ACTION_REQUEST_CODE_OFFSET + mealId * 10 + action.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.nutrition_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = appContext.getString(R.string.nutrition_notification_channel_description)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
        )
    }

    private fun hasPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    private fun notifySafely(id: Int, notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(appContext).notify(id, notification)
        } catch (_: SecurityException) {
        }
    }

    companion object {
        private const val CHANNEL_ID = "nutrition_local_reminders"
        private const val NOTIFICATION_ID_OFFSET = 1_100_000
        private const val CONTENT_REQUEST_CODE_OFFSET = 1_200_000
        private const val ACTION_REQUEST_CODE_OFFSET = 1_300_000
        private const val HYDRATION_NOTIFICATION_ID = 1_199_999
        private const val HYDRATION_CONTENT_REQUEST_CODE = 1_299_999
        fun mealNotificationId(mealId: Int): Int = NOTIFICATION_ID_OFFSET + mealId
    }
}

enum class NutritionNotificationAction { COMPLETE, POSTPONE, SKIP }
