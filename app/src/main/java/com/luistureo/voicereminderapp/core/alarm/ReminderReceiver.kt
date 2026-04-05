package com.luistureo.voicereminderapp.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.luistureo.voicereminderapp.core.notification.NotificationHelper
import com.luistureo.voicereminderapp.core.speech.ReminderVoiceAssistant

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderTitle = intent.getStringExtra("reminder_title")
            ?: "Tienes un recordatorio"

        val reminderDetail = intent.getStringExtra("reminder_detail")
            ?: "Tienes un recordatorio pendiente"

        val reminderDate = intent.getStringExtra("reminder_date")
            ?: "--/--/----"

        val reminderTime = intent.getStringExtra("reminder_time")
            ?: "--:--"

        val repeatCount = intent.getIntExtra("repeat_count", 0)
        val requestCodeBase = intent.getIntExtra("request_code_base", 0)

        val notificationHelper = NotificationHelper(context)
        notificationHelper.showReminderNotification(
            reminderTitle = reminderTitle,
            reminderDetail = reminderDetail,
            reminderDate = reminderDate,
            reminderTime = reminderTime
        )

        val pendingResult = goAsync()

        val voiceAssistant = ReminderVoiceAssistant(context)
        voiceAssistant.speakReminder(
            reminderText = reminderDetail,
            reminderDate = reminderDate,
            reminderTime = reminderTime,
            onFinished = {
                voiceAssistant.shutdown()
                pendingResult.finish()
            }
        )

        // Repite una segunda vez la alerta 5 segundos después
        if (repeatCount < 1) {
            scheduleRepeat(
                context = context,
                reminderTitle = reminderTitle,
                reminderDetail = reminderDetail,
                reminderDate = reminderDate,
                reminderTime = reminderTime,
                requestCodeBase = requestCodeBase,
                nextRepeatCount = repeatCount + 1
            )
        }
    }

    private fun scheduleRepeat(
        context: Context,
        reminderTitle: String,
        reminderDetail: String,
        reminderDate: String,
        reminderTime: String,
        requestCodeBase: Int,
        nextRepeatCount: Int
    ) {
        val repeatIntent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("reminder_title", reminderTitle)
            putExtra("reminder_detail", reminderDetail)
            putExtra("reminder_date", reminderDate)
            putExtra("reminder_time", reminderTime)
            putExtra("repeat_count", nextRepeatCount)
            putExtra("request_code_base", requestCodeBase)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCodeBase + nextRepeatCount,
            repeatIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAtMillis = System.currentTimeMillis() + 5000L

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
    }
}