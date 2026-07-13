package com.luistureo.voicereminderapp.presentation.recovery

import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryDashboard
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryGoal
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryHelpfulAction
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryMilestone
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryReminder
import com.luistureo.voicereminderapp.domain.recovery.model.RecoverySupportContact
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryTrigger

data class RecoveryUiState(
    val loading: Boolean = false,
    val goals: List<RecoveryGoal> = emptyList(),
    val selectedGoal: RecoveryGoal? = null,
    val dashboard: RecoveryDashboard? = null,
    val triggers: List<RecoveryTrigger> = emptyList(),
    val helpfulActions: List<RecoveryHelpfulAction> = emptyList(),
    val contacts: List<RecoverySupportContact> = emptyList(),
    val milestones: List<RecoveryMilestone> = emptyList(),
    val reminders: List<RecoveryReminder> = emptyList(),
    val messageRes: Int? = null
)
