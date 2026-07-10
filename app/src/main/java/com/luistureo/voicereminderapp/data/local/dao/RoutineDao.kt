package com.luistureo.voicereminderapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.luistureo.voicereminderapp.data.local.entity.RoutineDailyExecutionEntity
import com.luistureo.voicereminderapp.data.local.entity.RoutineEntity
import com.luistureo.voicereminderapp.data.local.entity.RoutineHistoryEntity
import com.luistureo.voicereminderapp.data.local.entity.RoutineTaskEntity
import com.luistureo.voicereminderapp.data.local.entity.RoutineTemplateEntity
import com.luistureo.voicereminderapp.data.local.entity.RoutineTemplateTaskEntity
import com.luistureo.voicereminderapp.data.local.entity.RoutineSuggestionEntity

data class RoutineGraphEntity(
    val routine: RoutineEntity,
    val tasks: List<RoutineTaskEntity>
)

data class RoutineTemplateGraphEntity(
    val template: RoutineTemplateEntity,
    val tasks: List<RoutineTemplateTaskEntity>
)

data class RoutineExecutionSnapshotEntity(
    val routine: RoutineEntity,
    val tasks: List<RoutineTaskEntity>,
    val execution: RoutineDailyExecutionEntity?
)

data class RoutineExecutionMutationEntity(
    val routine: RoutineEntity,
    val tasks: List<RoutineTaskEntity>,
    val execution: RoutineDailyExecutionEntity,
    val history: RoutineHistoryEntity?,
    val applied: Boolean
)

class ActiveRoutinePeriodConflictException : IllegalStateException()

@Dao
abstract class RoutineDao {
    @Query("SELECT COUNT(*) FROM routines")
    abstract suspend fun countRoutines(): Int

    @Insert
    abstract suspend fun insertRoutine(routine: RoutineEntity): Long

    @Update
    abstract suspend fun updateRoutine(routine: RoutineEntity)

    @Insert
    abstract suspend fun insertTasks(tasks: List<RoutineTaskEntity>)

    @Query("DELETE FROM routine_tasks WHERE routineId = :routineId")
    abstract suspend fun deleteTasksForRoutine(routineId: Int)

    @Query("SELECT * FROM routines ORDER BY period ASC, id ASC")
    abstract suspend fun getRoutines(): List<RoutineEntity>

    @Query("SELECT * FROM routines WHERE id = :routineId LIMIT 1")
    abstract suspend fun getRoutineById(routineId: Int): RoutineEntity?

    @Query("SELECT * FROM routine_tasks WHERE routineId = :routineId ORDER BY orderPriority ASC, id ASC")
    abstract suspend fun getTasksForRoutine(routineId: Int): List<RoutineTaskEntity>

    @Update
    abstract suspend fun updateTask(task: RoutineTaskEntity)

    @Update
    abstract suspend fun updateTasks(tasks: List<RoutineTaskEntity>)

    @Query(
        "SELECT COUNT(*) FROM routines " +
            "WHERE period = :period AND enabled = 1 AND id != :excludedRoutineId"
    )
    abstract suspend fun countOtherActiveRoutines(period: String, excludedRoutineId: Int): Int

