package com.luistureo.voicereminderapp.presentation.routine.state

import com.luistureo.voicereminderapp.domain.routine.model.RoutineTask
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTemplateTask
import java.time.LocalDate
import java.time.LocalTime

data class RoutineTaskDraft(
    val localId: Long,
    val persistedId: Int = 0,
    val title: String,
    val completed: Boolean = false,
    val completedOn: LocalDate? = null,
    val optionalTime: LocalTime? = null,
    val estimatedDurationMinutes: Int? = null,
    val notes: String? = null
) {
    fun toDomain(routineId: Int, orderPriority: Int) = RoutineTask(
        id = persistedId,
        routineId = routineId,
        title = title,
        orderPriority = orderPriority,
        completed = completed,
        completedOn = completedOn,
        optionalTime = optionalTime,
        estimatedDurationMinutes = estimatedDurationMinutes,
        notes = notes
    )
}

data class RoutineEditorState(
    val tasks: List<RoutineTaskDraft> = emptyList(),
    private val nextLocalId: Long = 1L
) {
    fun addTask(
        title: String,
        optionalTime: LocalTime? = null,
        estimatedDurationMinutes: Int? = null,
        notes: String? = null
    ): RoutineEditorState = copy(
        tasks = tasks + RoutineTaskDraft(
            localId = nextLocalId,
            title = title,
            optionalTime = optionalTime,
            estimatedDurationMinutes = estimatedDurationMinutes,
            notes = notes
        ),
        nextLocalId = nextLocalId + 1
    )

    fun editTask(task: RoutineTaskDraft): RoutineEditorState = copy(
        tasks = tasks.map { current -> if (current.localId == task.localId) task else current }
    )

    fun deleteTask(localId: Long): RoutineEditorState = copy(
        tasks = tasks.filterNot { it.localId == localId }
    )

    fun moveTask(fromPosition: Int, toPosition: Int): RoutineEditorState {
        if (fromPosition !in tasks.indices || toPosition !in tasks.indices) return this
        val reordered = tasks.toMutableList()
        val task = reordered.removeAt(fromPosition)
        reordered.add(toPosition, task)
        return copy(tasks = reordered)
    }

    companion object {
        fun fromTasks(tasks: List<RoutineTask>): RoutineEditorState {
            val drafts = tasks.mapIndexed { index, task ->
                RoutineTaskDraft(
                    localId = index + 1L,
                    persistedId = task.id,
                    title = task.title,
                    completed = task.completed,
                    completedOn = task.completedOn,
                    optionalTime = task.optionalTime,
                    estimatedDurationMinutes = task.estimatedDurationMinutes,
                    notes = task.notes
                )
            }
            return RoutineEditorState(drafts, drafts.size + 1L)
        }

        fun fromTemplate(tasks: List<RoutineTemplateTask>): RoutineEditorState {
            val drafts = tasks.sortedBy { it.orderPriority }.mapIndexed { index, task ->
                RoutineTaskDraft(
                    localId = index + 1L,
                    title = task.title,
                    estimatedDurationMinutes = task.estimatedDurationMinutes
                )
            }
            return RoutineEditorState(drafts, drafts.size + 1L)
        }
    }
}
