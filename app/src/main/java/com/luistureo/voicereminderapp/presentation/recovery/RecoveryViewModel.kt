package com.luistureo.voicereminderapp.presentation.recovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.recovery.RecoveryPreferenceStore
import com.luistureo.voicereminderapp.core.recovery.RecoveryNotificationHelper
import com.luistureo.voicereminderapp.core.recovery.RecoveryScheduler
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryCheckIn
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryDeletionMode
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryGoal
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryGoalStatus
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryHelpfulAction
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryMilestone
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryReminder
import com.luistureo.voicereminderapp.domain.recovery.model.RecoverySupportContact
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryTrigger
import com.luistureo.voicereminderapp.domain.recovery.repository.RecoveryRepository
import com.luistureo.voicereminderapp.domain.recovery.usecase.DeleteRecoveryGoalUseCase
import com.luistureo.voicereminderapp.domain.recovery.usecase.GetRecoveryDashboardUseCase
import com.luistureo.voicereminderapp.domain.recovery.usecase.RecordRecoveryCheckInUseCase
import com.luistureo.voicereminderapp.domain.recovery.usecase.SaveRecoveryGoalUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RecoveryViewModel(
    private val repository: RecoveryRepository,
    private val preferences: RecoveryPreferenceStore,
    private val scheduler: RecoveryScheduler,
    private val notificationHelper: RecoveryNotificationHelper,
    private val saveGoalUseCase: SaveRecoveryGoalUseCase = SaveRecoveryGoalUseCase(repository),
    private val recordCheckInUseCase: RecordRecoveryCheckInUseCase = RecordRecoveryCheckInUseCase(repository),
    private val dashboardUseCase: GetRecoveryDashboardUseCase = GetRecoveryDashboardUseCase(repository),
    private val deleteGoalUseCase: DeleteRecoveryGoalUseCase = DeleteRecoveryGoalUseCase(repository)
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecoveryUiState())
    val uiState: StateFlow<RecoveryUiState> = _uiState.asStateFlow()

    fun load(requestedGoalId: Int = 0) = viewModelScope.launch {
        _uiState.update { it.copy(loading = true) }
        runCatching {
            val goals = repository.getGoals()
            val selectedId = requestedGoalId.takeIf { it > 0 }
                ?: preferences.selectedGoalId().takeIf { it > 0 }
            val selected = goals.firstOrNull { it.id == selectedId }
                ?: goals.firstOrNull { it.status == RecoveryGoalStatus.ACTIVE }
                ?: goals.firstOrNull()
            if (selected != null) preferences.setSelectedGoalId(selected.id)
            val dashboard = selected?.let { dashboardUseCase(it) }
            RecoveryUiState(
                loading = false,
                goals = goals,
                selectedGoal = selected,
                dashboard = dashboard,
                triggers = selected?.let { repository.getTriggers(it.id) }.orEmpty(),
                helpfulActions = selected?.let { repository.getHelpfulActions(it.id) }.orEmpty(),
                contacts = selected?.let { repository.getSupportContacts(it.id) }.orEmpty(),
                milestones = selected?.let { repository.getMilestones(it.id) }.orEmpty(),
                reminders = selected?.let { repository.getReminders(it.id) }.orEmpty()
            )
        }.onSuccess { state -> _uiState.value = state }
            .onFailure { _uiState.update { it.copy(loading = false, messageRes = R.string.recovery_generic_error) } }
    }

    fun selectGoal(goalId: Int) {
        preferences.setSelectedGoalId(goalId)
        load(goalId)
    }

    fun saveGoal(goal: RecoveryGoal, onSaved: (Int) -> Unit = {}) = viewModelScope.launch {
        runCatching { saveGoalUseCase(goal) }
            .onSuccess { id -> preferences.setSelectedGoalId(id); onSaved(id); load(id) }
            .onFailure { _uiState.update { it.copy(messageRes = R.string.recovery_generic_error) } }
    }

    fun saveCheckIn(checkIn: RecoveryCheckIn, onSaved: () -> Unit = {}) = viewModelScope.launch {
        val startedAt = System.currentTimeMillis()
        runCatching {
            recordCheckInUseCase(checkIn)
            val goalId = requireNotNull(checkIn.goalId)
            val reachedMilestone = repository.getMilestones(goalId).any {
                it.achievedAtEpochMillis != null && it.achievedAtEpochMillis >= startedAt
            }
            val reminder = repository.getReminders(goalId).firstOrNull {
                it.type == com.luistureo.voicereminderapp.domain.recovery.model.RecoveryReminderType.MILESTONE &&
                    it.enabled
            }
            if (reachedMilestone && reminder != null && preferences.milestonesEnabled()) {
                notificationHelper.show(reminder)
            }
        }
            .onSuccess { onSaved(); load(checkIn.goalId ?: 0) }
            .onFailure { _uiState.update { it.copy(messageRes = R.string.recovery_generic_error) } }
    }

    fun saveTrigger(trigger: RecoveryTrigger) = viewModelScope.launch {
        repository.saveTrigger(trigger)
        load(trigger.goalId)
    }

    fun deleteTrigger(trigger: RecoveryTrigger) = viewModelScope.launch {
        repository.deleteTrigger(trigger)
        load(trigger.goalId)
    }

    fun saveHelpfulAction(action: RecoveryHelpfulAction) = viewModelScope.launch {
        repository.saveHelpfulAction(action)
        load(action.goalId)
    }

    fun deleteHelpfulAction(action: RecoveryHelpfulAction) = viewModelScope.launch {
        repository.deleteHelpfulAction(action)
        load(action.goalId)
    }

    fun saveContact(contact: RecoverySupportContact) = viewModelScope.launch {
        repository.saveSupportContact(contact)
        load(contact.goalId)
    }

    fun deleteContact(contact: RecoverySupportContact) = viewModelScope.launch {
        repository.deleteSupportContact(contact)
        load(contact.goalId)
    }

    fun saveReminder(reminder: RecoveryReminder) = viewModelScope.launch {
        val id = repository.saveReminder(reminder)
        val saved = reminder.copy(id = id)
        if (saved.enabled) scheduler.scheduleNext(saved) else scheduler.cancel(id)
        load(reminder.goalId)
    }

    fun saveMilestone(milestone: RecoveryMilestone) = viewModelScope.launch {
        repository.saveMilestone(milestone)
        load(milestone.goalId)
    }

    fun saveRecoverySettings(
        reminder: RecoveryReminder,
        paused: Boolean,
        onSaved: () -> Unit = {}
    ) = viewModelScope.launch {
        runCatching {
            val savedId = repository.saveReminder(reminder)
            repository.setGoalStatus(
                reminder.goalId,
                if (paused) RecoveryGoalStatus.PAUSED else RecoveryGoalStatus.ACTIVE
            )
            val reminders = repository.getReminders(reminder.goalId).map {
                if (it.id == savedId) reminder.copy(id = savedId) else it
            }
            if (paused) {
                scheduler.cancelGoal(reminders)
            } else {
                reminders.forEach { item ->
                    if (item.enabled) scheduler.scheduleNext(item) else scheduler.cancel(item.id)
                }
            }
        }.onSuccess { onSaved() }
            .onFailure { _uiState.update { it.copy(messageRes = R.string.recovery_generic_error) } }
    }

    fun setPaused(paused: Boolean) = viewModelScope.launch {
        val goal = _uiState.value.selectedGoal ?: return@launch
        repository.setGoalStatus(
            goal.id,
            if (paused) RecoveryGoalStatus.PAUSED else RecoveryGoalStatus.ACTIVE
        )
        val reminders = repository.getReminders(goal.id)
        if (paused) scheduler.cancelGoal(reminders) else reminders.filter { it.enabled }.forEach {
            scheduler.scheduleNext(it)
        }
        load(goal.id)
    }

    fun deleteGoal(mode: RecoveryDeletionMode, onFinished: () -> Unit = {}) = viewModelScope.launch {
        val goal = _uiState.value.selectedGoal ?: return@launch
        val reminders = repository.getReminders(goal.id)
        scheduler.cancelGoal(reminders)
        deleteGoalUseCase(goal, mode)
        preferences.setSelectedGoalId(0)
        onFinished()
        load()
    }

    fun consumeMessage() = _uiState.update { it.copy(messageRes = null) }
}
