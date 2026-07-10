package com.luistureo.voicereminderapp.domain.routine

import com.luistureo.voicereminderapp.domain.routine.factory.RoutineDraft
import com.luistureo.voicereminderapp.domain.routine.model.Routine
import com.luistureo.voicereminderapp.domain.routine.model.RoutineDailyExecution
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionResult
import com.luistureo.voicereminderapp.domain.routine.model.RoutineHistory
import com.luistureo.voicereminderapp.domain.routine.model.RoutinePeriod
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTask
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTemplate
import com.luistureo.voicereminderapp.domain.routine.model.RoutineSuggestion
import com.luistureo.voicereminderapp.domain.routine.repository.RoutineRepository
import java.time.LocalDate

internal class FakeRoutineRepository : RoutineRepository {
    val routines = mutableListOf<Routine>()
    val tasksByRoutine = mutableMapOf<Int, List<RoutineTask>>()
    val histories = mutableListOf<RoutineHistory>()
    val executions = mutableListOf<RoutineDailyExecution>()
    val templates = mutableListOf<RoutineTemplate>()
    val suggestions = mutableListOf<RoutineSuggestion>()
    var preparedDate: LocalDate? = null
    var forcePeriodConflict = false
    private val executionTransactionLock = Any()

    override suspend fun initializeDefaults(
        routines: List<RoutineDraft>,
        templates: List<RoutineTemplate>
    ): Boolean {
        if (this.routines.isNotEmpty()) return false
        routines.forEachIndexed { index, draft ->
            val id = index + 1
            this.routines += draft.routine.copy(id = id)
            tasksByRoutine[id] = draft.tasks.map { it.copy(routineId = id) }
        }
        this.templates += templates
        return true
    }

    override suspend fun saveRoutine(routine: Routine, tasks: List<RoutineTask>): Int {
        val id = routine.id.takeIf { it != 0 } ?: (routines.maxOfOrNull { it.id } ?: 0) + 1
        routines.removeAll { it.id == id }
        routines += routine.copy(id = id)
        tasksByRoutine[id] = tasks.map { it.copy(routineId = id) }
        return id
    }

    override suspend fun getRoutines(): List<Routine> = routines.toList()
    override suspend fun getRoutineById(routineId: Int): Routine? =
        routines.firstOrNull { it.id == routineId }

    override suspend fun getTasks(routineId: Int): List<RoutineTask> =
        tasksByRoutine[routineId].orEmpty()

    override suspend fun updateTask(task: RoutineTask) {
        tasksByRoutine[task.routineId] = tasksByRoutine[task.routineId].orEmpty().map {
            if (it.id == task.id) task else it
        }
    }

    override suspend fun hasOtherActiveRoutine(
        period: RoutinePeriod,
        excludedRoutineId: Int
    ): Boolean = forcePeriodConflict || routines.any {
        it.enabled && it.period == period && it.id != excludedRoutineId
    }

    override suspend fun deleteRoutine(routineId: Int) {
        routines.removeAll { it.id == routineId }
        tasksByRoutine.remove(routineId)
    }

    override suspend fun prepareDay(date: LocalDate) {
        preparedDate = date
    }

    override suspend fun saveDailyExecution(execution: RoutineDailyExecution) {
        executions.removeAll {
            it.routineId == execution.routineId && it.date == execution.date
        }
        executions += execution
    }

    override suspend fun getDailyExecution(
        routineId: Int,
        date: LocalDate
    ): RoutineDailyExecution? = executions.firstOrNull {
        it.routineId == routineId && it.date == date
    }

    override suspend fun updateExecutionAtomically(
        routineId: Int,
        date: LocalDate,
        transform: (
            routine: Routine,
            tasks: List<RoutineTask>,
            execution: RoutineDailyExecution?
        ) -> RoutineExecutionResult?
    ): RoutineExecutionResult? = synchronized(executionTransactionLock) {
        val routine = routines.firstOrNull { it.id == routineId } ?: return@synchronized null
        val tasks = tasksByRoutine[routineId].orEmpty()
        val execution = executions.firstOrNull {
            it.routineId == routineId && it.date == date
        }
        val result = transform(routine, tasks, execution) ?: return@synchronized null
        if (!result.applied) return@synchronized result

        tasksByRoutine[routineId] = result.tasks
        executions.removeAll { it.routineId == routineId && it.date == date }
        executions += result.execution
        histories.removeAll { it.routineId == routineId && it.date == date }
        result.history?.let(histories::add)
        result
    }

    override suspend fun saveHistory(history: RoutineHistory) {
        histories.removeAll { it.routineId == history.routineId && it.date == history.date }
        histories += history
    }

    override suspend fun saveTaskProgress(
        task: RoutineTask,
        execution: RoutineDailyExecution,
        history: RoutineHistory?
    ) {
        updateTask(task)
        saveDailyExecution(execution)
        histories.removeAll { it.routineId == execution.routineId && it.date == execution.date }
        if (history != null) histories += history
    }

    override suspend fun finalizeDay(
        tasks: List<RoutineTask>,
        execution: RoutineDailyExecution,
        history: RoutineHistory
    ) {
        tasks.forEach { updateTask(it) }
        saveDailyExecution(execution)
        saveHistory(history)
    }

    override suspend fun deleteHistory(routineId: Int, date: LocalDate) {
        histories.removeAll { it.routineId == routineId && it.date == date }
    }

    override suspend fun getHistory(routineId: Int): List<RoutineHistory> =
        histories.filter { it.routineId == routineId }

    override suspend fun getHistoryBetween(startDate: LocalDate, endDate: LocalDate) =
        histories.filter { !it.date.isBefore(startDate) && !it.date.isAfter(endDate) }
    override suspend fun getAllHistory() = histories.toList()

    override suspend fun getTemplates(): List<RoutineTemplate> = templates.toList()

    override suspend fun saveTemplate(template: RoutineTemplate): Int {
        val id = template.id.takeIf { it != 0 } ?: (templates.maxOfOrNull { it.id } ?: 0) + 1
        templates.removeAll { it.id == id }
        templates += template.copy(id = id)
        return id
    }

    override suspend fun deletePersonalTemplate(templateId: Int): Boolean =
        templates.removeIf { it.id == templateId && !it.builtIn }

    override suspend fun restoreBuiltInTemplates(templates: List<RoutineTemplate>) {
        this.templates.removeAll { it.builtIn }
        templates.forEach { saveTemplate(it.copy(id = 0)) }
    }

    override suspend fun saveSuggestion(suggestion: RoutineSuggestion): Int {
        val id = suggestion.id.takeIf { it != 0 } ?: (suggestions.maxOfOrNull { it.id } ?: 0) + 1
        suggestions.removeAll { it.id == id }
        suggestions += suggestion.copy(id = id)
        return id
    }

    override suspend fun getActiveSuggestions() = suggestions.filter { it.active }
    override suspend fun getLatestSuggestion(routineId: Int, type: String) = suggestions
        .filter { it.routineId == routineId && it.type.name == type }
        .maxByOrNull { it.createdAtEpochDay }
    override suspend fun countSuggestionsForDay(date: LocalDate) =
        suggestions.count { it.createdAtEpochDay == date.toEpochDay() }
    override suspend fun dismissSuggestion(id: Int, date: LocalDate) {
        val index = suggestions.indexOfFirst { it.id == id }
        if (index >= 0) suggestions[index] = suggestions[index].copy(
            active = false,
            dismissedAtEpochDay = date.toEpochDay()
        )
    }
}
