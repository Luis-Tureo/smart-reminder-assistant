package com.luistureo.voicereminderapp.data.repository

import com.luistureo.voicereminderapp.data.local.dao.ActiveRoutinePeriodConflictException
import com.luistureo.voicereminderapp.data.local.dao.RoutineDao
import com.luistureo.voicereminderapp.data.local.dao.RoutineExecutionMutationEntity
import com.luistureo.voicereminderapp.data.local.dao.RoutineGraphEntity
import com.luistureo.voicereminderapp.data.local.dao.RoutineTemplateGraphEntity
import com.luistureo.voicereminderapp.data.mapper.toDomain
import com.luistureo.voicereminderapp.data.mapper.toEntity
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
import com.luistureo.voicereminderapp.domain.routine.validation.DuplicateActiveRoutineException
import java.time.LocalDate

class RoutineRepositoryImpl(private val routineDao: RoutineDao) : RoutineRepository {
    override suspend fun initializeDefaults(
        routines: List<RoutineDraft>,
        templates: List<RoutineTemplate>
    ): Boolean = routineDao.initializeDefaults(
        routines = routines.map { draft ->
            RoutineGraphEntity(draft.routine.toEntity(), draft.tasks.map { it.toEntity() })
        },
        templates = templates.map { template ->
            RoutineTemplateGraphEntity(
                template.toEntity(),
                template.suggestedTasks.map { it.toEntity() }
            )
        }
    )

    override suspend fun saveRoutine(routine: Routine, tasks: List<RoutineTask>): Int = try {
        routineDao.saveRoutineGraph(
            RoutineGraphEntity(routine.toEntity(), tasks.map { it.toEntity() })
        )
    } catch (_: ActiveRoutinePeriodConflictException) {
        throw DuplicateActiveRoutineException()
    }

    override suspend fun getRoutines(): List<Routine> = routineDao.getRoutines().map { it.toDomain() }

    override suspend fun getRoutineById(routineId: Int): Routine? =
        routineDao.getRoutineById(routineId)?.toDomain()

    override suspend fun getTasks(routineId: Int): List<RoutineTask> =
        routineDao.getTasksForRoutine(routineId).map { it.toDomain() }

    override suspend fun updateTask(task: RoutineTask) {
        routineDao.updateTask(task.toEntity())
    }

    override suspend fun hasOtherActiveRoutine(
        period: RoutinePeriod,
        excludedRoutineId: Int
    ): Boolean = routineDao.countOtherActiveRoutines(period.name, excludedRoutineId) > 0

    override suspend fun deleteRoutine(routineId: Int) = routineDao.deleteRoutine(routineId)

    override suspend fun prepareDay(date: LocalDate) {
        routineDao.resetTaskCompletionFromOtherDays(date.toEpochDay())
    }

    override suspend fun saveDailyExecution(execution: RoutineDailyExecution) {
        routineDao.saveDailyExecution(execution.toEntity())
    }

    override suspend fun getDailyExecution(
        routineId: Int,
        date: LocalDate
    ): RoutineDailyExecution? =
        routineDao.getDailyExecution(routineId, date.toEpochDay())?.toDomain()

    override suspend fun updateExecutionAtomically(
        routineId: Int,
        date: LocalDate,
        transform: (
            routine: Routine,
            tasks: List<RoutineTask>,
            execution: RoutineDailyExecution?
        ) -> RoutineExecutionResult?
    ): RoutineExecutionResult? = routineDao.mutateRoutineExecution(
        routineId = routineId,
        dateEpochDay = date.toEpochDay()
    ) { snapshot ->
        val routine = snapshot.routine.toDomain()
        transform(
            routine,
            snapshot.tasks.map { it.toDomain() },
            snapshot.execution?.toDomain()
        )?.let { result ->
            require(result.routine == routine)
            RoutineExecutionMutationEntity(
                routine = snapshot.routine,
                tasks = result.tasks.map { it.toEntity() },
                execution = result.execution.toEntity(),
                history = result.history?.toEntity(),
                applied = result.applied
            )
        }
    }?.let { mutation ->
        RoutineExecutionResult(
            routine = mutation.routine.toDomain(),
            tasks = mutation.tasks.map { it.toDomain() },
            execution = mutation.execution.toDomain(),
            history = mutation.history?.toDomain(),
            applied = mutation.applied
        )
    }

    override suspend fun saveHistory(history: RoutineHistory) {
        routineDao.saveHistory(history.toEntity())
    }

    override suspend fun saveTaskProgress(
        task: RoutineTask,
        execution: RoutineDailyExecution,
        history: RoutineHistory?
    ) {
        routineDao.saveTaskProgress(task.toEntity(), execution.toEntity(), history?.toEntity())
    }

    override suspend fun finalizeDay(
        tasks: List<RoutineTask>,
        execution: RoutineDailyExecution,
        history: RoutineHistory
    ) {
        routineDao.finalizeRoutineDay(
            tasks.map { it.toEntity() },
            execution.toEntity(),
            history.toEntity()
        )
    }

    override suspend fun deleteHistory(routineId: Int, date: LocalDate) {
        routineDao.deleteHistoryForDate(routineId, date.toEpochDay())
    }

    override suspend fun getHistory(routineId: Int): List<RoutineHistory> =
        routineDao.getHistory(routineId).map { it.toDomain() }

    override suspend fun getHistoryBetween(startDate: LocalDate, endDate: LocalDate) =
        routineDao.getHistoryBetween(startDate.toEpochDay(), endDate.toEpochDay()).map { it.toDomain() }

    override suspend fun getAllHistory() = routineDao.getAllHistory().map { it.toDomain() }

    override suspend fun getTemplates(): List<RoutineTemplate> =
        routineDao.getTemplates().map { template ->
            template.toDomain(routineDao.getTemplateTasks(template.id))
        }

    override suspend fun saveTemplate(template: RoutineTemplate): Int =
        routineDao.saveTemplateGraph(
            RoutineTemplateGraphEntity(
                template.toEntity(),
                template.suggestedTasks.map { it.toEntity() }
            )
        )

    override suspend fun deletePersonalTemplate(templateId: Int): Boolean =
        routineDao.deletePersonalTemplate(templateId) > 0

    override suspend fun restoreBuiltInTemplates(templates: List<RoutineTemplate>) {
        routineDao.restoreBuiltInTemplates(
            templates.map {
                RoutineTemplateGraphEntity(it.toEntity(), it.suggestedTasks.map { task -> task.toEntity() })
            }
        )
    }

    override suspend fun saveSuggestion(suggestion: RoutineSuggestion): Int =
        routineDao.saveSuggestion(suggestion.toEntity()).toInt()

    override suspend fun getActiveSuggestions(): List<RoutineSuggestion> =
        routineDao.getActiveSuggestions().map { it.toDomain() }

    override suspend fun getLatestSuggestion(routineId: Int, type: String): RoutineSuggestion? =
        routineDao.getLatestSuggestion(routineId, type)?.toDomain()

    override suspend fun countSuggestionsForDay(date: LocalDate): Int =
        routineDao.countSuggestionsForDay(date.toEpochDay())

    override suspend fun dismissSuggestion(id: Int, date: LocalDate) {
        routineDao.dismissSuggestion(id, date.toEpochDay())
    }
}
