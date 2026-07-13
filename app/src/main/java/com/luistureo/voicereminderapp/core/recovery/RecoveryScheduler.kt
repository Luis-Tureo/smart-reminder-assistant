package com.luistureo.voicereminderapp.core.recovery

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryReminder
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryReminderType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RecoveryScheduler(private val context: Context) {
    private val alarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleNext(reminder: RecoveryReminder, now: ZonedDateTime = ZonedDateTime.now()) {
        cancel(reminder.id)
        if (!reminder.enabled || reminder.id <= 0) return
        if (reminder.type == RecoveryReminderType.MILESTONE) return
        val hour = reminder.timeMinutes.coerceIn(0, 1439) / 60
        val minute = reminder.timeMinutes.coerceIn(0, 1439) % 60
        var trigger = now.toLocalDate().atTime(hour, minute).atZone(now.zone)
        if (!trigger.isAfter(now)) trigger = trigger.plusDays(1)
        scheduleAt(reminder, trigger.toInstant().toEpochMilli())
    }

    fun scheduleSnooze(reminder: RecoveryReminder, nowEpochMillis: Long = System.currentTimeMillis()) {
        val trigger = nowEpochMillis + reminder.snoozeMinutes.coerceIn(5, 120) * 60_000L
        scheduleAt(reminder, trigger)
    }

    fun cancel(reminderId: Int) {
        if (reminderId <= 0) return
        val intent = Intent(context, RecoveryAlarmReceiver::class.java).apply {
            data = alarmUri(reminderId)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_OFFSET + reminderId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pending)
        pending.cancel()
    }

    fun cancelGoal(reminders: List<RecoveryReminder>) = reminders.forEach { cancel(it.id) }

    private fun scheduleAt(reminder: RecoveryReminder, triggerAtEpochMillis: Long) {
        val intent = Intent(context, RecoveryAlarmReceiver::class.java).apply {
            data = alarmUri(reminder.id)
            putExtra(RecoveryAlarmReceiver.EXTRA_REMINDER_ID, reminder.id)
            putExtra(RecoveryAlarmReceiver.EXTRA_EXPECTED_UPDATED_AT, reminder.updatedAtEpochMillis)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_OFFSET + reminder.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMillis, pending)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtEpochMillis, pending)
            }
        } catch (_: SecurityException) {
            runCatching {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtEpochMillis,
                    pending
                )
            }.recoverCatching {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtEpochMillis, pending)
            }
        }
    }

    private fun alarmUri(reminderId: Int) = "voicereminder://recovery/alarm/$reminderId".toUri()

    companion object {
        private const val REQUEST_CODE_OFFSET = 1_300_000
    }
}
