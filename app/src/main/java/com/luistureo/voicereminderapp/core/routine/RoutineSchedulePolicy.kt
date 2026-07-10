package com.luistureo.voicereminderapp.core.routine

import com.luistureo.voicereminderapp.domain.routine.model.Routine
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionState
import com.luistureo.voicereminderapp.domain.routine.service.RoutineExecutionTransitionPolicy
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

data class ScheduledRoutineAlarm(
    val type: RoutineAlarmType,
    val triggerAtEpochMillis: Long,
    val scheduledEpochDay: Long
)

object RoutineSchedulePolicy {
    fun resolve(
        routine: Routine,
        state: RoutineExecutionState,
        now: ZonedDateTime
    ): List<ScheduledRoutineAlarm> {
        if (!routine.enabled) return emptyList()
        val terminalToday = RoutineExecutionTransitionPolicy.isTerminal(state)
        return buildList {
            routine.startTime?.let { time ->
                add(resolveAlarm(RoutineAlarmType.START, time, state != RoutineExecutionState.PENDING, now))
            }
            routine.deadlineTime?.let { time ->
                add(resolveAlarm(RoutineAlarmType.DEADLINE, time, terminalToday, now))
            }
            if (routine.motivationBubbleEnabled) {
                routine.motivationSchedule?.let { time ->
                    add(resolveAlarm(RoutineAlarmType.PENDING_TASKS, time, terminalToday, now))
                }
            }
            if (state == RoutineExecutionState.PENDING || state == RoutineExecutionState.IN_PROGRESS) {
                add(resolveDayClose(now))
            }
        }
    }

    private fun resolveAlarm(
        type: RoutineAlarmType,
        time: LocalTime,
        skipToday: Boolean,
        now: ZonedDateTime
    ): ScheduledRoutineAlarm {
        val todayTrigger = now.toLocalDate().atTime(time).atZone(now.zone)
        val trigger = if (!skipToday && todayTrigger.isAfter(now)) {
            todayTrigger
        } else {
            todayTrigger.plusDays(1)
        }
        return ScheduledRoutineAlarm(
            type = type,
            triggerAtEpochMillis = trigger.toInstant().toEpochMilli(),
            scheduledEpochDay = trigger.toLocalDate().toEpochDay()
        )
    }

    private fun resolveDayClose(now: ZonedDateTime): ScheduledRoutineAlarm {
        val targetDate = now.toLocalDate()
        val trigger = targetDate.atTime(LocalTime.MAX).atZone(now.zone)
        return ScheduledRoutineAlarm(
            type = RoutineAlarmType.DAY_CLOSE,
            triggerAtEpochMillis = trigger.toInstant().toEpochMilli(),
            scheduledEpochDay = targetDate.toEpochDay()
        )
    }
}

object RoutineAlarmDeliveryPolicy {
    fun visibleTargetDate(
        scheduledEpochDay: Long,
        deliveryDate: LocalDate
    ): LocalDate? = runCatching { LocalDate.ofEpochDay(scheduledEpochDay) }
        .getOrNull()
        ?.takeIf { it == deliveryDate }

    fun dayCloseTargetDate(
        scheduledEpochDay: Long,
        deliveryDate: LocalDate
    ): LocalDate? = runCatching { LocalDate.ofEpochDay(scheduledEpochDay) }
        .getOrNull()
        ?.takeIf { !it.isAfter(deliveryDate) }
}
