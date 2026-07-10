package com.luistureo.voicereminderapp.presentation.routine.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luistureo.voicereminderapp.core.notification.NotificationHelper
import com.luistureo.voicereminderapp.core.routine.RoutineMotivationCoordinator
import com.luistureo.voicereminderapp.core.routine.RoutineScheduleCoordinator
import com.luistureo.voicereminderapp.core.routine.RoutineScheduler
import com.luistureo.voicereminderapp.domain.routine.model.Routine
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionAction
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionResult
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionState
import com.luistureo.voicereminderapp.domain.routine.model.RoutinePeriod
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTask
import com.luistureo.voicereminderapp.domain.routine.repository.RoutineRepository
import com.luistureo.voicereminderapp.domain.routine.usecase.ApplyRoutineExecutionActionUseCase
import com.luistureo.voicereminderapp.domain.routine.usecase.InitializeDailyRoutinesUseCase
import com.luistureo.voicereminderapp.domain.routine.usecase.PrepareRoutineDayUseCase
import com.luistureo.voicereminderapp.domain.routine.usecase.SaveRoutineUseCase
import com.luistureo.voicereminderapp.domain.routine.usecase.UpdateRoutineTaskProgressUseCase
import com.luistureo.voicereminderapp.domain.routine.factory.DefaultRoutineTemplateFactory
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTemplate
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTemplateTask
import com.luistureo.voicereminderapp.presentation.routine.state.RoutinePresentationPolicy
import com.luistureo.voicereminderapp.presentation.routine.state.RoutineUiState
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RoutineViewModel(
    private val repository: RoutineRepository,
    private val initializeDailyRoutines: InitializeDailyRoutinesUseCase,
    private val prepareRoutineDay: PrepareRoutineDayUseCase,
    private val saveRoutineUseCase: SaveRoutineUseCase,
    private val applyExecutionAction: ApplyRoutineExecutionActionUseCase,
    private val updateTaskProgress: UpdateRoutineTaskProgressUseCase,
    private val scheduler: RoutineScheduler,
    private val notifications: NotificationHelper,
    private val scheduleCoordinator: RoutineScheduleCoordinator,
    private val motivationCoordinator: RoutineMotivationCoordinator,
    private val todayProvider: () -> LocalDate = LocalDate::now
) : ViewModel() {
    private val _uiState = MutableStateFlow(RoutineUiState())
    val uiState: StateFlow<RoutineUiState> = _uiState.asStateFlow()
    private var routineLoadRequestId = 0L

    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            runCatching {
                initializeDailyRoutines()
                val today = todayProvider()
                prepareRoutineDay(today)
                val items = repository.getRoutines()
                    .sortedBy { routine ->
                        when (routine.period) {
                            RoutinePeriod.MORNING -> 0
                            RoutinePeriod.AFTERNOON -> 1
                            RoutinePeriod.NIGHT -> 2
                        }
                    }
                scheduleCoordinator.syncAll()
                items
                    .map { routine ->
                        RoutinePresentationPolicy.dashboardItem(
                            routine,
                            repository.getTasks(routine.id),
                            today
                        )
                    }
            }.onSuccess { items ->
                _uiState.update { it.copy(dashboardItems = items, isLoading = false) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isLoading = false, message = error.message ?: "No fue posible cargar las rutinas.")
                }
            }
        }
    }

    fun loadRoutine(routineId: Int) {
        val requestId = ++routineLoadRequestId
        viewModelScope.launch {
            _uiState.update { state ->
                val changingRoutine = state.selectedRoutine?.id?.let { it != routineId } == true
                state.copy(
                    selectedRoutine = if (changingRoutine) null else state.selectedRoutine,
                    tasks = if (changingRoutine) emptyList() else state.tasks,
                    isLoading = true,
                    message = null
                )
            }
            val result = runCatching {
                val today = todayProvider()
                prepareRoutineDay(today)
                repository.getRoutineById(routineId) to repository.getTasks(routineId)
            }
            if (requestId != routineLoadRequestId) return@launch
            result.onSuccess { (routine, tasks) ->
                _uiState.update {
                    it.copy(
                        selectedRoutine = routine,
                        tasks = tasks,
                        isLoading = false,
                        message = if (routine == null) "No se encontró la rutina." else null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isLoading = false, message = error.message ?: "No fue posible cargar la rutina.")
                }
            }
        }
    }

    fun toggleTask(task: RoutineTask, completed: Boolean) {
        viewModelScope.launch {
            val today = todayProvider()
            runCatching { updateTaskProgress(task, completed, today) }
                .onSuccess { result ->
                    if (result == null) {
                        _uiState.update { it.copy(message = "No fue posible actualizar la actividad.") }
                        return@onSuccess
                    }
                    syncAfterResult(result, today)
                    val completedAll = result.execution.state == RoutineExecutionState.COMPLETED
                _uiState.update {
                    it.copy(
                            selectedRoutine = result.routine,
                            tasks = result.tasks,
                        message = if (completedAll) "¡Rutina completada!" else null,
                        showCompletionFeedback = completedAll
                    )
                }
            }
                .onFailure { error ->
                    _uiState.update { it.copy(message = error.message ?: "No fue posible actualizar la actividad.") }
                }
        }
    }

    fun completeSelectedRoutine() {
        val routine = _uiState.value.selectedRoutine ?: return
        viewModelScope.launch {
            val today = todayProvider()
            runCatching {
                applyExecutionAction(routine.id, today, RoutineExecutionAction.COMPLETE)
            }.onSuccess { result ->
                if (result != null) {
                    syncAfterResult(result, today)
                    _uiState.update {
                        it.copy(
                            selectedRoutine = result.routine,
                            tasks = result.tasks,
                            message = "¡Rutina completada!",
                            showCompletionFeedback = true
                        )
                    }
                }
                }.onFailure { error ->
                    _uiState.update { it.copy(message = error.message ?: "No fue posible completar la rutina.") }
                }
        }
    }

    fun markSelectedRoutinePartial() {
        val routine = _uiState.value.selectedRoutine ?: return
        viewModelScope.launch {
            val today = todayProvider()
            runCatching {
                applyExecutionAction(routine.id, today, RoutineExecutionAction.PARTIAL)
            }.onSuccess { result ->
                if (result != null) {
                    syncAfterResult(result, today)
                    _uiState.update {
                        it.copy(
                            selectedRoutine = result.routine,
                            tasks = result.tasks,
                            message = "Avance parcial guardado."
                        )
                    }
                }
            }.onFailure { error ->
                _uiState.update { it.copy(message = error.message ?: "No fue posible guardar el avance.") }
            }
        }
    }

    fun saveRoutine(routine: Routine, tasks: List<RoutineTask>, onSaved: (Int) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val routineId = saveRoutineUseCase(routine, tasks)
                notifications.cancelRoutineNotifications(routineId)
                val savedRoutine = repository.getRoutineById(routineId)
                    ?: return@runCatching routineId
                val state = repository.getDailyExecution(routineId, todayProvider())?.state
                    ?: RoutineExecutionState.PENDING
                scheduler.replaceRoutineSchedule(savedRoutine, state)
                routineId
            }.onSuccess { routineId -> onSaved(routineId) }
                .onFailure { error ->
                    _uiState.update { it.copy(message = error.message ?: "No fue posible guardar la rutina.") }
                }
        }
    }

    fun loadTemplates() {
        viewModelScope.launch {
            runCatching {
                initializeDailyRoutines()
                var templates = repository.getTemplates()
                if (templates.count { it.builtIn } < 12) {
                    repository.restoreBuiltInTemplates(DefaultRoutineTemplateFactory.create())
                    templates = repository.getTemplates()
                }
                templates
            }
                .onSuccess { templates -> _uiState.update { it.copy(templates = templates) } }
                .onFailure { error ->
                    _uiState.update { it.copy(message = error.message ?: "No fue posible cargar las plantillas.") }
                }
        }
    }

    fun savePersonalTemplate(
        routine: Routine,
        tasks: List<RoutineTask>,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch {
            runCatching {
                repository.saveTemplate(
                    RoutineTemplate(
                        name = routine.name,
                        description = routine.description,
                        benefitsExplanation = "Plantilla personal editable. Adáptala según tus necesidades.",
                        period = routine.period,
                        estimatedTotalDurationMinutes = tasks.sumOf {
                            it.estimatedDurationMinutes ?: 0
                        },
                        icon = routine.icon,
                        color = routine.color,
                        category = routine.category,
                        editable = true,
                        builtIn = false,
                        suggestedTasks = tasks.mapIndexed { index, task ->
                            RoutineTemplateTask(
                                title = task.title,
                                description = task.description,
                                orderPriority = index,
                                estimatedDurationMinutes = task.estimatedDurationMinutes
                            )
                        }
                    )
                )
            }.onSuccess { onSaved() }
                .onFailure { error ->
                    _uiState.update { it.copy(message = error.message ?: "No fue posible guardar la plantilla.") }
                }
        }
    }

    fun deletePersonalTemplate(templateId: Int) {
        viewModelScope.launch {
            repository.deletePersonalTemplate(templateId)
            loadTemplates()
        }
    }

    fun restoreBuiltInTemplates() {
        viewModelScope.launch {
            repository.restoreBuiltInTemplates(DefaultRoutineTemplateFactory.create())
            loadTemplates()
        }
    }

    fun loadTemplate(templateId: Int) {
        viewModelScope.launch {
            runCatching {
                initializeDailyRoutines()
                repository.getTemplates().firstOrNull { it.id == templateId }
            }.onSuccess { template ->
                _uiState.update {
                    it.copy(
                        selectedTemplate = template,
                        message = if (template == null) "No se encontró la plantilla." else null
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(message = error.message ?: "No fue posible cargar la plantilla.") }
            }
        }
    }

    fun selectTemplate(templateId: Int) {
        val template = _uiState.value.templates.firstOrNull { it.id == templateId }
        _uiState.update { it.copy(selectedTemplate = template) }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
    fun consumeCompletionFeedback() = _uiState.update { it.copy(showCompletionFeedback = false) }

    private fun syncAfterResult(result: RoutineExecutionResult, date: LocalDate) {
        if (result.history != null) {
            scheduler.cancelRoutine(result.routine.id)
            notifications.cancelRoutineNotifications(result.routine.id)
        }
        scheduler.syncRoutine(result.routine, result.execution.state)
        motivationCoordinator.deliver(result.routine, result.execution.state, date)
    }
}
