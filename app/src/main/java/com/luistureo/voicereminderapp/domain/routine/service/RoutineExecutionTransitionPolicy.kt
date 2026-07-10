package com.luistureo.voicereminderapp.domain.routine.service

import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionAction
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionState

object RoutineExecutionTransitionPolicy {
    fun targetState(
        current: RoutineExecutionState,
        action: RoutineExecutionAction
    ): RoutineExecutionState? = when (action) {
        RoutineExecutionAction.START ->
            RoutineExecutionState.IN_PROGRESS.takeIf { current == RoutineExecutionState.PENDING }
        RoutineExecutionAction.COMPLETE -> RoutineExecutionState.COMPLETED
        RoutineExecutionAction.PARTIAL -> RoutineExecutionState.PARTIALLY_COMPLETED
        RoutineExecutionAction.SKIP -> RoutineExecutionState.SKIPPED
        RoutineExecutionAction.NOT_COMPLETED ->
            RoutineExecutionState.NOT_COMPLETED.takeIf {
                current == RoutineExecutionState.PENDING ||
                    current == RoutineExecutionState.IN_PROGRESS
            }
    }

    fun isTerminal(state: RoutineExecutionState): Boolean = state in setOf(
        RoutineExecutionState.COMPLETED,
        RoutineExecutionState.PARTIALLY_COMPLETED,
        RoutineExecutionState.SKIPPED,
        RoutineExecutionState.NOT_COMPLETED
    )
}
