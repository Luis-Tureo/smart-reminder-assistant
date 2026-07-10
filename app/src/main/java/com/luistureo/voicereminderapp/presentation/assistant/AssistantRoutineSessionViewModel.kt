package com.luistureo.voicereminderapp.presentation.assistant

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.luistureo.voicereminderapp.core.notification.NotificationHelper
import com.luistureo.voicereminderapp.core.routine.RoutineAssistantEvent
import com.luistureo.voicereminderapp.core.routine.RoutineAssistantEventHooks
import com.luistureo.voicereminderapp.core.routine.RoutineScheduler
import com.luistureo.voicereminderapp.data.local.database.ReminderDatabase
import com.luistureo.voicereminderapp.data.repository.RoutineRepositoryImpl
import com.luistureo.voicereminderapp.domain.routine.model.Routine
import com.luistureo.voicereminderapp.domain.routine.model.RoutineAssistantMode
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionAction
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionResult
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionState
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTask
import com.luistureo.voicereminderapp.domain.routine.model.ordered
import com.luistureo.voicereminderapp.domain.routine.repository.RoutineRepository
import com.luistureo.voicereminderapp.domain.routine.usecase.ApplyRoutineExecutionActionUseCase
import com.luistureo.voicereminderapp.domain.routine.usecase.UpdateRoutineTaskProgressUseCase
import java.time.LocalDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface AssistantRoutineSessionEvent {
    data class Started(
        val routine: Routine,
        val nextTask: RoutineTask?,
        val completedTasks: Int,
        val totalTasks: Int
    ) : AssistantRoutineSessionEvent
    data class TaskAdvanced(
        val routine: Routine,
        val nextTask: RoutineTask?,
        val completedTasks: Int,
        val totalTasks: Int
    ) : AssistantRoutineSessionEvent
    data class Finished(
        val routine: Routine,
        val state: RoutineExecutionState,
        val completedTasks: Int,
        val totalTasks: Int
    ) : AssistantRoutineSessionEvent
    data class Error(val message: String) : AssistantRoutineSessionEvent
}

