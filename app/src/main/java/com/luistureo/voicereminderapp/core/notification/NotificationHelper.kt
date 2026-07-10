package com.luistureo.voicereminderapp.core.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.alarm.NextDaySummaryReceiver
import com.luistureo.voicereminderapp.core.routine.RoutineAlarmType
import com.luistureo.voicereminderapp.core.routine.RoutineNotificationAction
import com.luistureo.voicereminderapp.core.routine.RoutineNotificationKind
import com.luistureo.voicereminderapp.core.routine.RoutineNotificationPlan
import com.luistureo.voicereminderapp.core.routine.RoutineNotificationPlanFactory
import com.luistureo.voicereminderapp.core.routine.RoutineActionReceiver
import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionAction
import com.luistureo.voicereminderapp.presentation.routine.RoutineDetailActivity
import com.luistureo.voicereminderapp.presentation.routine.RoutinePostponeActivity
import com.luistureo.voicereminderapp.presentation.assistant.AssistantActivity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class NotificationHelper(
    private val context: Context
) {

    private val reminderChannelId = "reminder_channel"
    private val urgentChannelId = "urgent_reminder_channel"
    private val nextDaySummaryChannelId = "next_day_summary_channel"
    private val routineChannelId = "daily_routine_channel"
    private val routineMotivationChannelId = "daily_routine_motivation_channel"

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

    fun showRoutineNotification(
        plan: RoutineNotificationPlan,
        routineId: Int,
        dateEpochDay: Long,
        alarmType: RoutineAlarmType
    ) {
        createNotificationChannels()
        if (!hasNotificationPermission()) return
        val channelId = if (plan.kind == RoutineNotificationKind.MOTIVATION) {
            routineMotivationChannelId
        } else {
            routineChannelId
        }
        val contentIntent = buildRoutineContentIntent(routineId)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(plan.title)
            .setContentText(plan.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(plan.message))
            .setPriority(
                if (plan.silent) NotificationCompat.PRIORITY_DEFAULT
                else NotificationCompat.PRIORITY_HIGH
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setSilent(plan.silent)
        plan.actions.forEach { action ->
            builder.addAction(
                actionIcon(action),
                context.getString(actionLabel(action)),
                buildRoutineActionIntent(
                    action = action,
                    routineId = routineId,
                    dateEpochDay = dateEpochDay,
                    alarmType = alarmType,
                    notificationId = plan.notificationId,
                    startWithAssistant = plan.startWithAssistant
                )
            )
        }
        if (plan.kind == RoutineNotificationKind.DEADLINE) {
            builder.setDeleteIntent(
                buildRoutineExecutionIntent(
                    action = RoutineExecutionAction.NOT_COMPLETED,
                    routineId = routineId,
                    dateEpochDay = dateEpochDay,
                    notificationId = plan.notificationId
                )
            )
        }
        val publicBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(context.getString(R.string.routine_module_title))
            .setContentText(context.getString(R.string.routine_lock_screen_message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        plan.actions.forEach { action ->
            publicBuilder.addAction(
                actionIcon(action),
                context.getString(actionLabel(action)),
                buildRoutineActionIntent(
                    action = action,
                    routineId = routineId,
                    dateEpochDay = dateEpochDay,
                    alarmType = alarmType,
                    notificationId = plan.notificationId,
                    startWithAssistant = plan.startWithAssistant
                )
            )
        }
        builder.setPublicVersion(publicBuilder.build())
        notifySafely(plan.notificationId, builder.build())
    }

    fun cancelRoutineNotifications(routineId: Int) {
        RoutineNotificationKind.entries.forEach { kind ->
            cancelNotification(RoutineNotificationPlanFactory.notificationId(routineId, kind))
        }
    }

    fun cancelNotification(notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    private fun buildReminderNotificationMessage(
        detail: String,
        date: String,
        time: String
    ): String {
        return "$detail \u2022 $date $time"
    }

    private fun buildRoutineContentIntent(routineId: Int): PendingIntent {
        val intent = Intent(context, RoutineDetailActivity::class.java).apply {
            data = "voicereminder://routine/detail/$routineId".toUri()
            putExtra(RoutineDetailActivity.EXTRA_ROUTINE_ID, routineId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            ROUTINE_CONTENT_INTENT_OFFSET + routineId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildRoutineActionIntent(
        action: RoutineNotificationAction,
        routineId: Int,
        dateEpochDay: Long,
        alarmType: RoutineAlarmType,
        notificationId: Int,
        startWithAssistant: Boolean
    ): PendingIntent {
        if (action == RoutineNotificationAction.START && startWithAssistant) {
            val intent = Intent(context, AssistantActivity::class.java).apply {
                data = "voicereminder://routine/assistant/$routineId/$dateEpochDay".toUri()
                putExtra(AssistantActivity.EXTRA_ROUTINE_ID, routineId)
                putExtra(AssistantActivity.EXTRA_ROUTINE_DATE_EPOCH_DAY, dateEpochDay)
                putExtra(AssistantActivity.EXTRA_ROUTINE_NOTIFICATION_ID, notificationId)
            }
            return PendingIntent.getActivity(
                context,
                routineActionRequestCode(routineId, action, alarmType),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        if (action == RoutineNotificationAction.POSTPONE) {
            val intent = Intent(context, RoutinePostponeActivity::class.java).apply {
                data = "voicereminder://routine/postpone/$routineId/${alarmType.name}".toUri()
                putExtra(RoutinePostponeActivity.EXTRA_ROUTINE_ID, routineId)
                putExtra(RoutinePostponeActivity.EXTRA_ALARM_TYPE, alarmType.name)
                putExtra(RoutinePostponeActivity.EXTRA_DATE_EPOCH_DAY, dateEpochDay)
                putExtra(RoutinePostponeActivity.EXTRA_NOTIFICATION_ID, notificationId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                routineActionRequestCode(routineId, action, alarmType),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val intent = Intent(context, RoutineActionReceiver::class.java).apply {
            data = (
                "voicereminder://routine/action/$routineId/${alarmType.name}/${action.name}"
            ).toUri()
            putExtra(RoutineActionReceiver.EXTRA_ROUTINE_ID, routineId)
            putExtra(RoutineActionReceiver.EXTRA_DATE_EPOCH_DAY, dateEpochDay)
            putExtra(RoutineActionReceiver.EXTRA_NOTIFICATION_ACTION, action.name)
            putExtra(RoutineActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getBroadcast(
            context,
            routineActionRequestCode(routineId, action, alarmType),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun routineActionRequestCode(
        routineId: Int,
        action: RoutineNotificationAction,
        alarmType: RoutineAlarmType
    ): Int = ROUTINE_ACTION_INTENT_OFFSET + routineId * 100 + alarmType.ordinal * 10 + action.ordinal

    private fun buildRoutineExecutionIntent(
        action: RoutineExecutionAction,
        routineId: Int,
        dateEpochDay: Long,
        notificationId: Int
    ): PendingIntent {
        val intent = Intent(context, RoutineActionReceiver::class.java).apply {
            data = "voicereminder://routine/execution/$routineId/${action.name}".toUri()
            putExtra(RoutineActionReceiver.EXTRA_ROUTINE_ID, routineId)
            putExtra(RoutineActionReceiver.EXTRA_DATE_EPOCH_DAY, dateEpochDay)
            putExtra(RoutineActionReceiver.EXTRA_EXECUTION_ACTION, action.name)
            putExtra(RoutineActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getBroadcast(
            context,
            ROUTINE_EXECUTION_INTENT_OFFSET + routineId * 10 + action.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun actionLabel(action: RoutineNotificationAction): Int = when (action) {
        RoutineNotificationAction.START -> R.string.routine_notification_start_action
        RoutineNotificationAction.COMPLETE -> R.string.routine_notification_complete_action
        RoutineNotificationAction.PARTIAL -> R.string.routine_notification_partial_action
        RoutineNotificationAction.POSTPONE -> R.string.routine_notification_postpone_action
    }

    private fun actionIcon(action: RoutineNotificationAction): Int = when (action) {
        RoutineNotificationAction.START -> android.R.drawable.ic_media_play
        RoutineNotificationAction.COMPLETE -> android.R.drawable.checkbox_on_background
        RoutineNotificationAction.PARTIAL -> android.R.drawable.ic_menu_edit
        RoutineNotificationAction.POSTPONE -> android.R.drawable.ic_lock_idle_alarm
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

        val routineChannel = NotificationChannel(
            routineChannelId,
            context.getString(R.string.routine_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.routine_notification_channel_description)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 180, 400)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }

        val routineMotivationChannel = NotificationChannel(
            routineMotivationChannelId,
            context.getString(R.string.routine_motivation_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.routine_motivation_channel_description)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        }

        manager.createNotificationChannel(reminderChannel)
        manager.createNotificationChannel(urgentChannel)
        manager.createNotificationChannel(nextDaySummaryChannel)
        manager.createNotificationChannel(routineChannel)
        manager.createNotificationChannel(routineMotivationChannel)
    }

    private companion object {
        const val ROUTINE_CONTENT_INTENT_OFFSET = 600_000
        const val ROUTINE_ACTION_INTENT_OFFSET = 700_000
        const val ROUTINE_EXECUTION_INTENT_OFFSET = 800_000
    }
}
