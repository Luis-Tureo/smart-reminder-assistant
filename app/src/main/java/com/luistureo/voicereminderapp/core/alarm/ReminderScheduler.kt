package com.luistureo.voicereminderapp.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

class ReminderScheduler(
    private val context: Context
) {

    // Programa una alarma local para mostrar y hablar el recordatorio
    fun scheduleReminder(
        reminderText: String,
        reminderDate: String,
        reminderTime: String,
        triggerTimeMillis: Long
    ) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("reminder_text", reminderText)
            putExtra("reminder_date", reminderDate)
            putExtra("reminder_time", reminderTime)
        }

        val requestCode = System.currentTimeMillis().toInt()

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTimeMillis,
            pendingIntent
        )
    }
}