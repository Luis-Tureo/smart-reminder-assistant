package com.luistureo.voicereminderapp.core.recovery

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
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryReminder
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryReminderType
import com.luistureo.voicereminderapp.presentation.recovery.RecoveryCheckInActivity
import com.luistureo.voicereminderapp.presentation.recovery.RecoveryDashboardActivity
import com.luistureo.voicereminderapp.presentation.recovery.RecoverySupportActivity

enum class RecoveryNotificationAction { REGISTER, SUPPORT, POSTPONE, SKIP }

class RecoveryNotificationHelper(private val context: Context) {
    fun show(reminder: RecoveryReminder) {
        createChannel()
        if (!hasPermission()) return
        val mode = RecoveryPreferenceStore(context).notificationTextMode()
        val fullTitle = when (reminder.type) {
            RecoveryReminderType.DAILY_CHECK_IN -> context.getString(R.string.recovery_notification_checkin_title)
            RecoveryReminderType.PERSONAL_MOTIVATION,
            RecoveryReminderType.MILESTONE -> context.getString(R.string.recovery_notification_motivation_title)
            RecoveryReminderType.HIGH_RISK_TIME,
            RecoveryReminderType.SUPPORT -> context.getString(R.string.recovery_notification_support_title)
        }
        val fullText = when (reminder.type) {
            RecoveryReminderType.DAILY_CHECK_IN -> context.getString(R.string.recovery_notification_checkin_text)
            RecoveryReminderType.PERSONAL_MOTIVATION,
            RecoveryReminderType.MILESTONE -> context.getString(R.string.recovery_notification_motivation_text)
            RecoveryReminderType.HIGH_RISK_TIME,
            RecoveryReminderType.SUPPORT -> context.getString(R.string.recovery_notification_support_text)
        }
        val discreetTitle = context.getString(R.string.recovery_notification_discreet_title)
        val discreetText = context.getString(R.string.recovery_notification_discreet_text)
        val title = if (mode == RecoveryNotificationTextMode.FULL) fullTitle else discreetTitle
        val text = if (mode == RecoveryNotificationTextMode.FULL) fullText else discreetText
        val notificationId = notificationId(reminder.id)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent(reminder.goalId))
            .addAction(
                android.R.drawable.ic_menu_edit,
                context.getString(R.string.recovery_notification_register),
                activityAction(reminder, RecoveryNotificationAction.REGISTER)
            )
            .addAction(
                android.R.drawable.ic_menu_help,
                context.getString(R.string.recovery_notification_support),
                activityAction(reminder, RecoveryNotificationAction.SUPPORT)
            )
            .addAction(
                android.R.drawable.ic_lock_idle_alarm,
                context.getString(R.string.recovery_notification_postpone),
                broadcastAction(reminder, RecoveryNotificationAction.POSTPONE, notificationId)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.recovery_notification_skip),
                broadcastAction(reminder, RecoveryNotificationAction.SKIP, notificationId)
            )

        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(discreetTitle)
            .setContentText(discreetText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(contentIntent(reminder.goalId))
            .setAutoCancel(true)
            .build()
        builder.setPublicVersion(publicVersion)
        runCatching { NotificationManagerCompat.from(context).notify(notificationId, builder.build()) }
    }

    fun cancel(reminderId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId(reminderId))
    }

    private fun contentIntent(goalId: Int): PendingIntent {
        val intent = Intent(context, RecoveryDashboardActivity::class.java).apply {
            data = "voicereminder://recovery/dashboard/$goalId".toUri()
            putExtra(RecoveryDashboardActivity.EXTRA_GOAL_ID, goalId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            CONTENT_OFFSET + goalId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun activityAction(
        reminder: RecoveryReminder,
        action: RecoveryNotificationAction
    ): PendingIntent {
        val target = if (action == RecoveryNotificationAction.REGISTER) {
            RecoveryCheckInActivity::class.java
        } else {
            RecoverySupportActivity::class.java
        }
        val intent = Intent(context, target).apply {
            data = "voicereminder://recovery/action/${action.name}/${reminder.id}".toUri()
            putExtra(RecoveryDashboardActivity.EXTRA_GOAL_ID, reminder.goalId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            ACTION_OFFSET + reminder.id * 10 + action.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun broadcastAction(
        reminder: RecoveryReminder,
        action: RecoveryNotificationAction,
        notificationId: Int
    ): PendingIntent {
        val intent = Intent(context, RecoveryActionReceiver::class.java).apply {
            data = "voicereminder://recovery/action/${action.name}/${reminder.id}".toUri()
            putExtra(RecoveryActionReceiver.EXTRA_REMINDER_ID, reminder.id)
            putExtra(RecoveryActionReceiver.EXTRA_ACTION, action.name)
            putExtra(RecoveryActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getBroadcast(
            context,
            ACTION_OFFSET + reminder.id * 10 + action.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.recovery_notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.recovery_notification_channel_description)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
        )
    }

    private fun hasPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    companion object {
        private const val CHANNEL_ID = "personal_review_channel"
        private const val NOTIFICATION_OFFSET = 1_200_000
        private const val CONTENT_OFFSET = 1_210_000
        private const val ACTION_OFFSET = 1_220_000
        fun notificationId(reminderId: Int) = NOTIFICATION_OFFSET + reminderId
    }
}
