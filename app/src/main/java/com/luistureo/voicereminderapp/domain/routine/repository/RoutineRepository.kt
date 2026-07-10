package com.luistureo.voicereminderapp.domain.routine.repository

import com.luistureo.voicereminderapp.domain.routine.factory.RoutineDraft
import com.luistureo.voicereminderapp.domain.routine.model.Routine
import com.luistureo.voicereminderapp.domain.routine.model.RoutineDailyExecution
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionResult
import com.luistureo.voicereminderapp.domain.routine.model.RoutineHistory
import com.luistureo.voicereminderapp.domain.routine.model.RoutinePeriod
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTask
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTemplate
import com.luistureo.voicereminderapp.domain.routine.model.RoutineSuggestion
import java.time.LocalDate

interface RoutineRepository {
    suspend fun initializeDefaults(
        routines: List<RoutineDraft>,
        templates: List<RoutineTemplate>
    ): Boolean

    suspend fun saveRoutine(routine: Routine, tasks: List<RoutineTask>): Int
    suspend fun getRoutines(): List<Routine>
    suspend fun getRoutineById(routineId: Int): Routine?
    suspend fun getTasks(routineId: Int): List<RoutineTask>
    suspend fun updateTask(task: RoutineTask)
    suspend fun hasOtherActiveRoutine(period: RoutinePeriod, excludedRoutineId: Int = 0): Boolean
    suspend fun deleteRoutine(routineId: Int)
    suspend fun prepareDay(date: LocalDate)
    suspend fun saveDailyExecution(execution: RoutineDailyExecution)
    suspend fun getDailyExecution(routineId: Int, date: LocalDate): RoutineDailyExecution?

    /**
     * Runs [transform] against one persisted snapshot and commits its result in the same
     * database transaction. The transform must remain deterministic and free of I/O.
     */
    suspend fun updateExecutionAtomically(
        routineId: Int,
        date: LocalDate,
        transform: (
            routine: Routine,
            tasks: List<RoutineTask>,
            execution: RoutineDailyExecution?
        ) -> RoutineExecutionResult?
    ): RoutineExecutionResult?
    suspend fun saveHistory(history: RoutineHistory)
    suspend fun saveTaskProgress(
        task: RoutineTask,
        execution: RoutineDailyExecution,
        history: RoutineHistory?
    )
    suspend fun finalizeDay(
        tasks: List<RoutineTask>,
        execution: RoutineDailyExecution,
        history: RoutineHistory
    )
    suspend fun deleteHistory(routineId: Int, date: LocalDate)
    suspend fun getHistory(routineId: Int): List<RoutineHistory>
    suspend fun getHistoryBetween(startDate: LocalDate, endDate: LocalDate): List<RoutineHistory>
    suspend fun getAllHistory(): List<RoutineHistory>
    suspend fun getTemplates(): List<RoutineTemplate>
    suspend fun saveTemplate(template: RoutineTemplate): Int
    suspend fun deletePersonalTemplate(templateId: Int): Boolean
    suspend fun restoreBuiltInTemplates(templates: List<RoutineTemplate>)
    suspend fun saveSuggestion(suggestion: RoutineSuggestion): Int
    suspend fun getActiveSuggestions(): List<RoutineSuggestion>
    suspend fun getLatestSuggestion(routineId: Int, type: String): RoutineSuggestion?
    suspend fun countSuggestionsForDay(date: LocalDate): Int
    suspend fun dismissSuggestion(id: Int, date: LocalDate)
}
