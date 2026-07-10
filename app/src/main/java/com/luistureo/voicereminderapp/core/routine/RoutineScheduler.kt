package com.luistureo.voicereminderapp.core.routine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import com.luistureo.voicereminderapp.domain.routine.model.Routine
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionState
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class RoutineScheduler(
    private val context: Context,
    private val preferenceStore: RoutinePreferenceStore = RoutinePreferenceStore(context)
) {
    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun syncRoutine(
        routine: Routine,
        state: RoutineExecutionState,
        now: ZonedDateTime = ZonedDateTime.now()
    ) {
        cancelBaseAlarms(routine.id)
        if (!routine.enabled) {
            cancelSnoozedAlarms(routine.id)
            return
        }
        val alarms = RoutineSchedulePolicy.resolve(routine, state, now)
        alarms.forEach { alarm ->
            schedule(routine, alarm.type, alarm.triggerAtEpochMillis, alarm.scheduledEpochDay)
        }
        alarms.firstOrNull { it.type == RoutineAlarmType.DAY_CLOSE }?.let { alarm ->
            preferenceStore.setPendingDayClose(routine.id, alarm.scheduledEpochDay)
        } ?: preferenceStore.clearPendingDayClose(routine.id)
    }

    fun replaceRoutineSchedule(
        routine: Routine,
        state: RoutineExecutionState,
        now: ZonedDateTime = ZonedDateTime.now()
    ) {
        cancelRoutine(routine.id)
        syncRoutine(routine, state, now)
    }

    fun schedulePostpone(
        routine: Routine,
        sourceType: RoutineAlarmType,
        minutes: Int,
        nowEpochMillis: Long = System.currentTimeMillis()
    ): Long {
        val snoozedType = sourceType.baseType.snoozedType()
        cancelAlarm(routine.id, snoozedType)
        val triggerAt = RoutinePostponePolicy.triggerAt(nowEpochMillis, minutes)
        val epochDay = Instant.ofEpochMilli(triggerAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toEpochDay()
        schedule(routine, snoozedType, triggerAt, epochDay)
        preferenceStore.setPendingPostpone(routine.id, snoozedType, triggerAt, epochDay)
        preferenceStore.recordPostpone(routine.id)
        return triggerAt
    }

    fun restorePostpones(routine: Routine, nowEpochMillis: Long = System.currentTimeMillis()) {
        preferenceStore.getPendingPostpones(routine.id).forEach { pending ->
            if (!routine.enabled || pending.triggerAtEpochMillis <= nowEpochMillis) {
                preferenceStore.clearPendingPostpone(routine.id, pending.alarmType)
            } else {
                schedule(
                    routine,
                    pending.alarmType,
                    pending.triggerAtEpochMillis,
                    pending.scheduledEpochDay
                )
            }
        }
    }

    fun markPostponeDelivered(routineId: Int, alarmType: RoutineAlarmType) {
        preferenceStore.clearPendingPostpone(routineId, alarmType)
    }

    fun markDayCloseDelivered(routineId: Int, scheduledEpochDay: Long) {
        preferenceStore.clearPendingDayClose(routineId, scheduledEpochDay)
    }

    fun cancelRoutine(routineId: Int) {
        RoutineAlarmType.entries.forEach { cancelAlarm(routineId, it) }
        preferenceStore.clearPendingPostpones(routineId)
        preferenceStore.clearPendingDayClose(routineId)
    }

    private fun cancelBaseAlarms(routineId: Int) {
        listOf(
            RoutineAlarmType.START,
            RoutineAlarmType.DEADLINE,
            RoutineAlarmType.PENDING_TASKS,
            RoutineAlarmType.DAY_CLOSE
        ).forEach { cancelAlarm(routineId, it) }
    }

    private fun cancelSnoozedAlarms(routineId: Int) {
        listOf(
            RoutineAlarmType.SNOOZED_START,
            RoutineAlarmType.SNOOZED_DEADLINE,
            RoutineAlarmType.SNOOZED_PENDING_TASKS
        ).forEach { cancelAlarm(routineId, it) }
        preferenceStore.clearPendingPostpones(routineId)
    }

    private fun schedule(
        routine: Routine,
        type: RoutineAlarmType,
        triggerAtEpochMillis: Long,
        scheduledEpochDay: Long
    ) {
        val pendingIntent = buildPendingIntent(
            routine = routine,
            type = type,
            scheduledEpochDay = scheduledEpochDay,
            flags = PendingIntent.FLAG_UPDATE_CURRENT
        ) ?: return
        scheduleAlarmSafely(triggerAtEpochMillis, pendingIntent)
    }

    private fun cancelAlarm(routineId: Int, type: RoutineAlarmType) {
        val intent = Intent(context, RoutineAlarmReceiver::class.java).apply {
            data = alarmUri(routineId, type)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(routineId, type),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun buildPendingIntent(
        routine: Routine,
        type: RoutineAlarmType,
        scheduledEpochDay: Long,
        flags: Int
    ): PendingIntent? {
        val intent = Intent(context, RoutineAlarmReceiver::class.java).apply {
            data = alarmUri(routine.id, type)
            putExtra(RoutineAlarmReceiver.EXTRA_ROUTINE_ID, routine.id)
            putExtra(RoutineAlarmReceiver.EXTRA_ALARM_TYPE, type.name)
            putExtra(RoutineAlarmReceiver.EXTRA_SCHEDULED_EPOCH_DAY, scheduledEpochDay)
            putExtra(RoutineAlarmReceiver.EXTRA_ROUTINE_UPDATED_AT, routine.updatedAtEpochMillis)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(routine.id, type),
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleAlarmSafely(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        try {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                return
            }
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun requestCode(routineId: Int, type: RoutineAlarmType): Int =
        REQUEST_CODE_OFFSET + routineId * 10 + type.ordinal

    private fun alarmUri(routineId: Int, type: RoutineAlarmType) =
        "voicereminder://routine/alarm/$routineId/${type.name}".toUri()

    companion object {
        private const val REQUEST_CODE_OFFSET = 500_000
    }
}
