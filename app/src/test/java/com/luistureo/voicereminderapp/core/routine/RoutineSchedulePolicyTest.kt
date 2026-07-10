package com.luistureo.voicereminderapp.core.routine

import com.luistureo.voicereminderapp.domain.routine.model.Routine
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionState
import com.luistureo.voicereminderapp.domain.routine.model.RoutinePeriod
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineSchedulePolicyTest {
    private val zone = ZoneId.of("America/Santiago")
    private val now = ZonedDateTime.of(2026, 7, 9, 7, 0, 0, 0, zone)

    @Test
    fun pendingRoutineSchedulesStartDeadlineAndPendingTasksForToday() {
        val routine = routine(
            startTime = LocalTime.of(8, 0),
            deadlineTime = LocalTime.of(10, 0),
            motivationSchedule = LocalTime.of(9, 0)
        )

        val alarms = RoutineSchedulePolicy.resolve(
            routine,
            RoutineExecutionState.PENDING,
            now
        ).associateBy { it.type }

        assertEquals(
            setOf(
                RoutineAlarmType.START,
                RoutineAlarmType.DEADLINE,
                RoutineAlarmType.PENDING_TASKS,
                RoutineAlarmType.DAY_CLOSE
            ),
            alarms.keys
        )
        assertAlarmAt(alarms.getValue(RoutineAlarmType.START), LocalTime.of(8, 0))
        assertAlarmAt(alarms.getValue(RoutineAlarmType.PENDING_TASKS), LocalTime.of(9, 0))
        assertAlarmAt(alarms.getValue(RoutineAlarmType.DEADLINE), LocalTime.of(10, 0))
    }

    @Test
    fun disabledRoutineDoesNotScheduleNotifications() {
        val alarms = RoutineSchedulePolicy.resolve(
            routine(
                enabled = false,
                startTime = LocalTime.of(8, 0),
                deadlineTime = LocalTime.of(10, 0),
                motivationSchedule = LocalTime.of(9, 0)
            ),
            RoutineExecutionState.PENDING,
            now
        )

        assertTrue(alarms.isEmpty())
    }

    @Test
    fun editedRoutineTimesProduceOnlyTheUpdatedSchedule() {
        val original = routine(
            startTime = LocalTime.of(8, 0),
            deadlineTime = LocalTime.of(10, 0),
            motivationSchedule = LocalTime.of(9, 0)
        )
        val edited = original.copy(
            startTime = LocalTime.of(8, 30),
            deadlineTime = LocalTime.of(11, 15),
            motivationSchedule = LocalTime.of(10, 30),
            updatedAtEpochMillis = 2L
        )

        val originalAlarms = RoutineSchedulePolicy.resolve(
            original,
            RoutineExecutionState.PENDING,
            now
        ).associateBy { it.type }
        val editedAlarms = RoutineSchedulePolicy.resolve(
            edited,
            RoutineExecutionState.PENDING,
            now
        ).associateBy { it.type }

        assertAlarmAt(editedAlarms.getValue(RoutineAlarmType.START), LocalTime.of(8, 30))
        assertAlarmAt(editedAlarms.getValue(RoutineAlarmType.PENDING_TASKS), LocalTime.of(10, 30))
        assertAlarmAt(editedAlarms.getValue(RoutineAlarmType.DEADLINE), LocalTime.of(11, 15))
        assertTrue(
            RoutineAlarmType.entries
                .filter { it != RoutineAlarmType.DAY_CLOSE }
                .filter { it in originalAlarms && it in editedAlarms }
                .all { originalAlarms.getValue(it).triggerAtEpochMillis != editedAlarms.getValue(it).triggerAtEpochMillis }
        )
    }

    @Test
    fun completedRoutineDefersDailyAlarmsUntilTomorrow() {
        val routine = routine(
            startTime = LocalTime.of(8, 0),
            deadlineTime = LocalTime.of(10, 0),
            motivationSchedule = LocalTime.of(9, 0)
        )

        val alarms = RoutineSchedulePolicy.resolve(
            routine,
            RoutineExecutionState.COMPLETED,
            now
        )

        assertTrue(alarms.isNotEmpty())
        assertTrue(alarms.all { it.scheduledEpochDay == now.toLocalDate().plusDays(1).toEpochDay() })
    }

    @Test
    fun disabledMotivationBubbleDoesNotSchedulePendingTasksAlarm() {
        val alarms = RoutineSchedulePolicy.resolve(
            routine(
                startTime = LocalTime.of(8, 0),
                deadlineTime = LocalTime.of(10, 0),
                motivationSchedule = LocalTime.of(9, 0),
                motivationBubbleEnabled = false
            ),
            RoutineExecutionState.PENDING,
            now
        )

        assertEquals(
            setOf(RoutineAlarmType.START, RoutineAlarmType.DEADLINE, RoutineAlarmType.DAY_CLOSE),
            alarms.map { it.type }.toSet()
        )
    }

    @Test
    fun inProgressRoutineSchedulesDeterministicDayClose() {
        val alarm = RoutineSchedulePolicy.resolve(
            routine(startTime = LocalTime.of(8, 0)),
            RoutineExecutionState.IN_PROGRESS,
            now
        ).single { it.type == RoutineAlarmType.DAY_CLOSE }

        assertEquals(now.toLocalDate().toEpochDay(), alarm.scheduledEpochDay)
        assertEquals(
            now.toLocalDate().atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli(),
            alarm.triggerAtEpochMillis
        )
    }

    @Test
    fun staleVisibleAlarmIsRejectedButLateDayCloseCanFinalizeItsLogicalDay() {
        val yesterday = now.toLocalDate().minusDays(1).toEpochDay()

        assertEquals(null, RoutineAlarmDeliveryPolicy.visibleTargetDate(yesterday, now.toLocalDate()))
        assertEquals(
            now.toLocalDate().minusDays(1),
            RoutineAlarmDeliveryPolicy.dayCloseTargetDate(yesterday, now.toLocalDate())
        )
    }

    private fun assertAlarmAt(alarm: ScheduledRoutineAlarm, time: LocalTime) {
        val expected = LocalDate.of(2026, 7, 9).atTime(time).atZone(zone)
        assertEquals(expected.toInstant().toEpochMilli(), alarm.triggerAtEpochMillis)
        assertEquals(expected.toLocalDate().toEpochDay(), alarm.scheduledEpochDay)
    }

    private fun routine(
        enabled: Boolean = true,
        startTime: LocalTime? = null,
        deadlineTime: LocalTime? = null,
        motivationSchedule: LocalTime? = null,
        motivationBubbleEnabled: Boolean = true
    ) = Routine(
        id = 7,
        name = "Rutina",
        description = "Descripci\u00f3n",
        category = "Bienestar",
        icon = "morning",
        color = 0,
        enabled = enabled,
        period = RoutinePeriod.MORNING,
        startTime = startTime,
        deadlineTime = deadlineTime,
        motivationBubbleEnabled = motivationBubbleEnabled,
        motivationSchedule = motivationSchedule,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L
    )
}
