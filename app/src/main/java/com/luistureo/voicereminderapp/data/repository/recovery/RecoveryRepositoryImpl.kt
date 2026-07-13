package com.luistureo.voicereminderapp.data.repository.recovery

import com.luistureo.voicereminderapp.data.local.dao.recovery.RecoveryDao
import com.luistureo.voicereminderapp.data.mapper.recovery.toDomain
import com.luistureo.voicereminderapp.data.mapper.recovery.toEntity
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

class RecoveryRepositoryImpl(private val dao: RecoveryDao) : RecoveryRepository {
    override suspend fun getGoals() = dao.getVisibleGoals().map { it.toDomain() }
    override suspend fun getGoal(goalId: Int) = dao.getGoalById(goalId)?.toDomain()

    override suspend fun saveGoal(goal: RecoveryGoal): Int {
        return if (goal.id == 0) dao.insertGoal(goal.toEntity()).toInt() else {
            dao.updateGoal(goal.toEntity())
            goal.id
        }
    }

    override suspend fun setGoalStatus(goalId: Int, status: RecoveryGoalStatus) {
        dao.updateGoalStatus(goalId, status.name, System.currentTimeMillis())
    }

    override suspend fun deleteGoal(goal: RecoveryGoal, mode: RecoveryDeletionMode) {
        when (mode) {
            RecoveryDeletionMode.ARCHIVE -> dao.archiveGoal(goal.id, System.currentTimeMillis())
            RecoveryDeletionMode.DELETE_KEEP_ANONYMOUS_HISTORY ->
                dao.deleteGoalKeepingAnonymousHistory(goal.id, goal.historyKey)
            RecoveryDeletionMode.DELETE_ALL -> dao.deleteGoalAndHistory(goal.id, goal.historyKey)
        }
    }

    override suspend fun saveCheckIn(checkIn: RecoveryCheckIn) =
        dao.upsertCheckIn(checkIn.toEntity()).toInt().takeIf { it > 0 } ?: checkIn.id

    override suspend fun getCheckIn(historyKey: String, date: LocalDate) =
        dao.getCheckIn(historyKey, date.toEpochDay())?.toDomain()

    override suspend fun getCheckIns(historyKey: String) =
        dao.getCheckIns(historyKey).map { it.toDomain() }

    override suspend fun getAllCheckIns() = dao.getAllCheckIns().map { it.toDomain() }

    override suspend fun getTriggers(goalId: Int) = dao.getTriggers(goalId).map { it.toDomain() }
    override suspend fun saveTrigger(trigger: RecoveryTrigger): Int = if (trigger.id == 0) {
        dao.insertTrigger(trigger.toEntity()).toInt()
    } else {
        dao.updateTrigger(trigger.toEntity()); trigger.id
    }
    override suspend fun deleteTrigger(trigger: RecoveryTrigger) = dao.deleteTrigger(trigger.toEntity())

    override suspend fun getHelpfulActions(goalId: Int) =
        dao.getHelpfulActions(goalId).map { it.toDomain() }
    override suspend fun saveHelpfulAction(action: RecoveryHelpfulAction): Int = if (action.id == 0) {
        dao.insertHelpfulAction(action.toEntity()).toInt()
    } else {
        dao.updateHelpfulAction(action.toEntity()); action.id
    }
    override suspend fun deleteHelpfulAction(action: RecoveryHelpfulAction) =
        dao.deleteHelpfulAction(action.toEntity())

    override suspend fun getSupportContacts(goalId: Int) =
        dao.getSupportContacts(goalId).map { it.toDomain() }
    override suspend fun saveSupportContact(contact: RecoverySupportContact): Int = if (contact.id == 0) {
        dao.insertSupportContact(contact.toEntity()).toInt()
    } else {
        dao.updateSupportContact(contact.toEntity()); contact.id
    }
    override suspend fun deleteSupportContact(contact: RecoverySupportContact) =
        dao.deleteSupportContact(contact.toEntity())

    override suspend fun getMilestones(goalId: Int) = dao.getMilestones(goalId).map { it.toDomain() }
    override suspend fun saveMilestone(milestone: RecoveryMilestone) =
        dao.upsertMilestone(milestone.toEntity()).toInt().takeIf { it > 0 } ?: milestone.id
    override suspend fun markMilestoneReached(milestoneId: Int, achievedAtEpochMillis: Long) =
        dao.markMilestoneReached(milestoneId, achievedAtEpochMillis)

    override suspend fun getReminders(goalId: Int) = dao.getReminders(goalId).map { it.toDomain() }
    override suspend fun getEnabledReminders() = dao.getEnabledReminders().map { it.toDomain() }
    override suspend fun saveReminder(reminder: RecoveryReminder) =
        dao.upsertReminder(reminder.toEntity()).toInt().takeIf { it > 0 } ?: reminder.id
    override suspend fun deleteReminder(reminderId: Int) = dao.deleteReminder(reminderId)
}
