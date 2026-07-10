package com.luistureo.voicereminderapp.domain.routine

import com.luistureo.voicereminderapp.domain.routine.model.Routine
import com.luistureo.voicereminderapp.domain.routine.model.RoutineDailyProgress
import com.luistureo.voicereminderapp.domain.routine.model.RoutinePeriod
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTask
import com.luistureo.voicereminderapp.domain.routine.model.ordered
import com.luistureo.voicereminderapp.domain.routine.service.DailyRoutineResetPolicy
import com.luistureo.voicereminderapp.domain.routine.service.RoutineCompletionCalculator
import com.luistureo.voicereminderapp.domain.routine.usecase.SaveRoutineUseCase
import com.luistureo.voicereminderapp.domain.routine.validation.DuplicateActiveRoutineException
import com.luistureo.voicereminderapp.domain.routine.validation.RoutineValidationMessages
import com.luistureo.voicereminderapp.domain.routine.validation.RoutineValidator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class RoutineDomainRulesTest {
    @Test
    fun taskOrderingUsesPriority() {
        val tasks = listOf(
            RoutineTask(id = 2, title = "Segunda", orderPriority = 1),
            RoutineTask(id = 1, title = "Primera", orderPriority = 0)
        )

        assertEquals(listOf("Primera", "Segunda"), tasks.ordered().map { it.title })
    }

    @Test
    fun dailyResetDoesNotCarryProgressToNextDate() {
        val monday = LocalDate.of(2026, 7, 6)
        val tuesday = monday.plusDays(1)
        val previous = RoutineDailyProgress(
            date = monday,
            completedTaskIds = setOf(1, 2)
        )

        val reset = DailyRoutineResetPolicy.progressFor(tuesday, previous)

        assertEquals(tuesday, reset.date)
        assertTrue(reset.completedTaskIds.isEmpty())
    }

    @Test
    fun completionPercentageIsCalculatedAndBounded() {
        assertEquals(75.0, RoutineCompletionCalculator.percentage(3, 4), 0.0)
        assertEquals(100.0, RoutineCompletionCalculator.percentage(8, 4), 0.0)
        assertEquals(0.0, RoutineCompletionCalculator.percentage(0, 0), 0.0)
    }

    @Test
    fun rejectsDeadlineBeforeStartAndInvalidExplicitTaskOrdering() {
        val routine = routine().copy(
            startTime = LocalTime.of(9, 0),
            deadlineTime = LocalTime.of(8, 0)
        )
        val tasks = listOf(
            RoutineTask(title = "Primera", orderPriority = 0, optionalTime = LocalTime.of(8, 30)),
            RoutineTask(title = "Segunda", orderPriority = 1, optionalTime = LocalTime.of(8, 15))
        )

        val result = RoutineValidator().validate(routine, tasks)

        assertFalse(result.isValid)
        assertTrue(result.errors.contains(RoutineValidationMessages.INVALID_TIME_RANGE))
        assertTrue(result.errors.contains(RoutineValidationMessages.INVALID_TASK_ORDER))
    }

    @Test
    fun preventsSecondActiveRoutineForSamePeriod() = runBlocking {
        val repository = FakeRoutineRepository().apply { forcePeriodConflict = true }

        try {
            SaveRoutineUseCase(repository)(routine(), emptyList())
            fail("Se esperaba conflicto de período")
        } catch (error: DuplicateActiveRoutineException) {
            assertEquals(RoutineValidationMessages.DUPLICATE_ACTIVE_PERIOD, error.message)
        }
    }

    private fun routine() = Routine(
        name = "Rutina",
        description = "Descripción",
        category = "Día",
        icon = "schedule",
        color = 0,
        enabled = true,
        period = RoutinePeriod.MORNING,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L
    )
}
