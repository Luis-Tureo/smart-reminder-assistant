package com.luistureo.voicereminderapp.data.mapper

import com.luistureo.voicereminderapp.data.local.entity.ReminderEntity
import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.model.ReminderRecurrence
import com.luistureo.voicereminderapp.domain.model.ReminderRecurrenceUnit
import com.luistureo.voicereminderapp.domain.model.ReminderScheduleState
import com.luistureo.voicereminderapp.domain.model.ReminderSource
import com.luistureo.voicereminderapp.domain.model.ReminderType
import com.luistureo.voicereminderapp.domain.model.ReminderWeekday

// Convierte la entidad persistida al modelo de dominio.
fun ReminderEntity.toDomain(): Reminder {
    val resolvedType = ReminderType.entries.firstOrNull { it.name == type } ?: ReminderType.DEFAULT
    val resolvedSource = ReminderSource.entries.firstOrNull { it.name == source }
        ?: ReminderSource.MANUAL
    val resolvedRecurrenceUnit = recurrenceUnit?.let { storedValue ->
        ReminderRecurrenceUnit.entries.firstOrNull { it.name == storedValue }
    }

    val recurrence = resolvedRecurrenceUnit?.let { unit ->
        ReminderRecurrence(
            unit = unit,
            interval = recurrenceInterval,
            weekdays = recurrenceWeekdays
                .split(",")
                .mapNotNull { item ->
                    item.trim()
                        .takeIf { it.isNotBlank() }
                        ?.let { value -> ReminderWeekday.entries.firstOrNull { it.name == value } }
                }
                .toSet(),
            isActive = isRecurringActive
        )
    }

    return Reminder(
        id = id,
        title = title,
        detail = detail,
        scheduledAtEpochMillis = scheduledAtEpochMillis,
        isCompleted = isCompleted,
        type = resolvedType,
        isUrgent = isUrgent,
        source = resolvedSource,
        recurrence = recurrence,
        scheduleState = ReminderScheduleState(
            nextTriggerAtEpochMillis = nextTriggerAtEpochMillis,
            lastTriggeredAtEpochMillis = lastTriggeredAtEpochMillis,
            activeAlertAtEpochMillis = activeAlertAtEpochMillis,
            activeAlertRepeatCount = activeAlertRepeatCount,
            nextUrgentRepeatAtEpochMillis = nextUrgentRepeatAtEpochMillis
        )
    )
}

// Convierte el dominio a la entidad persistida.
fun Reminder.toEntity(): ReminderEntity {
    return ReminderEntity(
        id = id,
        title = title,
        detail = detail,
        scheduledAtEpochMillis = scheduledAtEpochMillis,
        isCompleted = isCompleted,
        type = type.name,
        isUrgent = isUrgent,
        source = source.name,
        recurrenceUnit = recurrence?.unit?.name,
        recurrenceInterval = recurrence?.normalizedInterval ?: 1,
        recurrenceWeekdays = recurrence?.weekdays
            ?.sortedBy { it.isoDayNumber }
            ?.joinToString(separator = ",") { it.name }
            .orEmpty(),
        isRecurringActive = recurrence?.isActive ?: false,
        nextTriggerAtEpochMillis = scheduleState.nextTriggerAtEpochMillis,
        lastTriggeredAtEpochMillis = scheduleState.lastTriggeredAtEpochMillis,
        activeAlertAtEpochMillis = scheduleState.activeAlertAtEpochMillis,
        activeAlertRepeatCount = scheduleState.activeAlertRepeatCount,
        nextUrgentRepeatAtEpochMillis = scheduleState.nextUrgentRepeatAtEpochMillis
    )
}
