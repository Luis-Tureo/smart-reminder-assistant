package com.luistureo.voicereminderapp.core.reminder

import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.model.ReminderScheduleState
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone

class ReminderScheduleStateResolver() {
    // Mantiene compatibilidad mientras existan llamadas con el calculador legado.
    constructor(@Suppress("UNUSED_PARAMETER") occurrenceCalculator: Any?) : this()

    private val timeZoneId: String = TimeZone.currentSystemDefault().id

    fun resolveOnSave(reminder: Reminder): ReminderScheduleState {
        return ReminderScheduleStateResolverCore.resolveOnSave(
            reminder = reminder,
            timeZoneId = timeZoneId
        )
    }

    fun resolveAfterPrimaryTrigger(
        reminder: Reminder,
        occurrenceAtEpochMillis: Long,
        nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds()
    ): ReminderScheduleState {
        return ReminderScheduleStateResolverCore.resolveAfterPrimaryTrigger(
            reminder = reminder,
            occurrenceAtEpochMillis = occurrenceAtEpochMillis,
            timeZoneId = timeZoneId,
            nowEpochMillis = nowEpochMillis
        )
    }

    fun resolveAfterUrgentRepeat(
        reminder: Reminder,
        nowEpochMillis: Long = Clock.System.now().toEpochMilliseconds()
    ): ReminderScheduleState {
        return ReminderScheduleStateResolverCore.resolveAfterUrgentRepeat(
            reminder = reminder,
            nowEpochMillis = nowEpochMillis
        )
    }

    fun clearUrgentAlert(scheduleState: ReminderScheduleState): ReminderScheduleState {
        return ReminderScheduleStateResolverCore.clearUrgentAlert(
            scheduleState = scheduleState
        )
    }

    fun isCurrentPrimaryTrigger(
        reminder: Reminder,
        occurrenceAtEpochMillis: Long
    ): Boolean {
        return ReminderScheduleStateResolverCore.isCurrentPrimaryTrigger(
            reminder = reminder,
            occurrenceAtEpochMillis = occurrenceAtEpochMillis
        )
    }

    fun isCurrentUrgentRepeat(
        reminder: Reminder,
        occurrenceAtEpochMillis: Long
    ): Boolean {
        return ReminderScheduleStateResolverCore.isCurrentUrgentRepeat(
            reminder = reminder,
            occurrenceAtEpochMillis = occurrenceAtEpochMillis
        )
    }

    fun buildNotificationId(
        reminderId: Int,
        occurrenceAtEpochMillis: Long,
        alertCount: Int
    ): Int {
        return ReminderScheduleStateResolverCore.buildNotificationId(
            reminderId = reminderId,
            occurrenceAtEpochMillis = occurrenceAtEpochMillis,
            alertCount = alertCount
        )
    }

    companion object {
        const val MAX_URGENT_ALERT_COUNT = ReminderScheduleStateResolverCore.MAX_URGENT_ALERT_COUNT
        const val URGENT_REPEAT_DELAY_MILLIS = ReminderScheduleStateResolverCore.URGENT_REPEAT_DELAY_MILLIS
    }
}
