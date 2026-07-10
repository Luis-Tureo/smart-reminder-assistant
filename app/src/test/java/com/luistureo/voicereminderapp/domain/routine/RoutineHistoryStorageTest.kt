package com.luistureo.voicereminderapp.domain.routine

import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionState
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTask
import com.luistureo.voicereminderapp.domain.routine.usecase.CompleteRoutineDayUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RoutineHistoryStorageTest {
    @Test
    fun completingDayStoresHistoryAndFinalExecutionState() = runBlocking {
        val repository = FakeRoutineRepository()
        val date = LocalDate.of(2026, 7, 9)
        val tasks = listOf(
            RoutineTask(
                id = 1,
                routineId = 9,
                title = "Hecha",
                orderPriority = 0,
                completed = true,
                completedOn = date
            ),
            RoutineTask(id = 2, routineId = 9, title = "Pendiente", orderPriority = 1)
        )

        val history = CompleteRoutineDayUseCase(repository) { 100L }(9, date, tasks)

        assertEquals(50.0, history.completionPercentage, 0.0)
        assertEquals(RoutineExecutionState.PARTIALLY_COMPLETED, history.finalState)
        assertEquals(history, repository.histories.single())
        assertEquals(RoutineExecutionState.PARTIALLY_COMPLETED, repository.executions.single().state)
    }
}
