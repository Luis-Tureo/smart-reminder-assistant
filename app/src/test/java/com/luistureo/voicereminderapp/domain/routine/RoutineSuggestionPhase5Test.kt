package com.luistureo.voicereminderapp.domain.routine

import com.luistureo.voicereminderapp.domain.routine.model.*
import com.luistureo.voicereminderapp.domain.routine.service.RoutineSuggestionEngine
import com.luistureo.voicereminderapp.domain.routine.service.RoutineSuggestionPolicy
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineSuggestionPhase5Test {
    private val today = LocalDate.of(2026, 7, 10)

    @Test fun detectsLongLowCompletionPendingPostponedScheduleAndInactiveRules() {
        val routine = routine().copy(startTime = LocalTime.of(8, 0), deadlineTime = LocalTime.of(8, 20))
        val tasks = (1..11).map { RoutineTask(id = it, routineId = 1, title = "Tarea $it",
            orderPriority = it, estimatedDurationMinutes = 5) }
        val history = (1..4).map { offset ->
            RoutineHistory(date = today.minusDays(offset.toLong()), routineId = 1,
                completedTasks = 1, totalTasks = 11, completionPercentage = 9.0,
                finalState = RoutineExecutionState.PARTIALLY_COMPLETED,
                pendingTaskTitles = listOf("Tarea 2"))
        }
        val types = RoutineSuggestionEngine().evaluate(routine, tasks, history, today, 4, 3)
            .map { it.type }.toSet()
        assertTrue(RoutineSuggestionType.EXCESSIVE_TASKS in types)
        assertTrue(RoutineSuggestionType.LOW_COMPLETION in types)
        assertTrue(RoutineSuggestionType.REPEATED_PENDING_TASK in types)
        assertTrue(RoutineSuggestionType.FREQUENT_POSTPONEMENT in types)
        assertTrue(RoutineSuggestionType.SCHEDULE_MISMATCH in types)
    }

    @Test fun policyLimitsFrequencyCooldownSettingsAndInterruptions() {
        val settings = RoutineSuggestionSettings(enabled = true, preferredHour = 18)
        assertFalse(RoutineSuggestionPolicy.canCreate(settings, LocalTime.of(17, 0), 0, null, today))
        assertFalse(RoutineSuggestionPolicy.canCreate(settings, LocalTime.of(19, 0), 1, null, today))
        assertFalse(RoutineSuggestionPolicy.canCreate(settings, LocalTime.of(19, 0), 0, null, today,
            assistantSpeechActive = true))
        val dismissed = RoutineSuggestion(routineId = 1, type = RoutineSuggestionType.LOW_COMPLETION,
            message = "Mensaje", primaryAction = "Revisar", createdAtEpochDay = today.minusDays(2).toEpochDay(),
            dismissedAtEpochDay = today.minusDays(2).toEpochDay(), active = false)
        assertFalse(RoutineSuggestionPolicy.canCreate(settings, LocalTime.of(19, 0), 0, dismissed, today))
        assertFalse(RoutineSuggestionPolicy.canCreate(settings.copy(enabled = false), LocalTime.of(19, 0), 0, null, today))
    }

    @Test fun outputRespectsVoiceBubbleAndActiveSpeech() {
        assertTrue(RoutineSuggestionPolicy.output(RoutineSuggestionSettings(showBubble = true, speak = false), false).first)
        assertFalse(RoutineSuggestionPolicy.output(RoutineSuggestionSettings(showBubble = false, speak = false), false).first)
        assertFalse(RoutineSuggestionPolicy.output(RoutineSuggestionSettings(showBubble = true, speak = true), true).second)
    }

    private fun routine() = Routine(id = 1, name = "Rutina", description = "", category = "Organización",
        icon = "wb_sunny", color = 0, enabled = true, period = RoutinePeriod.MORNING,
        createdAtEpochMillis = 1, updatedAtEpochMillis = 1)
}
