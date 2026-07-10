package com.luistureo.voicereminderapp.domain.routine

import com.luistureo.voicereminderapp.domain.routine.model.Routine
import com.luistureo.voicereminderapp.domain.routine.model.RoutineAssistantMode
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionAction
import com.luistureo.voicereminderapp.domain.routine.model.RoutinePeriod
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTask
import com.luistureo.voicereminderapp.domain.routine.usecase.ApplyRoutineExecutionActionUseCase
import com.luistureo.voicereminderapp.domain.routine.usecase.UpdateRoutineTaskProgressUseCase
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantRoutineHistoryTest {
    @Test
    fun guidedCompletionPreservesAssistantModeInExecutionAndHistory() = runBlocking {
        val repository = FakeRoutineRepository()
        val date = LocalDate.of(2026, 7, 10)
        val routine = Routine(
            id = 1,
            name = "Mañana",
            description = "Rutina",
            category = "Bienestar",
            icon = "morning",
            color = 0,
            enabled = true,
            period = RoutinePeriod.MORNING,
            assistantMode = RoutineAssistantMode.STEP_BY_STEP_GUIDE,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L
        )
        val task = RoutineTask(id = 8, routineId = 1, title = "Desayunar", orderPriority = 0)
        repository.routines += routine
        repository.tasksByRoutine[1] = listOf(task)

        ApplyRoutineExecutionActionUseCase(repository)(
            1,
            date,
            RoutineExecutionAction.START,
            RoutineAssistantMode.STEP_BY_STEP_GUIDE
        )
        UpdateRoutineTaskProgressUseCase(repository)(
            task,
            true,
            date,
            RoutineAssistantMode.STEP_BY_STEP_GUIDE
        )

        assertEquals(
            RoutineAssistantMode.STEP_BY_STEP_GUIDE,
            repository.getDailyExecution(1, date)?.assistantGuidanceMode
        )
        assertEquals(
            RoutineAssistantMode.STEP_BY_STEP_GUIDE,
            repository.getHistory(1).single().assistantGuidanceMode
        )
    }
}
