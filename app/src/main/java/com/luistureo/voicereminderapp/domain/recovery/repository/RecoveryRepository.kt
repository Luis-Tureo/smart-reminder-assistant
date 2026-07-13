package com.luistureo.voicereminderapp.domain.recovery.repository

import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryCheckIn
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryDeletionMode
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryGoal
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryGoalStatus
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryHelpfulAction
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryMilestone
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryReminder
import com.luistureo.voicereminderapp.domain.recovery.model.RecoverySupportContact
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryTrigger
import java.time.LocalDate

interface RecoveryRepository {
    suspend fun getGoals(): List<RecoveryGoal>
    suspend fun getGoal(goalId: Int): RecoveryGoal?
    suspend fun saveGoal(goal: RecoveryGoal): Int
    suspend fun setGoalStatus(goalId: Int, status: RecoveryGoalStatus)
    suspend fun deleteGoal(goal: RecoveryGoal, mode: RecoveryDeletionMode)

    suspend fun saveCheckIn(checkIn: RecoveryCheckIn): Int
    suspend fun getCheckIn(historyKey: String, date: LocalDate): RecoveryCheckIn?
    suspend fun getCheckIns(historyKey: String): List<RecoveryCheckIn>
    suspend fun getAllCheckIns(): List<RecoveryCheckIn>

    suspend fun getTriggers(goalId: Int): List<RecoveryTrigger>
    suspend fun saveTrigger(trigger: RecoveryTrigger): Int
    suspend fun deleteTrigger(trigger: RecoveryTrigger)

    suspend fun getHelpfulActions(goalId: Int): List<RecoveryHelpfulAction>
    suspend fun saveHelpfulAction(action: RecoveryHelpfulAction): Int
    suspend fun deleteHelpfulAction(action: RecoveryHelpfulAction)

    suspend fun getSupportContacts(goalId: Int): List<RecoverySupportContact>
    suspend fun saveSupportContact(contact: RecoverySupportContact): Int
    suspend fun deleteSupportContact(contact: RecoverySupportContact)

    suspend fun getMilestones(goalId: Int): List<RecoveryMilestone>
    suspend fun saveMilestone(milestone: RecoveryMilestone): Int
    suspend fun markMilestoneReached(milestoneId: Int, achievedAtEpochMillis: Long)

    suspend fun getReminders(goalId: Int): List<RecoveryReminder>
    suspend fun getEnabledReminders(): List<RecoveryReminder>
    suspend fun saveReminder(reminder: RecoveryReminder): Int
    suspend fun deleteReminder(reminderId: Int)
}
