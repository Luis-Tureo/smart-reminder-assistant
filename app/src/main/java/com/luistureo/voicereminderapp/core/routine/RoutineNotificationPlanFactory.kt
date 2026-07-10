package com.luistureo.voicereminderapp.core.routine

import com.luistureo.voicereminderapp.domain.routine.model.Routine
import com.luistureo.voicereminderapp.domain.routine.model.RoutineAssistantMode
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionState
import com.luistureo.voicereminderapp.domain.routine.model.RoutinePeriod

object RoutineNotificationPlanFactory {
    fun start(routine: Routine, preferredName: String?): RoutineNotificationPlan =
        RoutineNotificationPlan(
            notificationId = notificationId(routine.id, RoutineNotificationKind.START),
            kind = RoutineNotificationKind.START,
            title = "${periodIcon(routine.period)} ${routine.name}",
            message = "${greeting(routine.period, preferredName)} es momento de comenzar tu rutina.",
            actions = listOf(
                RoutineNotificationAction.START,
                RoutineNotificationAction.COMPLETE,
                RoutineNotificationAction.POSTPONE
            ),
            startWithAssistant = routine.assistantMode != RoutineAssistantMode.SIMPLE_DISPLAY
        )

    fun deadline(routine: Routine, remainingTasks: Int): RoutineNotificationPlan =
        RoutineNotificationPlan(
            notificationId = notificationId(routine.id, RoutineNotificationKind.DEADLINE),
            kind = RoutineNotificationKind.DEADLINE,
            title = "Rutina incompleta",
            message = "Tu rutina ${periodDescription(routine.period)} todavía tiene actividades pendientes. " +
                if (remainingTasks == 1) "Te falta 1 actividad." else "Te faltan $remainingTasks actividades.",
            actions = listOf(
                RoutineNotificationAction.COMPLETE,
                RoutineNotificationAction.PARTIAL,
                RoutineNotificationAction.POSTPONE
            )
        )

    fun pending(
        routine: Routine,
        remainingTasks: Int,
        executionState: RoutineExecutionState
    ): RoutineNotificationPlan =
        RoutineNotificationPlan(
            notificationId = notificationId(routine.id, RoutineNotificationKind.PENDING_TASKS),
            kind = RoutineNotificationKind.PENDING_TASKS,
            title = "Actividades pendientes",
            message = if (remainingTasks == 1) {
                "Aún tienes 1 actividad pendiente en tu rutina."
            } else {
                "Aún tienes $remainingTasks actividades pendientes en tu rutina."
            },
            actions = when (executionState) {
                RoutineExecutionState.PENDING -> listOf(
                    RoutineNotificationAction.START,
                    RoutineNotificationAction.COMPLETE,
                    RoutineNotificationAction.POSTPONE
                )
                RoutineExecutionState.IN_PROGRESS -> listOf(
                    RoutineNotificationAction.COMPLETE,
                    RoutineNotificationAction.PARTIAL,
                    RoutineNotificationAction.POSTPONE
                )
                else -> emptyList()
            },
            startWithAssistant = routine.assistantMode != RoutineAssistantMode.SIMPLE_DISPLAY
        )

    fun motivation(
        routine: Routine,
        state: RoutineExecutionState,
        preferredName: String?
    ): RoutineNotificationPlan? {
        if (!routine.motivationBubbleEnabled) return null
        val message = motivationMessage(routine, state, preferredName) ?: return null
        return RoutineNotificationPlan(
            notificationId = notificationId(routine.id, RoutineNotificationKind.MOTIVATION),
            kind = RoutineNotificationKind.MOTIVATION,
            title = "Tu asistente diario",
            message = message,
            actions = emptyList(),
            silent = true
        )
    }

    fun motivationMessage(
        routine: Routine,
        state: RoutineExecutionState,
        preferredName: String?
    ): String? = when (state) {
            RoutineExecutionState.COMPLETED ->
                "${personalizedLead("Excelente", preferredName)}, completaste tu rutina ${periodDescription(routine.period)}."
            RoutineExecutionState.PARTIALLY_COMPLETED ->
                "${personalizedLead("Buen trabajo", preferredName)}, avanzaste parte de tu rutina."
            RoutineExecutionState.NOT_COMPLETED,
            RoutineExecutionState.SKIPPED ->
                "${personalizedLead("No pasa nada", preferredName)}, puedes intentarlo nuevamente."
            else -> null
        }

    fun notificationId(routineId: Int, kind: RoutineNotificationKind): Int =
        NOTIFICATION_OFFSET + routineId * 10 + kind.ordinal

    private fun greeting(period: RoutinePeriod, preferredName: String?): String {
        val greeting = when (period) {
            RoutinePeriod.MORNING -> "Buenos días"
            RoutinePeriod.AFTERNOON -> "Buenas tardes"
            RoutinePeriod.NIGHT -> "Buenas noches"
        }
        return if (preferredName.isNullOrBlank()) "$greeting," else "$greeting ${preferredName.trim()},"
    }

    private fun personalizedLead(lead: String, preferredName: String?): String =
        if (preferredName.isNullOrBlank()) lead else "$lead ${preferredName.trim()}"

    private fun periodDescription(period: RoutinePeriod): String = when (period) {
        RoutinePeriod.MORNING -> "de la mañana"
        RoutinePeriod.AFTERNOON -> "de la tarde"
        RoutinePeriod.NIGHT -> "de la noche"
    }

    private fun periodIcon(period: RoutinePeriod): String = when (period) {
        RoutinePeriod.MORNING -> "🌅"
        RoutinePeriod.AFTERNOON -> "☀️"
        RoutinePeriod.NIGHT -> "🌙"
    }

    private const val NOTIFICATION_OFFSET = 500_000
}
