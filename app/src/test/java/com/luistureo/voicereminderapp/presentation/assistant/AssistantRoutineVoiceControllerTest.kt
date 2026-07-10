package com.luistureo.voicereminderapp.presentation.assistant

import com.luistureo.voicereminderapp.domain.routine.model.Routine
import com.luistureo.voicereminderapp.domain.routine.model.RoutineAssistantMode
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionState
import com.luistureo.voicereminderapp.domain.routine.model.RoutinePeriod
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantRoutineVoiceControllerTest {
    private val controller = AssistantRoutineVoiceController()

    @Test
    fun simpleDisplayDoesNotStartAssistantGuidance() {
        assertNull(controller.start(routine(RoutineAssistantMode.SIMPLE_DISPLAY), task(), "María"))
    }

    @Test
    fun guidedStartUsesNameAndAnnouncesFirstTask() {
        val turn = requireNotNull(
            controller.start(routine(RoutineAssistantMode.STEP_BY_STEP_GUIDE), task(), "María")
        )

        assertTrue(turn.message.contains("Buenos días María"))
        assertTrue(turn.message.contains("Primero, Tomar medicamento"))
        assertEquals(AssistantVisualState.IDLE, turn.finalVisualState)
    }

    @Test
    fun smartGuideUsesDeterministicFlowWithoutAiDecision() {
        val turn = controller.start(routine(RoutineAssistantMode.SMART_GUIDE), task(), null)

        assertTrue(requireNotNull(turn).message.contains("Vamos a comenzar"))
    }

    @Test
    fun outputPolicyRespectsVoiceAndBubbleSettings() {
        val disabled = AssistantRoutineOutputPolicy.resolve(
            routine(RoutineAssistantMode.STEP_BY_STEP_GUIDE).copy(
                voiceEnabled = false,
                motivationBubbleEnabled = false
            )
        )
        val enabled = AssistantRoutineOutputPolicy.resolve(
            routine(RoutineAssistantMode.STEP_BY_STEP_GUIDE).copy(
                voiceEnabled = true,
                motivationBubbleEnabled = true
            )
        )

        assertFalse(disabled.voiceEnabled)
        assertFalse(disabled.bubbleEnabled)
        assertTrue(enabled.voiceEnabled)
        assertTrue(enabled.bubbleEnabled)
    }

    @Test
    fun completedAndPartialSummariesReportExactProgress() {
        val routine = routine(RoutineAssistantMode.STEP_BY_STEP_GUIDE)
        val completed = controller.summary(
            routine,
            RoutineExecutionState.COMPLETED,
            5,
            5,
            "María"
        )
        val partial = controller.summary(
            routine,
            RoutineExecutionState.PARTIALLY_COMPLETED,
            3,
            5,
            "María"
        )

        assertTrue(completed.message.contains("5 de 5"))
        assertTrue(completed.message.contains("Excelente trabajo María"))
        assertEquals(AssistantVisualState.SUCCESS, completed.finalVisualState)
        assertTrue(partial.message.contains("3 de 5"))
        assertTrue(partial.message.contains("Buen avance María"))
    }

    @Test
    fun missedMessageIsSupportive() {
        val turn = controller.summary(
            routine(RoutineAssistantMode.STEP_BY_STEP_GUIDE),
            RoutineExecutionState.NOT_COMPLETED,
            1,
            4,
            null
        )

        assertTrue(turn.message.contains("puedes intentarlo nuevamente"))
    }

    @Test
    fun voiceCompletionRequiresAnExplicitSupportedPhrase() {
        assertTrue(RoutineVoiceConfirmationPolicy.confirmsCompletion("Listo"))
        assertTrue(RoutineVoiceConfirmationPolicy.confirmsCompletion("Terminé"))
        assertTrue(RoutineVoiceConfirmationPolicy.confirmsCompletion("Ya lo hice"))
        assertFalse(RoutineVoiceConfirmationPolicy.confirmsCompletion("Todavía no"))
        assertFalse(RoutineVoiceConfirmationPolicy.confirmsCompletion("No estoy listo"))
    }

    private fun routine(mode: RoutineAssistantMode) = Routine(
        id = 4,
        name = "Rutina de mañana",
        description = "Rutina",
        category = "Bienestar",
        icon = "morning",
        color = 0,
        enabled = true,
        period = RoutinePeriod.MORNING,
        assistantMode = mode,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L
    )

    private fun task() = RoutineTask(
        id = 9,
        routineId = 4,
        title = "Tomar medicamento",
        orderPriority = 0
    )
}
