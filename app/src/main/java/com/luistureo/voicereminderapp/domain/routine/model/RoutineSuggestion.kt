package com.luistureo.voicereminderapp.domain.routine.model

enum class RoutineSuggestionType {
    EXCESSIVE_TASKS,
    LOW_COMPLETION,
    REPEATED_PENDING_TASK,
    FREQUENT_POSTPONEMENT,
    SCHEDULE_MISMATCH,
    GOOD_PROGRESS,
    SHORTER_ROUTINE,
    INACTIVE_PERIOD
}

data class RoutineSuggestion(
    val id: Int = 0,
    val routineId: Int,
    val type: RoutineSuggestionType,
    val message: String,
    val primaryAction: String,
    val secondaryAction: String = "Mantener igual",
    val createdAtEpochDay: Long,
    val dismissedAtEpochDay: Long? = null,
    val active: Boolean = true,
    val requiresUserConfirmation: Boolean = true
)
