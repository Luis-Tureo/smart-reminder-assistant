package com.luistureo.voicereminderapp.domain.routine

import com.luistureo.voicereminderapp.domain.routine.model.Routine
import com.luistureo.voicereminderapp.domain.routine.model.RoutineDailyExecution
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionAction
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionState
import com.luistureo.voicereminderapp.domain.routine.model.RoutineHistory
import com.luistureo.voicereminderapp.domain.routine.model.RoutinePeriod
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTask
import com.luistureo.voicereminderapp.domain.routine.usecase.ApplyRoutineExecutionActionUseCase
import com.luistureo.voicereminderapp.domain.routine.usecase.UpdateRoutineTaskProgressUseCase
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineExecutionActionUseCaseTest {
    private val date = LocalDate.of(2026, 7, 9)

    @Test
    fun startMovesPendingExecutionToInProgressWithoutCreatingHistory() = runBlocking {
        val repository = repositoryWith(
            tasks = listOf(task(id = 1), task(id = 2))
        )

        val result = requireNotNull(
            ApplyRoutineExecutionActionUseCase(repository) { 100L }(
                ROUTINE_ID,
                date,
                RoutineExecutionAction.START
            )
        )

        assertTrue(result.applied)
        assertEquals(RoutineExecutionState.IN_PROGRESS, result.execution.state)
        assertEquals(100L, result.execution.updatedAtEpochMillis)
        assertNull(result.history)
        assertEquals(RoutineExecutionState.IN_PROGRESS, repository.executions.single().state)
        assertTrue(repository.histories.isEmpty())
        assertTrue(repository.getTasks(ROUTINE_ID).none { it.completed })
    }

    @Test
    fun completeFromEveryExecutionStateCompletesEveryTaskAndStoresFullHistory() = runBlocking {
        RoutineExecutionState.values().forEach { initialState ->
            val repository = repositoryWith(
                tasks = listOf(
                    task(id = 1, completed = true, completedOn = date),
                    task(id = 2),
                    task(id = 3, completed = true, completedOn = date.minusDays(1))
                ),
                executionState = initialState
            )

            val result = requireNotNull(
                ApplyRoutineExecutionActionUseCase(repository) { 200L }(
                    ROUTINE_ID,
                    date,
                    RoutineExecutionAction.COMPLETE
                )
            )

            assertEquals(initialState.name, RoutineExecutionState.COMPLETED, result.execution.state)
            assertTrue(initialState.name, result.tasks.all { it.completed && it.completedOn == date })
            assertTrue(
                initialState.name,
                repository.getTasks(ROUTINE_ID).all { it.completed && it.completedOn == date }
            )
            assertEquals(initialState.name, RoutineExecutionState.COMPLETED, repository.executions.single().state)
            with(repository.histories.single()) {
                assertEquals(initialState.name, 3, completedTasks)
                assertEquals(initialState.name, 3, totalTasks)
                assertEquals(initialState.name, 100.0, completionPercentage, 0.0)
                assertEquals(initialState.name, RoutineExecutionState.COMPLETED, finalState)
            }
        }
    }

    @Test
    fun partialPreservesTasksAndStoresActualProgress() = runBlocking {
        val originalTasks = listOf(
            task(id = 1, completed = true, completedOn = date),
            task(id = 2)
        )
        val repository = repositoryWith(
            tasks = originalTasks,
            executionState = RoutineExecutionState.IN_PROGRESS
        )

        val result = requireNotNull(
            ApplyRoutineExecutionActionUseCase(repository) { 300L }(
                ROUTINE_ID,
                date,
                RoutineExecutionAction.PARTIAL
            )
        )

        assertTrue(result.applied)
        assertEquals(originalTasks, result.tasks)
        assertEquals(originalTasks, repository.getTasks(ROUTINE_ID))
        assertEquals(RoutineExecutionState.PARTIALLY_COMPLETED, result.execution.state)
        with(requireNotNull(result.history)) {
            assertEquals(1, completedTasks)
            assertEquals(2, totalTasks)
            assertEquals(50.0, completionPercentage, 0.0)
            assertEquals(RoutineExecutionState.PARTIALLY_COMPLETED, finalState)
        }
    }

    @Test
    fun skipPreservesTasksAndStoresSkippedOutcome() = runBlocking {
        val originalTasks = listOf(
            task(id = 1, completed = true, completedOn = date),
            task(id = 2)
        )
        val repository = repositoryWith(
            tasks = originalTasks,
            executionState = RoutineExecutionState.IN_PROGRESS
        )

        val result = requireNotNull(
            ApplyRoutineExecutionActionUseCase(repository) { 400L }(
                ROUTINE_ID,
                date,
                RoutineExecutionAction.SKIP
            )
        )

        assertTrue(result.applied)
        assertEquals(originalTasks, repository.getTasks(ROUTINE_ID))
        assertEquals(RoutineExecutionState.SKIPPED, result.execution.state)
        with(requireNotNull(result.history)) {
            assertEquals(1, completedTasks)
            assertEquals(2, totalTasks)
            assertEquals(50.0, completionPercentage, 0.0)
            assertEquals(RoutineExecutionState.SKIPPED, finalState)
        }
    }

    @Test
    fun notCompletedIsAppliedOnlyFromInProgress() = runBlocking {
        val repository = repositoryWith(
            tasks = listOf(task(id = 1)),
            executionState = RoutineExecutionState.IN_PROGRESS
        )

        val result = requireNotNull(
            ApplyRoutineExecutionActionUseCase(repository) { 500L }(
                ROUTINE_ID,
                date,
                RoutineExecutionAction.NOT_COMPLETED
            )
        )

        assertTrue(result.applied)
        assertEquals(RoutineExecutionState.NOT_COMPLETED, result.execution.state)
        assertEquals(RoutineExecutionState.NOT_COMPLETED, repository.executions.single().state)
        assertEquals(RoutineExecutionState.NOT_COMPLETED, repository.histories.single().finalState)
    }

    @Test
    fun notCompletedIsIgnoredFromTerminalStates() = runBlocking {
        RoutineExecutionState.values()
            .filter { it !in setOf(RoutineExecutionState.PENDING, RoutineExecutionState.IN_PROGRESS) }
            .forEach { initialState ->
                val repository = repositoryWith(
                    tasks = listOf(task(id = 1)),
                    executionState = initialState
                )

                val result = requireNotNull(
                    ApplyRoutineExecutionActionUseCase(repository) { 600L }(
                        ROUTINE_ID,
                        date,
                        RoutineExecutionAction.NOT_COMPLETED
                    )
                )

                assertFalse(initialState.name, result.applied)
                assertEquals(initialState.name, initialState, result.execution.state)
                assertEquals(initialState.name, initialState, repository.executions.single().state)
                assertTrue(initialState.name, repository.histories.isEmpty())
            }
    }

    @Test
    fun terminalActionExposesOneConsistentExecutionTaskAndHistorySnapshot() = runBlocking {
        val previousHistory = RoutineHistory(
            date = date,
            routineId = ROUTINE_ID,
            completedTasks = 1,
            totalTasks = 2,
            completionPercentage = 50.0,
            finalState = RoutineExecutionState.PARTIALLY_COMPLETED
        )
        val repository = repositoryWith(
            tasks = listOf(
                task(id = 1, completed = true, completedOn = date),
                task(id = 2)
            ),
            executionState = RoutineExecutionState.PARTIALLY_COMPLETED
        ).apply {
            histories += previousHistory
        }

        val result = requireNotNull(
            ApplyRoutineExecutionActionUseCase(repository) { 700L }(
                ROUTINE_ID,
                date,
                RoutineExecutionAction.COMPLETE
            )
        )

        assertEquals(result.execution, repository.executions.single())
        assertEquals(result.tasks, repository.getTasks(ROUTINE_ID))
        assertEquals(result.history, repository.histories.single())
        assertEquals(RoutineExecutionState.COMPLETED, repository.histories.single().finalState)
        assertEquals(2, repository.histories.single().completedTasks)
    }

    @Test
    fun completingFirstTaskMovesPendingRoutineToInProgress() = runBlocking {
        val first = task(id = 1)
        val repository = repositoryWith(
            tasks = listOf(first, task(id = 2)),
            executionState = RoutineExecutionState.PENDING
        )

        val result = requireNotNull(
            UpdateRoutineTaskProgressUseCase(repository) { 800L }(first, true, date)
        )

        assertEquals(RoutineExecutionState.IN_PROGRESS, result.execution.state)
        assertTrue(result.tasks.single { it.id == first.id }.completed)
        assertEquals(date, result.tasks.single { it.id == first.id }.completedOn)
        assertTrue(repository.histories.isEmpty())
        assertEquals(RoutineExecutionState.IN_PROGRESS, repository.executions.single().state)
    }

    @Test
    fun completingLastTaskCompletesRoutineAndStoresHistory() = runBlocking {
        val last = task(id = 2)
        val repository = repositoryWith(
            tasks = listOf(
                task(id = 1, completed = true, completedOn = date),
                last
            ),
            executionState = RoutineExecutionState.IN_PROGRESS
        )

        val result = requireNotNull(
            UpdateRoutineTaskProgressUseCase(repository) { 900L }(last, true, date)
        )

        assertEquals(RoutineExecutionState.COMPLETED, result.execution.state)
        assertTrue(result.tasks.all { it.completed && it.completedOn == date })
        assertNotNull(result.history)
        assertEquals(100.0, requireNotNull(result.history).completionPercentage, 0.0)
        assertEquals(RoutineExecutionState.COMPLETED, repository.histories.single().finalState)
    }

    @Test
    fun uncheckingTaskReopensCompletedRoutineAndRemovesFinalHistory() = runBlocking {
        val taskToReopen = task(id = 2, completed = true, completedOn = date)
        val repository = repositoryWith(
            tasks = listOf(
                task(id = 1, completed = true, completedOn = date),
                taskToReopen
            ),
            executionState = RoutineExecutionState.COMPLETED
        ).apply {
            histories += RoutineHistory(
                date = date,
                routineId = ROUTINE_ID,
                completedTasks = 2,
                totalTasks = 2,
                completionPercentage = 100.0,
                finalState = RoutineExecutionState.COMPLETED
            )
        }

        val result = requireNotNull(
            UpdateRoutineTaskProgressUseCase(repository) { 1_000L }(
                taskToReopen,
                false,
                date
            )
        )

        assertEquals(RoutineExecutionState.IN_PROGRESS, result.execution.state)
        assertFalse(result.tasks.single { it.id == taskToReopen.id }.completed)
        assertNull(result.tasks.single { it.id == taskToReopen.id }.completedOn)
        assertNull(result.history)
        assertTrue(repository.histories.isEmpty())
        assertEquals(RoutineExecutionState.IN_PROGRESS, repository.executions.single().state)
    }

    @Test
    fun concurrentTaskUpdatesPreserveBothChangesAndCompleteRoutine() = runBlocking {
        val first = task(id = 1)
        val second = task(id = 2)
        val repository = repositoryWith(
            tasks = listOf(first, second),
            executionState = RoutineExecutionState.PENDING
        )
        val start = CompletableDeferred<Unit>()

        coroutineScope {
            listOf(first, second).map { currentTask ->
                async(Dispatchers.Default) {
                    start.await()
                    UpdateRoutineTaskProgressUseCase(repository) { currentTask.id.toLong() }(
                        currentTask,
                        true,
                        date
                    )
                }
            }.also { start.complete(Unit) }.awaitAll()
        }

        assertTrue(repository.getTasks(ROUTINE_ID).all { it.completed && it.completedOn == date })
        assertEquals(RoutineExecutionState.COMPLETED, repository.executions.single().state)
        with(repository.histories.single()) {
            assertEquals(2, completedTasks)
            assertEquals(2, totalTasks)
            assertEquals(100.0, completionPercentage, 0.0)
            assertEquals(RoutineExecutionState.COMPLETED, finalState)
        }
    }

    @Test
    fun concurrentStartCannotOverwriteCompletion() = runBlocking {
        val repository = repositoryWith(
            tasks = listOf(task(id = 1), task(id = 2)),
            executionState = RoutineExecutionState.PENDING
        )
        val start = CompletableDeferred<Unit>()

        coroutineScope {
            listOf(RoutineExecutionAction.START, RoutineExecutionAction.COMPLETE).map { action ->
                async(Dispatchers.Default) {
                    start.await()
                    ApplyRoutineExecutionActionUseCase(repository) { action.ordinal.toLong() }(
                        ROUTINE_ID,
                        date,
                        action
                    )
                }
            }.also { start.complete(Unit) }.awaitAll()
        }

        assertEquals(RoutineExecutionState.COMPLETED, repository.executions.single().state)
        assertTrue(repository.getTasks(ROUTINE_ID).all { it.completed && it.completedOn == date })
        assertEquals(RoutineExecutionState.COMPLETED, repository.histories.single().finalState)
    }

    @Test
    fun taskProgressUsesPersistedTaskInsteadOfStaleCallerSnapshot() = runBlocking {
        val persisted = task(id = 1).copy(title = "Titulo actualizado", notes = "Dato reciente")
        val stale = task(id = 1).copy(title = "Titulo antiguo", notes = null)
        val repository = repositoryWith(
            tasks = listOf(persisted, task(id = 2)),
            executionState = RoutineExecutionState.PENDING
        )

        UpdateRoutineTaskProgressUseCase(repository) { 1_100L }(stale, true, date)

        with(repository.getTasks(ROUTINE_ID).single { it.id == persisted.id }) {
            assertEquals("Titulo actualizado", title)
            assertEquals("Dato reciente", notes)
            assertTrue(completed)
            assertEquals(date, completedOn)
        }
    }

    private fun repositoryWith(
        tasks: List<RoutineTask>,
        executionState: RoutineExecutionState? = null
    ) = FakeRoutineRepository().apply {
        routines += routine()
        tasksByRoutine[ROUTINE_ID] = tasks
        executionState?.let { state ->
            executions += RoutineDailyExecution(
                date = date,
                routineId = ROUTINE_ID,
                state = state,
                updatedAtEpochMillis = 1L
            )
        }
    }

    private fun routine() = Routine(
        id = ROUTINE_ID,
        name = "Mañana",
        description = "Inicio del día",
        category = "Día",
        icon = "wb_sunny",
        color = 0,
        enabled = true,
        period = RoutinePeriod.MORNING,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L
    )

    private fun task(
        id: Int,
        completed: Boolean = false,
        completedOn: LocalDate? = null
    ) = RoutineTask(
        id = id,
        routineId = ROUTINE_ID,
        title = "Actividad $id",
        orderPriority = id - 1,
        completed = completed,
        completedOn = completedOn
    )

    private companion object {
        const val ROUTINE_ID = 7
    }
}
