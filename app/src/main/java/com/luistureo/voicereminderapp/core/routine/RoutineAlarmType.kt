package com.luistureo.voicereminderapp.core.routine

enum class RoutineAlarmType {
    START,
    DEADLINE,
    PENDING_TASKS,
    SNOOZED_START,
    SNOOZED_DEADLINE,
    SNOOZED_PENDING_TASKS,
    DAY_CLOSE;

    val baseType: RoutineAlarmType
        get() = when (this) {
            SNOOZED_START -> START
            SNOOZED_DEADLINE -> DEADLINE
            SNOOZED_PENDING_TASKS -> PENDING_TASKS
            else -> this
        }

    val isSnoozed: Boolean
        get() = this != baseType

    fun snoozedType(): RoutineAlarmType = when (baseType) {
        START -> SNOOZED_START
        DEADLINE -> SNOOZED_DEADLINE
        PENDING_TASKS -> SNOOZED_PENDING_TASKS
        else -> this
    }
}
