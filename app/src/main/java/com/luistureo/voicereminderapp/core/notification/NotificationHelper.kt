package com.luistureo.voicereminderapp.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.luistureo.voicereminderapp.domain.model.Reminder

class NotificationHelper(
    private val context: Context
) {

    private val reminderChannelId = "reminder_channel"
    private val urgentChannelId = "urgent_reminder_channel"

    fun showReminderNotification(
        reminder: Reminder,
        occurrenceAtEpochMillis: Long? = null,
        notificationId: Int = reminder.id.takeIf { it != 0 } ?: System.currentTimeMillis().toInt()
    ) {
        createNotificationChannels()
        if (!hasNotificationPermission()) return

        val nextDate = occurrenceAtEpochMillis?.let {
            com.luistureo.voicereminderapp.core.utils.DateTimeFormatter.formatDateFromEpoch(it)
        } ?: reminder.nextTriggerDate
            ?: reminder.date
        val nextTime = occurrenceAtEpochMillis?.let {
            com.luistureo.voicereminderapp.core.utils.DateTimeFormatter.formatTimeFromEpoch(it)
        } ?: reminder.nextTriggerTime
            ?: reminder.time
        val notificationMessage = buildReminderNotificationMessage(
            detail = reminder.detail,
            date = nextDate,
            time = nextTime
        )
        val targetChannelId = if (reminder.isUrgent) urgentChannelId else reminderChannelId
        val priority = if (reminder.isUrgent) {
            NotificationCompat.PRIORITY_MAX
        } else {
            NotificationCompat.PRIORITY_HIGH
        }

        val notification = NotificationCompat.Builder(context, targetChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(reminder.title)
            .setContentText(notificationMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationMessage))
            .setPriority(priority)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .setVibrate(
                if (reminder.isUrgent) longArrayOf(0, 800, 200, 800, 200, 800)
                else longArrayOf(0, 500, 200, 500)
            )
            .build()

        notifySafely(notificationId, notification)
    }

    private fun buildReminderNotificationMessage(
        detail: String,
        date: String,
        time: String
    ): String {
        return "$detail \u2022 $date $time"
    }

    private fun notifySafely(notificationId: Int, notification: android.app.Notification) {
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    // Registra canales separados para recordatorios normales y urgentes.
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val reminderChannel = NotificationChannel(
            reminderChannelId,
            "Reminder Notifications",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Channel for reminder notifications"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 200, 500)
            setSound(
                Settings.System.DEFAULT_NOTIFICATION_URI,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .build()
            )
        }
        val urgentChannel = NotificationChannel(
            urgentChannelId,
            "Urgent Reminder Notifications",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Channel for urgent reminder notifications"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 800, 200, 800, 200, 800)
            setSound(
                Settings.System.DEFAULT_ALARM_ALERT_URI,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build()
            )
        }
        manager.createNotificationChannel(reminderChannel)
        manager.createNotificationChannel(urgentChannel)
    }
}
