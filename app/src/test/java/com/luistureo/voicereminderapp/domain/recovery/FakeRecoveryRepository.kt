package com.luistureo.voicereminderapp.domain.recovery

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
import java.time.LocalDate

class FakeRecoveryRepository : RecoveryRepository {
    val goals = mutableListOf<RecoveryGoal>()
    val checkIns = mutableListOf<RecoveryCheckIn>()
    val triggers = mutableListOf<RecoveryTrigger>()
    val helpfulActions = mutableListOf<RecoveryHelpfulAction>()
    val contacts = mutableListOf<RecoverySupportContact>()
    val milestones = mutableListOf<RecoveryMilestone>()
    val reminders = mutableListOf<RecoveryReminder>()
    val deletionModes = mutableListOf<RecoveryDeletionMode>()

    override suspend fun getGoals() = goals.filter { it.status != RecoveryGoalStatus.ARCHIVED }
    override suspend fun getGoal(goalId: Int) = goals.firstOrNull { it.id == goalId }

    override suspend fun saveGoal(goal: RecoveryGoal): Int {
        val id = goal.id.takeIf { it > 0 } ?: ((goals.maxOfOrNull { it.id } ?: 0) + 1)
        goals.removeAll { it.id == id }
        goals += goal.copy(id = id)
        return id
    }

    override suspend fun setGoalStatus(goalId: Int, status: RecoveryGoalStatus) {
        val current = getGoal(goalId) ?: return
        saveGoal(current.copy(status = status))
    }

    override suspend fun deleteGoal(goal: RecoveryGoal, mode: RecoveryDeletionMode) {
        deletionModes += mode
        when (mode) {
            RecoveryDeletionMode.ARCHIVE -> setGoalStatus(goal.id, RecoveryGoalStatus.ARCHIVED)
            RecoveryDeletionMode.DELETE_KEEP_ANONYMOUS_HISTORY -> {
                goals.removeAll { it.id == goal.id }
                checkIns.replaceAll {
                    if (it.goalHistoryKey == goal.historyKey) it.copy(
                        goalId = null,
                        cravingIntensity = null,
                        trigger = null,
                        helpfulAction = null,
                        note = null
                    ) else it
                }
                removeGoalChildren(goal.id)
            }
            RecoveryDeletionMode.DELETE_ALL -> {
                goals.removeAll { it.id == goal.id }
                checkIns.removeAll { it.goalHistoryKey == goal.historyKey }
                removeGoalChildren(goal.id)
            }
        }
    }

    override suspend fun saveCheckIn(checkIn: RecoveryCheckIn): Int {
        val existing = checkIns.firstOrNull {
            it.goalHistoryKey == checkIn.goalHistoryKey && it.date == checkIn.date
        }
        val id = checkIn.id.takeIf { it > 0 } ?: existing?.id
            ?: ((checkIns.maxOfOrNull { it.id } ?: 0) + 1)
        checkIns.removeAll { it.goalHistoryKey == checkIn.goalHistoryKey && it.date == checkIn.date }
        checkIns += checkIn.copy(id = id)
        return id
    }

    override suspend fun getCheckIn(historyKey: String, date: LocalDate) = checkIns.firstOrNull {
        it.goalHistoryKey == historyKey && it.date == date
    }
    override suspend fun getCheckIns(historyKey: String) = checkIns
        .filter { it.goalHistoryKey == historyKey }.sortedByDescending { it.date }
    override suspend fun getAllCheckIns() = checkIns.sortedByDescending { it.date }

    override suspend fun getTriggers(goalId: Int) = triggers.filter { it.goalId == goalId }
    override suspend fun saveTrigger(trigger: RecoveryTrigger): Int {
        val id = trigger.id.takeIf { it > 0 } ?: ((triggers.maxOfOrNull { it.id } ?: 0) + 1)
        triggers.removeAll { it.id == id }; triggers += trigger.copy(id = id); return id
    }
    override suspend fun deleteTrigger(trigger: RecoveryTrigger) { triggers.removeAll { it.id == trigger.id } }

    override suspend fun getHelpfulActions(goalId: Int) = helpfulActions.filter { it.goalId == goalId }
    override suspend fun saveHelpfulAction(action: RecoveryHelpfulAction): Int {
        val id = action.id.takeIf { it > 0 } ?: ((helpfulActions.maxOfOrNull { it.id } ?: 0) + 1)
        helpfulActions.removeAll { it.id == id }; helpfulActions += action.copy(id = id); return id
    }
    override suspend fun deleteHelpfulAction(action: RecoveryHelpfulAction) {
        helpfulActions.removeAll { it.id == action.id }
    }

    override suspend fun getSupportContacts(goalId: Int) = contacts.filter { it.goalId == goalId }
    override suspend fun saveSupportContact(contact: RecoverySupportContact): Int {
        val id = contact.id.takeIf { it > 0 } ?: ((contacts.maxOfOrNull { it.id } ?: 0) + 1)
        contacts.removeAll { it.id == id }; contacts += contact.copy(id = id); return id
    }
    override suspend fun deleteSupportContact(contact: RecoverySupportContact) {
        contacts.removeAll { it.id == contact.id }
    }

    override suspend fun getMilestones(goalId: Int) = milestones.filter { it.goalId == goalId }
    override suspend fun saveMilestone(milestone: RecoveryMilestone): Int {
        val id = milestone.id.takeIf { it > 0 } ?: ((milestones.maxOfOrNull { it.id } ?: 0) + 1)
        milestones.removeAll { it.id == id }; milestones += milestone.copy(id = id); return id
    }
    override suspend fun markMilestoneReached(milestoneId: Int, achievedAtEpochMillis: Long) {
        val index = milestones.indexOfFirst { it.id == milestoneId }
        if (index >= 0) milestones[index] = milestones[index].copy(achievedAtEpochMillis = achievedAtEpochMillis)
    }

    override suspend fun getReminders(goalId: Int) = reminders.filter { it.goalId == goalId }
    override suspend fun getEnabledReminders() = reminders.filter { it.enabled }
    override suspend fun saveReminder(reminder: RecoveryReminder): Int {
        val existing = reminders.firstOrNull { it.goalId == reminder.goalId && it.type == reminder.type }
        val id = reminder.id.takeIf { it > 0 } ?: existing?.id
            ?: ((reminders.maxOfOrNull { it.id } ?: 0) + 1)
        reminders.removeAll { it.goalId == reminder.goalId && it.type == reminder.type }
        reminders += reminder.copy(id = id)
        return id
    }
    override suspend fun deleteReminder(reminderId: Int) { reminders.removeAll { it.id == reminderId } }

    private fun removeGoalChildren(goalId: Int) {
        triggers.removeAll { it.goalId == goalId }
        helpfulActions.removeAll { it.goalId == goalId }
        contacts.removeAll { it.goalId == goalId }
        milestones.removeAll { it.goalId == goalId }
        reminders.removeAll { it.goalId == goalId }
    }
}
