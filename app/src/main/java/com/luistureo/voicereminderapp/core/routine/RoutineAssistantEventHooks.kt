package com.luistureo.voicereminderapp.core.routine

import com.luistureo.voicereminderapp.domain.routine.model.RoutineAssistantMode
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionState

data class RoutineAssistantEvent(
    val routineId: Int,
    val assistantMode: RoutineAssistantMode,
    val state: RoutineExecutionState,
    val occurredAtEpochMillis: Long
)

object RoutineAssistantEventHooks {
    @Volatile
    var listener: ((RoutineAssistantEvent) -> Unit)? = null

    fun emit(event: RoutineAssistantEvent) {
        listener?.invoke(event)
    }
}