class AssistantRoutineSessionViewModel(
    private val repository: RoutineRepository,
    private val applyAction: ApplyRoutineExecutionActionUseCase,
    private val updateTask: UpdateRoutineTaskProgressUseCase,
    private val scheduler: RoutineScheduler,
    private val notifications: NotificationHelper,
    private val todayProvider: () -> LocalDate = LocalDate::now
) : ViewModel() {
    private val _events = Channel<AssistantRoutineSessionEvent>(Channel.BUFFERED)
    val events: Flow<AssistantRoutineSessionEvent> = _events.receiveAsFlow()

    private var routine: Routine? = null
    private var tasks: List<RoutineTask> = emptyList()
    private var sessionDate: LocalDate? = null
    private var operationInProgress = false

    fun start(routineId: Int, dateEpochDay: Long, notificationId: Int) {
        if (operationInProgress || routine != null) return
        val today = todayProvider()
        if (dateEpochDay != today.toEpochDay()) {
            if (notificationId != 0) notifications.cancelNotification(notificationId)
            _events.trySend(AssistantRoutineSessionEvent.Error("Este aviso ya no está vigente."))
            return
        }
        operationInProgress = true
        viewModelScope.launch {
            runCatching {
                repository.prepareDay(today)
                val storedRoutine = repository.getRoutineById(routineId)
                    ?: error("No se encontró la rutina.")
                if (!storedRoutine.enabled) error("La rutina está desactivada.")
                if (storedRoutine.assistantMode == RoutineAssistantMode.SIMPLE_DISPLAY) {
                    error("Esta rutina usa el modo de notificación simple.")
                }
                val result = applyAction(
                    routineId,
                    today,
                    RoutineExecutionAction.START,
                    storedRoutine.assistantMode
                ) ?: error("No fue posible iniciar la rutina.")
                if (notificationId != 0) notifications.cancelNotification(notificationId)
                updateSession(result, today)
                result
            }.onSuccess { result ->
                operationInProgress = false
                if (isTerminal(result.execution.state)) {
                    emitFinished(result)
                } else {
                    _events.send(
                        AssistantRoutineSessionEvent.Started(
                            result.routine,
                            nextTask(result.tasks, today),
                            completedCount(result.tasks, today),
                            result.tasks.size
                        )
                    )
                }
            }.onFailure { error ->
                operationInProgress = false
                _events.send(
                    AssistantRoutineSessionEvent.Error(
                        error.message ?: "No fue posible iniciar la guía."
                    )
                )
            }
        }
    }

    fun confirmCurrentTask() {
        val currentRoutine = routine ?: return
        val date = sessionDate ?: return
        val currentTask = nextTask(tasks, date) ?: return
        if (operationInProgress) return
        operationInProgress = true
        viewModelScope.launch {
            runCatching {
                updateTask(
                    currentTask,
                    true,
                    date,
                    currentRoutine.assistantMode
                ) ?: error("No fue posible confirmar la actividad.")
            }.onSuccess { result ->
                operationInProgress = false
                updateSession(result, date)
                if (result.execution.state == RoutineExecutionState.COMPLETED) {
                    emitFinished(result)
                } else {
                    _events.send(
                        AssistantRoutineSessionEvent.TaskAdvanced(
                            result.routine,
                            nextTask(result.tasks, date),
                            completedCount(result.tasks, date),
                            result.tasks.size
                        )
                    )
                }
            }.onFailure { error ->
                operationInProgress = false
                _events.send(
                    AssistantRoutineSessionEvent.Error(
                        error.message ?: "No fue posible confirmar la actividad."
                    )
                )
            }
        }
    }

    fun finishPartial() {
        val currentRoutine = routine ?: return
        val date = sessionDate ?: return
        if (operationInProgress) return
        operationInProgress = true
        viewModelScope.launch {
            runCatching {
                applyAction(
                    currentRoutine.id,
                    date,
                    RoutineExecutionAction.PARTIAL,
                    currentRoutine.assistantMode
                ) ?: error("No fue posible guardar el avance.")
            }.onSuccess { result ->
                operationInProgress = false
                updateSession(result, date)
                emitFinished(result)
            }.onFailure { error ->
                operationInProgress = false
                _events.send(
                    AssistantRoutineSessionEvent.Error(
                        error.message ?: "No fue posible guardar el avance."
                    )
                )
            }
        }
    }

    private fun updateSession(result: RoutineExecutionResult, date: LocalDate) {
        routine = result.routine
        tasks = result.tasks.ordered()
        sessionDate = date
        if (result.history != null) {
            scheduler.cancelRoutine(result.routine.id)
            notifications.cancelRoutineNotifications(result.routine.id)
        }
        scheduler.syncRoutine(result.routine, result.execution.state)
        RoutineAssistantEventHooks.emit(
            RoutineAssistantEvent(
                routineId = result.routine.id,
                assistantMode = result.routine.assistantMode,
                state = result.execution.state,
                occurredAtEpochMillis = System.currentTimeMillis()
            )
        )
    }

    private suspend fun emitFinished(result: RoutineExecutionResult) {
        val date = sessionDate ?: todayProvider()
        val completed = result.tasks.count { it.completed && it.completedOn == date }
                _events.send(
            AssistantRoutineSessionEvent.Finished(
                result.routine,
                result.execution.state,
                completed,
                result.tasks.size
            )
        )
    }

    private fun nextTask(tasks: List<RoutineTask>, date: LocalDate): RoutineTask? =
        tasks.ordered().firstOrNull { !it.completed || it.completedOn != date }

    private fun completedCount(tasks: List<RoutineTask>, date: LocalDate): Int =
        tasks.count { it.completed && it.completedOn == date }

    private fun isTerminal(state: RoutineExecutionState): Boolean = state in setOf(
        RoutineExecutionState.COMPLETED,
        RoutineExecutionState.PARTIALLY_COMPLETED,
        RoutineExecutionState.SKIPPED,
        RoutineExecutionState.NOT_COMPLETED
    )
}

class AssistantRoutineSessionViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repository = RoutineRepositoryImpl(
            ReminderDatabase.getDatabase(appContext).routineDao()
        )
        return AssistantRoutineSessionViewModel(
            repository = repository,
            applyAction = ApplyRoutineExecutionActionUseCase(repository),
            updateTask = UpdateRoutineTaskProgressUseCase(repository),
            scheduler = RoutineScheduler(appContext),
            notifications = NotificationHelper(appContext)
        ) as T
    }
}
