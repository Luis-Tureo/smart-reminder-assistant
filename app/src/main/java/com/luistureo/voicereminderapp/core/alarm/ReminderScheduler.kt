package com.luistureo.voicereminderapp.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import com.luistureo.voicereminderapp.core.reminder.ReminderOccurrenceCalculator
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarSyncLogger
import com.luistureo.voicereminderapp.domain.model.Reminder

class ReminderScheduler(
    private val context: Context,
    private val occurrenceCalculator: ReminderOccurrenceCalculator = ReminderOccurrenceCalculator()
) {

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private var syncedReminderIds: Set<Int> = emptySet()

    // Sincroniza alarmas principales y repeticiones urgentes.
    fun syncReminderSchedules(reminders: List<Reminder>) {
        val currentIds = reminders.map { it.id }.toSet()

        (syncedReminderIds - currentIds).forEach { reminderId ->
            cancelReminder(reminderId)
        }

        reminders.forEach { reminder ->
            syncPrimaryAlarm(reminder)
            syncUrgentAlarm(reminder)
        }

        syncedReminderIds = currentIds
    }

    fun syncReminderSchedule(reminder: Reminder) {
        syncPrimaryAlarm(reminder)
        syncUrgentAlarm(reminder)
    }

    fun scheduleReminder(reminder: Reminder) {
        val triggerAtMillis = reminder.scheduleState.nextTriggerAtEpochMillis ?: return

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            data = reminderIntentData(reminder.id, "primary")
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminder.id)
            putExtra(ReminderReceiver.EXTRA_OCCURRENCE_AT, triggerAtMillis)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleAlarmSafely(triggerAtMillis, pendingIntent)
    }

    fun scheduleUrgentRepeat(reminder: Reminder) {
        val triggerAtMillis = reminder.scheduleState.nextUrgentRepeatAtEpochMillis ?: return
        val occurrenceAtEpochMillis = reminder.scheduleState.activeAlertAtEpochMillis ?: return

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            data = reminderIntentData(reminder.id, "urgent")
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminder.id)
            putExtra(ReminderReceiver.EXTRA_OCCURRENCE_AT, occurrenceAtEpochMillis)
            putExtra(ReminderReceiver.EXTRA_IS_URGENT_REPEAT, true)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id + URGENT_REPEAT_OFFSET,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleAlarmSafely(triggerAtMillis, pendingIntent)
    }

    fun cancelReminder(reminderId: Int) {
        cancelPrimaryReminder(reminderId)
        cancelUrgentRepeat(reminderId)
    }

    fun canScheduleExactAlarms(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }

    private fun cancelPrimaryReminder(reminderId: Int) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId,
            Intent(context, ReminderReceiver::class.java).apply {
                data = reminderIntentData(reminderId, "primary")
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun cancelUrgentRepeat(reminderId: Int) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId + URGENT_REPEAT_OFFSET,
            Intent(context, ReminderReceiver::class.java).apply {
                data = reminderIntentData(reminderId, "urgent")
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun scheduleAlarmSafely(
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        val manager = alarmManager

        try {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !manager.canScheduleExactAlarms()
            ) {
                manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                return
            }

            manager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } catch (_: SecurityException) {
            manager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    private fun shouldSchedulePrimaryReminder(reminder: Reminder): Boolean {
        return ReminderAlarmPolicy.shouldSchedulePrimaryReminder(reminder)
    }

    private fun shouldScheduleUrgentRepeat(reminder: Reminder): Boolean {
        return ReminderAlarmPolicy.shouldScheduleUrgentRepeat(reminder)
    }

    private fun syncPrimaryAlarm(reminder: Reminder) {
        if (shouldSchedulePrimaryReminder(reminder)) {
            scheduleReminder(reminder)
        } else {
            val nextTriggerAt = reminder.scheduleState.nextTriggerAtEpochMillis
            if (
                reminder.isSuspended &&
                !reminder.isCompleted &&
                (
                        nextTriggerAt == null ||
                                ReminderAlarmPolicy.isSuspendedOccurrence(reminder, nextTriggerAt)
                        )
            ) {
                CalendarSyncLogger.alarmSkippedForSuspendedAppointment(
                    reminder.originProvider
                )
            }
            cancelPrimaryReminder(reminder.id)
        }
    }

    private fun syncUrgentAlarm(reminder: Reminder) {
        if (shouldScheduleUrgentRepeat(reminder)) {
            scheduleUrgentRepeat(reminder)
        } else {
            cancelUrgentRepeat(reminder.id)
        }
    }

    private fun reminderIntentData(reminderId: Int, kind: String) =
        "voicereminder://alarm/reminder/$reminderId/$kind".toUri()

    companion object {
        private const val URGENT_REPEAT_OFFSET = 100_000
    }
}
