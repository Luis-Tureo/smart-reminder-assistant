package com.luistureo.voicereminderapp.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.luistureo.voicereminderapp.core.notification.NotificationHelper
import com.luistureo.voicereminderapp.core.speech.ReminderVoiceAssistant

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderText = intent.getStringExtra("reminder_text")
            ?: "Tienes un recordatorio"

        val reminderDate = intent.getStringExtra("reminder_date")
            ?: "--/--/----"

        val reminderTime = intent.getStringExtra("reminder_time")
            ?: "--:--"

        val notificationHelper = NotificationHelper(context)
        notificationHelper.showReminderNotification(
            reminderText = reminderText,
            reminderDate = reminderDate,
            reminderTime = reminderTime
        )

        val pendingResult = goAsync()

        val voiceAssistant = ReminderVoiceAssistant(context)
        voiceAssistant.speakReminder(
            reminderText = reminderText,
            reminderDate = reminderDate,
            reminderTime = reminderTime,
            onFinished = {
                voiceAssistant.shutdown()
                pendingResult.finish()
            }
        )
    }
}