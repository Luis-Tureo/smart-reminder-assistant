package com.luistureo.voicereminderapp.presentation.routine

import com.luistureo.voicereminderapp.domain.routine.model.Routine
import com.luistureo.voicereminderapp.domain.routine.model.RoutinePeriod
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTask
import com.luistureo.voicereminderapp.presentation.routine.state.RoutinePresentationPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RoutinePresentationPolicyTest {
    private val today = LocalDate.of(2026, 7, 9)

    @Test
    fun taskCheckboxUpdatesProgressImmediately() {
        val first = RoutineTask(id = 1, routineId = 4, title = "Agua", orderPriority = 0)
        val second = RoutineTask(id = 2, routineId = 4, title = "Desayuno", orderPriority = 1)

        val completed = RoutinePresentationPolicy.toggleTask(first, true, today)
        val progress = RoutinePresentationPolicy.dashboardItem(routine(), listOf(completed, second), today)

        assertTrue(completed.completed)
        assertEquals(today, completed.completedOn)
        assertEquals(1, progress.completedTasks)
        assertEquals(50, progress.percentage)
    }

    @Test
    fun uncheckingTaskRemovesDailyCompletion() {
        val task = RoutineTask(
            id = 1,
            routineId = 4,
            title = "Agua",
            orderPriority = 0,
            completed = true,
            completedOn = today
        )

        val pending = RoutinePresentationPolicy.toggleTask(task, false, today)

        assertFalse(pending.completed)
        assertNull(pending.completedOn)
    }

    @Test
    fun disabledRoutineHidesActiveActions() {
        assertFalse(RoutinePresentationPolicy.activeActionsVisible(routine().copy(enabled = false)))
        assertTrue(RoutinePresentationPolicy.activeActionsVisible(routine()))
    }

    private fun routine() = Routine(
        id = 4,
        name = "Mañana",
        description = "Inicio",
        category = "Día",
        icon = "wb_sunny",
        color = 0,
        enabled = true,
        period = RoutinePeriod.MORNING,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L
    )
}
