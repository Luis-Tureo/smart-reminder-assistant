package com.luistureo.voicereminderapp.core.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.luistureo.voicereminderapp.core.notification.NotificationHelper
import com.luistureo.voicereminderapp.core.reminder.ReminderScheduleStateResolver
import com.luistureo.voicereminderapp.core.speech.ReminderVoiceAssistant
import com.luistureo.voicereminderapp.core.utils.DateTimeFormatterCore
import com.luistureo.voicereminderapp.data.local.database.ReminderDatabase
import com.luistureo.voicereminderapp.data.repository.ReminderRepositoryImpl
import com.luistureo.voicereminderapp.domain.model.Reminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getIntExtra(EXTRA_REMINDER_ID, 0)
        if (reminderId == 0) return

        val isUrgentRepeat = intent.getBooleanExtra(EXTRA_IS_URGENT_REPEAT, false)
        val requestedOccurrenceAtEpochMillis = intent
            .getLongExtra(EXTRA_OCCURRENCE_AT, 0L)
            .takeIf { it > 0L }
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleReminderTrigger(
                    context = context,
                    reminderId = reminderId,
                    isUrgentRepeat = isUrgentRepeat,
                    requestedOccurrenceAtEpochMillis = requestedOccurrenceAtEpochMillis
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleReminderTrigger(
        context: Context,
        reminderId: Int,
        isUrgentRepeat: Boolean,
        requestedOccurrenceAtEpochMillis: Long?
    ) {
        val repository = ReminderRepositoryImpl(
            ReminderDatabase.getDatabase(context).reminderDao()
        )
        val scheduler = ReminderScheduler(context)
        val scheduleStateResolver = ReminderScheduleStateResolver()
        val reminder = repository.getReminderById(reminderId)

        if (reminder == null || reminder.isCompleted) {
            scheduler.cancelReminder(reminderId)
            scheduler.scheduleNextDaySummary()
            return
        }

        val occurrenceAtEpochMillis = requestedOccurrenceAtEpochMillis ?: resolveOccurrenceAt(
            reminder = reminder,
            isUrgentRepeat = isUrgentRepeat
        )

        if (occurrenceAtEpochMillis == null) {
            scheduler.syncReminderSchedule(reminder)
            return
        }

        val isCurrentTrigger = if (isUrgentRepeat) {
            scheduleStateResolver.isCurrentUrgentRepeat(reminder, occurrenceAtEpochMillis)
        } else {
            scheduleStateResolver.isCurrentPrimaryTrigger(reminder, occurrenceAtEpochMillis)
        }

        if (!isCurrentTrigger) {
            scheduler.syncReminderSchedule(reminder)
            return
        }

        val notificationId = scheduleStateResolver.buildNotificationId(
            reminderId = reminder.id,
            occurrenceAtEpochMillis = occurrenceAtEpochMillis,
            alertCount = resolveAlertCount(reminder, isUrgentRepeat)
        )

        NotificationHelper(context).showReminderNotification(
            reminder = reminder,
            occurrenceAtEpochMillis = occurrenceAtEpochMillis,
            notificationId = notificationId
        )

        if (!isUrgentRepeat) {
            speakReminder(context, reminder, occurrenceAtEpochMillis)
        }

        val updatedReminder = reminder.copy(
            scheduleState = if (isUrgentRepeat) {
                scheduleStateResolver.resolveAfterUrgentRepeat(reminder)
            } else {
                scheduleStateResolver.resolveAfterPrimaryTrigger(
                    reminder = reminder,
                    occurrenceAtEpochMillis = occurrenceAtEpochMillis
                )
            }
        )

        repository.updateReminder(updatedReminder)
        scheduler.syncReminderSchedule(updatedReminder)
    }

    private fun resolveOccurrenceAt(
        reminder: Reminder,
        isUrgentRepeat: Boolean
    ): Long? {
        return if (isUrgentRepeat) {
            reminder.scheduleState.activeAlertAtEpochMillis
        } else {
            reminder.scheduleState.nextTriggerAtEpochMillis
        }
    }

    private fun resolveAlertCount(
        reminder: Reminder,
        isUrgentRepeat: Boolean
    ): Int {
        return if (isUrgentRepeat) {
            reminder.scheduleState.activeAlertRepeatCount + 1
        } else {
            1
        }
    }

    private fun speakReminder(
        context: Context,
        reminder: Reminder,
        occurrenceAtEpochMillis: Long
    ) {
        val voiceAssistant = ReminderVoiceAssistant(context)

        voiceAssistant.speakReminder(
            reminderText = reminder.detail,
            reminderDate = DateTimeFormatterCore.formatDateFromEpoch(occurrenceAtEpochMillis),
            reminderTime = DateTimeFormatterCore.formatTimeFromEpoch(occurrenceAtEpochMillis),
            onFinished = {
                voiceAssistant.shutdown()
            }
        )
    }

    companion object {
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_OCCURRENCE_AT = "extra_occurrence_at"
        const val EXTRA_IS_URGENT_REPEAT = "extra_is_urgent_repeat"
    }
}
