package com.luistureo.voicereminderapp.core.routine

enum class RoutineNotificationKind {
    START,
    DEADLINE,
    PENDING_TASKS,
    MOTIVATION
}

enum class RoutineNotificationAction {
    START,
    COMPLETE,
    PARTIAL,
    POSTPONE
}

data class RoutineNotificationPlan(
    val notificationId: Int,
    val kind: RoutineNotificationKind,
    val title: String,
    val message: String,
    val actions: List<RoutineNotificationAction>,
    val silent: Boolean = false,
    val startWithAssistant: Boolean = false
)
