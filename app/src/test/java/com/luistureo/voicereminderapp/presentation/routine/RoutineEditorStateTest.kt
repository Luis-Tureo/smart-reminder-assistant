package com.luistureo.voicereminderapp.presentation.routine

import com.luistureo.voicereminderapp.domain.routine.FakeRoutineRepository
import com.luistureo.voicereminderapp.domain.routine.model.Routine
import com.luistureo.voicereminderapp.domain.routine.model.RoutinePeriod
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTemplateTask
import com.luistureo.voicereminderapp.domain.routine.usecase.SaveRoutineUseCase
import com.luistureo.voicereminderapp.presentation.routine.state.RoutineEditorState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineEditorStateTest {
    @Test
    fun userCanAddEditDeleteAndReorderTasks() {
        var state = RoutineEditorState()
            .addTask("Agua")
            .addTask("Desayuno")
            .addTask("Agenda")

        state = state.editTask(state.tasks[1].copy(title = "Desayuno saludable"))
        state = state.moveTask(2, 0)
        state = state.deleteTask(state.tasks.last().localId)

        assertEquals(listOf("Agenda", "Agua"), state.tasks.map { it.title })
        assertEquals(listOf(0, 1), state.tasks.mapIndexed { index, task ->
            task.toDomain(8, index).orderPriority
        })
    }

    @Test
    fun templateSelectionCreatesEditableTasksWithoutSavingAutomatically() {
        val state = RoutineEditorState.fromTemplate(
            listOf(
                RoutineTemplateTask(title = "Segunda", orderPriority = 1),
                RoutineTemplateTask(title = "Primera", orderPriority = 0)
            )
        )

        assertEquals(listOf("Primera", "Segunda"), state.tasks.map { it.title })
        assertTrue(state.tasks.all { it.persistedId == 0 })
        assertEquals("Primera editada", state.editTask(state.tasks[0].copy(title = "Primera editada")).tasks[0].title)
    }

    @Test
    fun editingRoutineSavesNameAndTaskChanges() = runBlocking {
        val repository = FakeRoutineRepository()
        val editor = RoutineEditorState().addTask("Actividad editada")
        val routine = Routine(
            name = "Rutina editada",
            description = "Descripción",
            category = "Día",
            icon = "wb_sunny",
            color = 0,
            enabled = false,
            period = RoutinePeriod.MORNING,
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 2L
        )

        val id = SaveRoutineUseCase(repository)(
            routine,
            editor.tasks.mapIndexed { index, task -> task.toDomain(0, index) }
        )

        assertEquals("Rutina editada", repository.getRoutineById(id)?.name)
        assertEquals("Actividad editada", repository.getTasks(id).single().title)
    }
}
