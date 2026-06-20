package com.luistureo.voicereminderapp.core.reminder

import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.model.ReminderScheduleState

class ReminderScheduleStateResolver(
    private val occurrenceCalculator: ReminderOccurrenceCalculator = ReminderOccurrenceCalculator()
) {

    fun resolveOnSave(reminder: Reminder): ReminderScheduleState {
        val candidateTriggerAtEpochMillis = if (reminder.isCompleted) {
            null
        } else {
            occurrenceCalculator.resolveNextTriggerAtEpochMillis(reminder)
        }
        val nextTriggerAtEpochMillis = skipSuspendedOccurrence(
            reminder,
            candidateTriggerAtEpochMillis
        )

        return ReminderScheduleState(
            nextTriggerAtEpochMillis = nextTriggerAtEpochMillis,
            lastTriggeredAtEpochMillis = reminder.scheduleState.lastTriggeredAtEpochMillis
        )
    }

    fun resolveAfterPrimaryTrigger(
        reminder: Reminder,
        occurrenceAtEpochMillis: Long,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): ReminderScheduleState {
        val candidateTriggerAtEpochMillis = if (reminder.isCompleted) {
            null
        } else {
            occurrenceCalculator.resolveNextTriggerAtEpochMillis(
                reminder = reminder,
                fromEpochMillis = occurrenceAtEpochMillis
            )
        }
        val nextTriggerAtEpochMillis = skipSuspendedOccurrence(
            reminder,
            candidateTriggerAtEpochMillis
        )
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

    fun resolveAfterSuspendedTrigger(
        reminder: Reminder,
        occurrenceAtEpochMillis: Long
    ): ReminderScheduleState {
        val nextTriggerAtEpochMillis = occurrenceCalculator.resolveNextTriggerAtEpochMillis(
            reminder = reminder,
            fromEpochMillis = occurrenceAtEpochMillis
        )
        return ReminderScheduleState(
            nextTriggerAtEpochMillis = skipSuspendedOccurrence(
                reminder,
                nextTriggerAtEpochMillis
            ),
            lastTriggeredAtEpochMillis = reminder.scheduleState.lastTriggeredAtEpochMillis
        )
    }

    private fun skipSuspendedOccurrence(
        reminder: Reminder,
        candidateAtEpochMillis: Long?
    ): Long? {
        if (!reminder.isSuspended || candidateAtEpochMillis == null) {
            return candidateAtEpochMillis
        }
        if (reminder.suspendedOccurrenceAtEpochMillis != candidateAtEpochMillis) {
            return candidateAtEpochMillis
        }
        if (!reminder.isRecurring) return null

        return occurrenceCalculator.resolveNextTriggerAtEpochMillis(
            reminder = reminder,
            fromEpochMillis = candidateAtEpochMillis
        )
    }

    fun resolveAfterUrgentRepeat(
        reminder: Reminder,
        nowEpochMillis: Long = System.currentTimeMillis()
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

    companion object {
        const val MAX_URGENT_ALERT_COUNT = 3
        const val URGENT_REPEAT_DELAY_MILLIS = 15_000L
    }
}
