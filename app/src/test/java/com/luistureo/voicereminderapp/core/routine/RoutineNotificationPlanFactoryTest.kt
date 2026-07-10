package com.luistureo.voicereminderapp.core.routine

import com.luistureo.voicereminderapp.domain.routine.model.Routine
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionState
import com.luistureo.voicereminderapp.domain.routine.model.RoutinePeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineNotificationPlanFactoryTest {
    @Test
    fun startPlanUsesPreferredNameAndMinimalActions() {
        val routine = routine()

        val plan = RoutineNotificationPlanFactory.start(routine, "  Maria  ")

        assertEquals(RoutineNotificationKind.START, plan.kind)
        assertTrue(plan.title.contains(routine.name))
        assertTrue(plan.message.contains("Maria"))
        assertFalse(plan.message.contains("  Maria  "))
        assertEquals(
            listOf(
                RoutineNotificationAction.START,
                RoutineNotificationAction.COMPLETE,
                RoutineNotificationAction.POSTPONE
            ),
            plan.actions
        )
    }

    @Test
    fun startPlanUsesGenericGreetingWhenPreferredNameIsMissing() {
        val unnamed = RoutineNotificationPlanFactory.start(routine(), null)
        val blankName = RoutineNotificationPlanFactory.start(routine(), "   ")

        assertEquals(unnamed.message, blankName.message)
        assertEquals(
            "Buenos d\u00edas, es momento de comenzar tu rutina.",
            unnamed.message
        )
        assertFalse(unnamed.message.contains("null"))
    }

    @Test
    fun deadlinePlanReportsRemainingTasksAndOffersExpectedActions() {
        val plan = RoutineNotificationPlanFactory.deadline(routine(), remainingTasks = 2)

        assertEquals(RoutineNotificationKind.DEADLINE, plan.kind)
        assertEquals("Rutina incompleta", plan.title)
        assertTrue(plan.message.contains("2 actividades"))
        assertEquals(
            listOf(
                RoutineNotificationAction.COMPLETE,
                RoutineNotificationAction.PARTIAL,
                RoutineNotificationAction.POSTPONE
            ),
            plan.actions
        )
    }

    @Test
    fun pendingPlanReportsSingularTaskAndOffersExpectedActions() {
        val plan = RoutineNotificationPlanFactory.pending(
            routine(),
            remainingTasks = 1,
            executionState = RoutineExecutionState.PENDING
        )

        assertEquals(RoutineNotificationKind.PENDING_TASKS, plan.kind)
        assertTrue(plan.message.contains("1 actividad pendiente"))
        assertEquals(
            listOf(
                RoutineNotificationAction.START,
                RoutineNotificationAction.COMPLETE,
                RoutineNotificationAction.POSTPONE
            ),
            plan.actions
        )
    }

    @Test
    fun pendingPlanForInProgressRoutineDoesNotOfferInvalidStartAction() {
        val plan = RoutineNotificationPlanFactory.pending(
            routine(),
            remainingTasks = 2,
            executionState = RoutineExecutionState.IN_PROGRESS
        )

        assertEquals(
            listOf(
                RoutineNotificationAction.COMPLETE,
                RoutineNotificationAction.PARTIAL,
                RoutineNotificationAction.POSTPONE
            ),
            plan.actions
        )
        assertFalse(plan.actions.contains(RoutineNotificationAction.START))
    }

    @Test
    fun motivationBubbleFlagControlsVisualMotivationIndependentlyFromVoice() {
        val visualOnlyRoutine = routine().copy(
            motivationBubbleEnabled = true,
            voiceEnabled = false
        )
        val voiceOnlyRoutine = routine().copy(
            motivationBubbleEnabled = false,
            voiceEnabled = true
        )

        val visualPlan = RoutineNotificationPlanFactory.motivation(
            visualOnlyRoutine,
            RoutineExecutionState.COMPLETED,
            preferredName = "Maria"
        )
        val suppressedPlan = RoutineNotificationPlanFactory.motivation(
            voiceOnlyRoutine,
            RoutineExecutionState.COMPLETED,
            preferredName = "Maria"
        )

        assertNotNull(visualPlan)
        assertTrue(requireNotNull(visualPlan).message.contains("Maria"))
        assertTrue(visualPlan.silent)
        assertTrue(visualPlan.actions.isEmpty())
        assertNull(suppressedPlan)
    }

    @Test
    fun motivationIsNotCreatedForNonTerminalExecutionStates() {
        assertNull(
            RoutineNotificationPlanFactory.motivation(
                routine(),
                RoutineExecutionState.IN_PROGRESS,
                preferredName = null
            )
        )
    }

    @Test
    fun notificationKindsUseDifferentStableIdentifiers() {
        val routineId = 27
        val ids = RoutineNotificationKind.entries.map { kind ->
            RoutineNotificationPlanFactory.notificationId(routineId, kind)
        }

        assertEquals(ids.size, ids.toSet().size)
        assertEquals(
            ids,
            RoutineNotificationKind.entries.map { kind ->
                RoutineNotificationPlanFactory.notificationId(routineId, kind)
            }
        )
    }

    private fun routine() = Routine(
        id = 7,
        name = "Rutina de ma\u00f1ana",
        description = "Comenzar bien el d\u00eda",
        category = "Bienestar",
        icon = "morning",
        color = 0,
        enabled = true,
        period = RoutinePeriod.MORNING,
        motivationBubbleEnabled = true,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L
    )
}
