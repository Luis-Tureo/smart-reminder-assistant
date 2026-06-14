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
import com.luistureo.voicereminderapp.core.alarm.NextDaySummaryReceiver
import com.luistureo.voicereminderapp.domain.model.Reminder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class NotificationHelper(
    private val context: Context
) {

    private val reminderChannelId = "reminder_channel"
    private val urgentChannelId = "urgent_reminder_channel"
    private val nextDaySummaryChannelId = "next_day_summary_channel"

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

    fun showNextDaySummaryNotification(
        targetDate: LocalDate,
        reminders: List<Reminder>
    ) {
        createNotificationChannels()

        if (!hasNotificationPermission()) return

        val locale = Locale.forLanguageTag("es-CL")
        val formatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", locale)
        val title = "Resumen de ma\u00F1ana"
        val body = buildString {
            append("Tienes ")
            append(reminders.size)
            append(if (reminders.size == 1) " recordatorio" else " recordatorios")
            append(" para ")
            append(targetDate.format(formatter))
            append(". ")
            append(
                reminders.take(3).joinToString(separator = " \u2022 ") { reminder ->
                    reminder.title
                }
            )
        }

        val notification = NotificationCompat.Builder(context, nextDaySummaryChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .build()

        notifySafely(NextDaySummaryReceiver.REQUEST_CODE, notification)
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
            NotificationManagerCompat.from(context)
                .notify(notificationId, notification)
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

    // Registra canales separados para normal, urgente y resumen.
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

        val nextDaySummaryChannel = NotificationChannel(
            nextDaySummaryChannelId,
            "Next Day Summary Notifications",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Channel for next-day summary notifications"
            enableVibration(false)
            setSound(null, null)
        }

        manager.createNotificationChannel(reminderChannel)
        manager.createNotificationChannel(urgentChannel)
        manager.createNotificationChannel(nextDaySummaryChannel)
    }
}
