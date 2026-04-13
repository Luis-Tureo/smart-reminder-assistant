package com.luistureo.voicereminderapp.core.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.luistureo.voicereminderapp.core.preference.NextDaySummaryPreferenceStore
import com.luistureo.voicereminderapp.core.reminder.ReminderOccurrenceCalculator
import com.luistureo.voicereminderapp.core.reminder.ReminderOccurrenceCalculatorCore
import com.luistureo.voicereminderapp.domain.model.Reminder
import java.time.ZoneId

class ReminderScheduler(
    private val context: Context,
    private val summaryPreferenceStore: NextDaySummaryPreferenceStore =
        NextDaySummaryPreferenceStore(context.applicationContext)
) {
    // Mantiene compatibilidad mientras existan llamadas que inyectan el facade legado.
    constructor(
        context: Context,
        @Suppress("UNUSED_PARAMETER") occurrenceCalculator: ReminderOccurrenceCalculator,
        summaryPreferenceStore: NextDaySummaryPreferenceStore =
            NextDaySummaryPreferenceStore(context.applicationContext)
    ) : this(
        context = context,
        summaryPreferenceStore = summaryPreferenceStore
    )

    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val timeZoneId: String = ZoneId.systemDefault().id
    private var syncedReminderIds: Set<Int> = emptySet()

    // Sincroniza alarmas principales, repeticiones urgentes y el resumen global.
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
        scheduleNextDaySummary()
    }

    fun syncReminderSchedule(reminder: Reminder) {
        syncPrimaryAlarm(reminder)
        syncUrgentAlarm(reminder)
        scheduleNextDaySummary()
    }

    fun scheduleReminder(reminder: Reminder) {
        val triggerAtMillis = reminder.scheduleState.nextTriggerAtEpochMillis ?: return

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminder.id)
            putExtra(ReminderReceiver.EXTRA_OCCURRENCE_AT, triggerAtMillis)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
    }

    fun scheduleUrgentRepeat(reminder: Reminder) {
        val triggerAtMillis = reminder.scheduleState.nextUrgentRepeatAtEpochMillis ?: return
        val occurrenceAtEpochMillis = reminder.scheduleState.activeAlertAtEpochMillis ?: return

        val intent = Intent(context, ReminderReceiver::class.java).apply {
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

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
    }

    fun cancelReminder(reminderId: Int) {
        cancelPrimaryReminder(reminderId)
        cancelUrgentRepeat(reminderId)
    }

    fun scheduleNextDaySummary() {
        val summaryTime = summaryPreferenceStore.getSummaryTime()
        val triggerAtMillis = ReminderOccurrenceCalculatorCore.resolveNextGlobalSummaryTrigger(
            summaryHour = summaryTime.hour,
            summaryMinute = summaryTime.minute,
            nowEpochMillis = System.currentTimeMillis(),
            timeZoneId = timeZoneId
        )
        val intent = Intent(context, NextDaySummaryReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            NextDaySummaryReceiver.REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
    }

    private fun cancelPrimaryReminder(reminderId: Int) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun cancelUrgentRepeat(reminderId: Int) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId + URGENT_REPEAT_OFFSET,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return

        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun shouldSchedulePrimaryReminder(reminder: Reminder): Boolean {
        return reminder.scheduleState.nextTriggerAtEpochMillis != null && !reminder.isCompleted
    }

    private fun shouldScheduleUrgentRepeat(reminder: Reminder): Boolean {
        return reminder.isUrgent &&
                !reminder.isCompleted &&
                reminder.scheduleState.activeAlertAtEpochMillis != null &&
                reminder.scheduleState.nextUrgentRepeatAtEpochMillis != null
    }

    private fun syncPrimaryAlarm(reminder: Reminder) {
        if (shouldSchedulePrimaryReminder(reminder)) {
            scheduleReminder(reminder)
        } else {
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

    companion object {
        private const val URGENT_REPEAT_OFFSET = 100_000
    }
}
