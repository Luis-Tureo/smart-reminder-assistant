package com.luistureo.voicereminderapp.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.luistureo.voicereminderapp.core.notification.NotificationHelper
import com.luistureo.voicereminderapp.core.reminder.ReminderOccurrenceCalculatorCore
import com.luistureo.voicereminderapp.data.local.database.ReminderDatabase
import com.luistureo.voicereminderapp.data.repository.ReminderRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class NextDaySummaryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            val repository = ReminderRepositoryImpl(
                ReminderDatabase.getDatabase(context).reminderDao()
            )
            val tomorrow = LocalDate.now().plusDays(1)
            val timeZoneId = ZoneId.systemDefault().id
            val reminders = repository.getAllReminders().filter { reminder ->
                !reminder.isCompleted && ReminderOccurrenceCalculatorCore.occursOnDate(
                    reminder = reminder,
                    year = tomorrow.year,
                    monthNumber = tomorrow.monthValue,
                    dayOfMonth = tomorrow.dayOfMonth,
                    timeZoneId = timeZoneId
                )
            }

            if (reminders.isNotEmpty()) {
                NotificationHelper(context).showNextDaySummaryNotification(
                    targetDate = tomorrow,
                    reminders = reminders
                )
            }

            ReminderScheduler(context).scheduleNextDaySummary()
            pendingResult.finish()
        }
    }

    companion object {
        const val REQUEST_CODE = 9_001
    }
}
