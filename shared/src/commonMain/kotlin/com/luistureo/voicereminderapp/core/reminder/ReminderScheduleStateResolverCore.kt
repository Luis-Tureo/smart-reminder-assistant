package com.luistureo.voicereminderapp.core.reminder

import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.model.ReminderScheduleState
import kotlinx.datetime.Clock

// Contiene la parte multiplataforma de la resolucion del estado de agenda.
object ReminderScheduleStateResolverCore {

    fun resolveOnSave(
        reminder: Reminder,
        timeZoneId: String,
        nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds()
    ): ReminderScheduleState {
        val nextTriggerAtEpochMillis = if (reminder.isCompleted) {
            null
        } else {
            ReminderOccurrenceCalculatorCore.resolveNextTriggerAtEpochMillis(
                reminder = reminder,
                fromEpochMillis = nowEpochMillis,
                timeZoneId = timeZoneId
            )
        }

        return ReminderScheduleState(
            nextTriggerAtEpochMillis = nextTriggerAtEpochMillis,
            lastTriggeredAtEpochMillis = reminder.scheduleState.lastTriggeredAtEpochMillis
        )
    }

    fun resolveAfterPrimaryTrigger(
        reminder: Reminder,
        occurrenceAtEpochMillis: Long,
        timeZoneId: String,
        nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds()
    ): ReminderScheduleState {
        val nextTriggerAtEpochMillis = if (reminder.isCompleted) {
            null
        } else {
            ReminderOccurrenceCalculatorCore.resolveNextTriggerAtEpochMillis(
                reminder = reminder,
                fromEpochMillis = occurrenceAtEpochMillis,
                timeZoneId = timeZoneId
            )
        }
        val activeAlertRepeatCount = if (reminder.isUrgent && !reminder.isCompleted) 1 else 0
        val nextUrgentRepeatAtEpochMillis = activeAlertRepeatCount
            .takeIf { it in 1 until MAX_URGENT_ALERT_COUNT }
            ?.let { nowEpochMillis + URGENT_REPEAT_DELAY_MILLIS }

        return ReminderScheduleState(
            nextTriggerAtEpochMillis = nextTriggerAtEpochMillis,
            lastTriggeredAtEpochMillis = nowEpochMillis,
            activeAlertAtEpochMillis = occurrenceAtEpochMillis.takeIf { activeAlertRepeatCount > 0 },
            activeAlertRepeatCount = activeAlertRepeatCount,
            nextUrgentRepeatAtEpochMillis = nextUrgentRepeatAtEpochMillis
        )
    }

    fun resolveAfterUrgentRepeat(
        reminder: Reminder,
        nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds()
    ): ReminderScheduleState {
        if (!reminder.isUrgent || reminder.isCompleted) {
            return clearUrgentAlert(reminder.scheduleState)
        }

        val nextRepeatCount = reminder.scheduleState.activeAlertRepeatCount + 1
        val shouldContinue = nextRepeatCount < MAX_URGENT_ALERT_COUNT

        return reminder.scheduleState.copy(
            activeAlertAtEpochMillis = reminder.scheduleState.activeAlertAtEpochMillis
                .takeIf { shouldContinue },
            activeAlertRepeatCount = nextRepeatCount,
            nextUrgentRepeatAtEpochMillis = if (shouldContinue) {
                nowEpochMillis + URGENT_REPEAT_DELAY_MILLIS
            } else {
                null
            }
        )
    }

    fun clearUrgentAlert(scheduleState: ReminderScheduleState): ReminderScheduleState {
        return scheduleState.copy(
            activeAlertAtEpochMillis = null,
            activeAlertRepeatCount = 0,
            nextUrgentRepeatAtEpochMillis = null
        )
    }

    fun isCurrentPrimaryTrigger(
        reminder: Reminder,
        occurrenceAtEpochMillis: Long
    ): Boolean {
        return reminder.scheduleState.nextTriggerAtEpochMillis == occurrenceAtEpochMillis
    }

    fun isCurrentUrgentRepeat(
        reminder: Reminder,
        occurrenceAtEpochMillis: Long
    ): Boolean {
        return reminder.scheduleState.activeAlertAtEpochMillis == occurrenceAtEpochMillis &&
            reminder.scheduleState.nextUrgentRepeatAtEpochMillis != null &&
            reminder.scheduleState.activeAlertRepeatCount in 1 until MAX_URGENT_ALERT_COUNT
    }

    fun buildNotificationId(
        reminderId: Int,
        occurrenceAtEpochMillis: Long,
        alertCount: Int
    ): Int {
        return "$reminderId-$occurrenceAtEpochMillis-$alertCount".hashCode()
    }

    const val MAX_URGENT_ALERT_COUNT = 3
    const val URGENT_REPEAT_DELAY_MILLIS = 15_000L
}