    @Query("DELETE FROM routines WHERE id = :routineId")
    abstract suspend fun deleteRoutine(routineId: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveDailyExecution(execution: RoutineDailyExecutionEntity): Long

    @Query(
        "SELECT * FROM routine_daily_executions " +
            "WHERE routineId = :routineId AND dateEpochDay = :dateEpochDay LIMIT 1"
    )
    abstract suspend fun getDailyExecution(
        routineId: Int,
        dateEpochDay: Long
    ): RoutineDailyExecutionEntity?

    @Query(
        "UPDATE routine_tasks SET completed = 0, completedOnEpochDay = NULL " +
            "WHERE completed = 1 AND (completedOnEpochDay IS NULL OR completedOnEpochDay != :dateEpochDay)"
    )
    abstract suspend fun resetTaskCompletionFromOtherDays(dateEpochDay: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveHistory(history: RoutineHistoryEntity): Long

    @Query("SELECT * FROM routine_history WHERE routineId = :routineId ORDER BY dateEpochDay ASC")
    abstract suspend fun getHistory(routineId: Int): List<RoutineHistoryEntity>

    @Query(
        "SELECT * FROM routine_history WHERE dateEpochDay BETWEEN :startEpochDay AND :endEpochDay " +
            "ORDER BY dateEpochDay ASC"
    )
    abstract suspend fun getHistoryBetween(
        startEpochDay: Long,
        endEpochDay: Long
    ): List<RoutineHistoryEntity>

    @Query("SELECT * FROM routine_history ORDER BY dateEpochDay ASC")
    abstract suspend fun getAllHistory(): List<RoutineHistoryEntity>

    @Query("DELETE FROM routine_history WHERE routineId = :routineId AND dateEpochDay = :dateEpochDay")
    abstract suspend fun deleteHistoryForDate(routineId: Int, dateEpochDay: Long)

    @Query("SELECT COUNT(*) FROM routine_templates")
    abstract suspend fun countTemplates(): Int

    @Insert
    abstract suspend fun insertTemplate(template: RoutineTemplateEntity): Long

    @Update
    abstract suspend fun updateTemplate(template: RoutineTemplateEntity)

    @Insert
    abstract suspend fun insertTemplateTasks(tasks: List<RoutineTemplateTaskEntity>)

    @Query("DELETE FROM routine_template_tasks WHERE templateId = :templateId")
    abstract suspend fun deleteTemplateTasks(templateId: Int)

    @Query("DELETE FROM routine_templates WHERE id = :templateId AND builtIn = 0")
    abstract suspend fun deletePersonalTemplate(templateId: Int): Int

    @Query("DELETE FROM routine_templates WHERE builtIn = 1")
    abstract suspend fun deleteBuiltInTemplates()

    @Query("SELECT * FROM routine_templates ORDER BY id ASC")
    abstract suspend fun getTemplates(): List<RoutineTemplateEntity>

    @Query(
        "SELECT * FROM routine_template_tasks " +
            "WHERE templateId = :templateId ORDER BY orderPriority ASC, id ASC"
    )
    abstract suspend fun getTemplateTasks(templateId: Int): List<RoutineTemplateTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveSuggestion(suggestion: RoutineSuggestionEntity): Long

    @Query(
        "SELECT * FROM routine_suggestions WHERE active = 1 " +
            "ORDER BY createdAtEpochDay DESC, id DESC"
    )
    abstract suspend fun getActiveSuggestions(): List<RoutineSuggestionEntity>

    @Query(
        "SELECT * FROM routine_suggestions WHERE routineId = :routineId AND type = :type " +
            "ORDER BY createdAtEpochDay DESC LIMIT 1"
    )
    abstract suspend fun getLatestSuggestion(
        routineId: Int,
        type: String
    ): RoutineSuggestionEntity?

    @Query("SELECT COUNT(*) FROM routine_suggestions WHERE createdAtEpochDay = :epochDay")
    abstract suspend fun countSuggestionsForDay(epochDay: Long): Int

    @Query(
        "UPDATE routine_suggestions SET active = 0, dismissedAtEpochDay = :epochDay WHERE id = :id"
    )
    abstract suspend fun dismissSuggestion(id: Int, epochDay: Long)

    @Transaction
    open suspend fun initializeDefaults(
        routines: List<RoutineGraphEntity>,
        templates: List<RoutineTemplateGraphEntity>
    ): Boolean {
        if (countRoutines() > 0) return false
        routines.forEach { graph ->
            val routineId = insertRoutine(graph.routine).toInt()
            insertTasks(graph.tasks.map { it.copy(routineId = routineId) })
        }
        if (countTemplates() == 0) {
            templates.forEach { graph ->
                val templateId = insertTemplate(graph.template).toInt()
                insertTemplateTasks(graph.tasks.map { it.copy(templateId = templateId) })
            }
        }
        return true
    }

    @Transaction
    open suspend fun saveRoutineGraph(graph: RoutineGraphEntity): Int {
        if (
            graph.routine.enabled &&
            countOtherActiveRoutines(graph.routine.period, graph.routine.id) > 0
        ) {
            throw ActiveRoutinePeriodConflictException()
        }
        val routineId = if (graph.routine.id == 0) {
            insertRoutine(graph.routine).toInt()
        } else {
            updateRoutine(graph.routine)
            graph.routine.id
        }
        deleteTasksForRoutine(routineId)
        if (graph.tasks.isNotEmpty()) {
            insertTasks(graph.tasks.map { it.copy(routineId = routineId) })
        }
        return routineId
    }

    @Transaction
    open suspend fun saveTemplateGraph(graph: RoutineTemplateGraphEntity): Int {
        val templateId = if (graph.template.id == 0) {
            insertTemplate(graph.template).toInt()
        } else {
            updateTemplate(graph.template)
            graph.template.id
        }
        deleteTemplateTasks(templateId)
        if (graph.tasks.isNotEmpty()) {
            insertTemplateTasks(graph.tasks.map { it.copy(templateId = templateId) })
        }
        return templateId
    }

    @Transaction
    open suspend fun restoreBuiltInTemplates(templates: List<RoutineTemplateGraphEntity>) {
        deleteBuiltInTemplates()
        templates.forEach { graph ->
            val templateId = insertTemplate(graph.template.copy(id = 0)).toInt()
            insertTemplateTasks(graph.tasks.map { it.copy(id = 0, templateId = templateId) })
        }
    }

    @Transaction
    open suspend fun saveTaskProgress(
        task: RoutineTaskEntity,
        execution: RoutineDailyExecutionEntity,
        history: RoutineHistoryEntity?
    ) {
        updateTask(task)
        saveDailyExecution(execution)
        if (history == null) {
            deleteHistoryForDate(execution.routineId, execution.dateEpochDay)
        } else {
            saveHistory(history)
        }
    }

    @Transaction
    open suspend fun finalizeRoutineDay(
        tasks: List<RoutineTaskEntity>,
        execution: RoutineDailyExecutionEntity,
        history: RoutineHistoryEntity
    ) {
        if (tasks.isNotEmpty()) updateTasks(tasks)
        saveDailyExecution(execution)
        saveHistory(history)
    }

    @Transaction
    open suspend fun mutateRoutineExecution(
        routineId: Int,
        dateEpochDay: Long,
        transform: (RoutineExecutionSnapshotEntity) -> RoutineExecutionMutationEntity?
    ): RoutineExecutionMutationEntity? {
        val routine = getRoutineById(routineId) ?: return null
        val snapshot = RoutineExecutionSnapshotEntity(
            routine = routine,
            tasks = getTasksForRoutine(routineId),
            execution = getDailyExecution(routineId, dateEpochDay)
        )
        val mutation = transform(snapshot) ?: return null
        validateMutation(snapshot, mutation, dateEpochDay)
        if (!mutation.applied) return mutation

        if (mutation.tasks.isNotEmpty()) updateTasks(mutation.tasks)
        saveDailyExecution(mutation.execution)
        if (mutation.history == null) {
            deleteHistoryForDate(routineId, dateEpochDay)
        } else {
            saveHistory(mutation.history)
        }
        return mutation
    }

    private fun validateMutation(
        snapshot: RoutineExecutionSnapshotEntity,
        mutation: RoutineExecutionMutationEntity,
        dateEpochDay: Long
    ) {
        require(mutation.routine == snapshot.routine)
        require(mutation.execution.routineId == snapshot.routine.id)
        require(mutation.execution.dateEpochDay == dateEpochDay)
        require(mutation.execution.id == (snapshot.execution?.id ?: 0))
        require(mutation.tasks.size == snapshot.tasks.size)
        require(mutation.tasks.map { it.id }.toSet() == snapshot.tasks.map { it.id }.toSet())
        require(mutation.tasks.all { it.routineId == snapshot.routine.id })
        val storedTasksById = snapshot.tasks.associateBy { it.id }
        mutation.tasks.forEach { task ->
            val stored = requireNotNull(storedTasksById[task.id])
            require(
                task.copy(
                    completed = stored.completed,
                    completedOnEpochDay = stored.completedOnEpochDay
                ) == stored
            )
        }
        mutation.history?.let { history ->
            require(history.routineId == snapshot.routine.id)
            require(history.dateEpochDay == dateEpochDay)
        }
    }
}
