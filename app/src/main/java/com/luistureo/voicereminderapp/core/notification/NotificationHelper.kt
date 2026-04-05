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

class NotificationHelper(
    private val context: Context
) {

    private val channelId = "reminder_channel"

    // Muestra la notificación
    fun showReminderNotification(
        reminderTitle: String,
        reminderDetail: String,
        reminderDate: String,
        reminderTime: String
    ) {
        createNotificationChannel()

        if (!hasNotificationPermission()) return

        val notificationMessage = "$reminderDetail • $reminderDate $reminderTime"

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(reminderTitle)
            .setContentText(notificationMessage)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(notificationMessage)
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_SOUND)
            .setVibrate(longArrayOf(0, 700, 250, 700))
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(System.currentTimeMillis().toInt(), notification)
        } catch (exception: SecurityException) {
            exception.printStackTrace()
        }
    }

    // Verifica permiso de notificaciones solo en Android 13+
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

    // Crea canal de notificación para Android 8+
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Reminder Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for reminder notifications"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 700, 250, 700)
                setSound(
                    Settings.System.DEFAULT_NOTIFICATION_URI,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .build()
                )
            }

            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}