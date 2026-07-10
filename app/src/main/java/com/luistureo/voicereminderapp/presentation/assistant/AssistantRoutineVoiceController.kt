package com.luistureo.voicereminderapp.presentation.assistant

import com.luistureo.voicereminderapp.domain.routine.model.Routine
import com.luistureo.voicereminderapp.domain.routine.model.RoutineAssistantMode
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionState
import com.luistureo.voicereminderapp.domain.routine.model.RoutinePeriod
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTask
import java.text.Normalizer

data class AssistantRoutineVoiceTurn(
    val message: String,
    val bubbleMessage: String,
    val finalVisualState: AssistantVisualState
)

data class AssistantRoutineOutputOptions(
    val voiceEnabled: Boolean,
    val bubbleEnabled: Boolean
)

object AssistantRoutineOutputPolicy {
    fun resolve(routine: Routine): AssistantRoutineOutputOptions {
        val guidanceEnabled = routine.assistantMode != RoutineAssistantMode.SIMPLE_DISPLAY
        return AssistantRoutineOutputOptions(
            voiceEnabled = guidanceEnabled && routine.voiceEnabled,
            bubbleEnabled = guidanceEnabled && routine.motivationBubbleEnabled
        )
    }
}

class AssistantRoutineVoiceController {
    fun start(
        routine: Routine,
        nextTask: RoutineTask?,
        preferredName: String?
    ): AssistantRoutineVoiceTurn? {
        if (routine.assistantMode == RoutineAssistantMode.SIMPLE_DISPLAY) return null
        val greeting = greeting(routine.period, preferredName)
        val firstStep = nextTask?.let { " Primero, ${it.title}." }.orEmpty()
        return AssistantRoutineVoiceTurn(
            message = "$greeting Vamos a comenzar tu rutina ${period(routine.period)}.$firstStep",
            bubbleMessage = "Vamos paso a paso.",
            finalVisualState = AssistantVisualState.IDLE
        )
    }

    fun taskCompleted(nextTask: RoutineTask?): AssistantRoutineVoiceTurn =
        if (nextTask == null) {
            AssistantRoutineVoiceTurn(
                message = "Excelente, completaste todas las actividades.",
                bubbleMessage = "¡Muy bien!",
                finalVisualState = AssistantVisualState.SUCCESS
            )
        } else {
            AssistantRoutineVoiceTurn(
                message = "Perfecto, continuemos con la siguiente actividad. Ahora, ${nextTask.title}.",
                bubbleMessage = "Seguimos con el siguiente paso.",
                finalVisualState = AssistantVisualState.IDLE
            )
        }

    fun summary(
        routine: Routine,
        state: RoutineExecutionState,
        completedTasks: Int,
        totalTasks: Int,
        preferredName: String?
    ): AssistantRoutineVoiceTurn {
        val name = preferredName?.trim()?.takeIf { it.isNotEmpty() }?.let { " $it" }.orEmpty()
        val lead = when (state) {
            RoutineExecutionState.COMPLETED -> "Excelente trabajo$name, completaste tu rutina."
            RoutineExecutionState.PARTIALLY_COMPLETED ->
                "Buen avance$name, completaste parte de tus actividades."
            RoutineExecutionState.NOT_COMPLETED ->
                "No importa$name, puedes intentarlo nuevamente."
            else -> "Tu avance quedó guardado."
        }
        return AssistantRoutineVoiceTurn(
            message = "$lead Completaste $completedTasks de $totalTasks actividades de tu rutina ${period(routine.period)}.",
            bubbleMessage = when (state) {
                RoutineExecutionState.COMPLETED -> "¡Rutina completada!"
                RoutineExecutionState.PARTIALLY_COMPLETED -> "Buen avance."
                else -> "Puedes intentarlo nuevamente."
            },
            finalVisualState = if (state == RoutineExecutionState.COMPLETED) {
                AssistantVisualState.SUCCESS
            } else {
                AssistantVisualState.IDLE
            }
        )
    }

    private fun greeting(period: RoutinePeriod, preferredName: String?): String {
        val base = when (period) {
            RoutinePeriod.MORNING -> "Buenos días"
            RoutinePeriod.AFTERNOON -> "Buenas tardes"
            RoutinePeriod.NIGHT -> "Buenas noches"
        }
        return preferredName?.trim()?.takeIf { it.isNotEmpty() }?.let { "$base $it." } ?: "$base."
    }

    private fun period(period: RoutinePeriod): String = when (period) {
        RoutinePeriod.MORNING -> "de la mañana"
        RoutinePeriod.AFTERNOON -> "de la tarde"
        RoutinePeriod.NIGHT -> "de la noche"
    }
}

object RoutineVoiceConfirmationPolicy {
    private val confirmations = setOf("listo", "termine", "ya lo hice")

    fun confirmsCompletion(text: String): Boolean = normalize(text) in confirmations

    private fun normalize(text: String): String = Normalizer.normalize(
        text.lowercase().trim(),
        Normalizer.Form.NFD
    ).replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9 ]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}
