package com.luistureo.voicereminderapp.data.local.dao.recovery

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.luistureo.voicereminderapp.data.local.entity.recovery.RecoveryCheckInEntity
import com.luistureo.voicereminderapp.data.local.entity.recovery.RecoveryGoalEntity
import com.luistureo.voicereminderapp.data.local.entity.recovery.RecoveryHelpfulActionEntity
import com.luistureo.voicereminderapp.data.local.entity.recovery.RecoveryMilestoneEntity
import com.luistureo.voicereminderapp.data.local.entity.recovery.RecoveryReminderEntity
import com.luistureo.voicereminderapp.data.local.entity.recovery.RecoverySupportContactEntity
import com.luistureo.voicereminderapp.data.local.entity.recovery.RecoveryTriggerEntity

@Dao
interface RecoveryDao {
    @Query("SELECT * FROM recovery_goals WHERE status != 'ARCHIVED' ORDER BY updatedAtEpochMillis DESC")
    suspend fun getVisibleGoals(): List<RecoveryGoalEntity>

    @Query("SELECT * FROM recovery_goals WHERE id = :goalId LIMIT 1")
    suspend fun getGoalById(goalId: Int): RecoveryGoalEntity?

    @Insert
    suspend fun insertGoal(goal: RecoveryGoalEntity): Long

    @Update
    suspend fun updateGoal(goal: RecoveryGoalEntity)

    @Query("UPDATE recovery_goals SET status = :status, updatedAtEpochMillis = :updatedAt WHERE id = :goalId")
    suspend fun updateGoalStatus(goalId: Int, status: String, updatedAt: Long)

    @Query("DELETE FROM recovery_goals WHERE id = :goalId")
    suspend fun deleteGoalRow(goalId: Int)

    @Upsert
    suspend fun upsertCheckIn(checkIn: RecoveryCheckInEntity): Long

    @Query("SELECT * FROM recovery_check_ins WHERE goalHistoryKey = :historyKey ORDER BY dateEpochDay DESC, updatedAtEpochMillis DESC")
    suspend fun getCheckIns(historyKey: String): List<RecoveryCheckInEntity>

    @Query("SELECT * FROM recovery_check_ins WHERE goalHistoryKey = :historyKey AND dateEpochDay = :dateEpochDay LIMIT 1")
    suspend fun getCheckIn(historyKey: String, dateEpochDay: Long): RecoveryCheckInEntity?

    @Query("SELECT * FROM recovery_check_ins ORDER BY dateEpochDay DESC, updatedAtEpochMillis DESC")
    suspend fun getAllCheckIns(): List<RecoveryCheckInEntity>

    @Query("DELETE FROM recovery_check_ins WHERE goalHistoryKey = :historyKey")
    suspend fun deleteCheckIns(historyKey: String)

    @Query(
        "UPDATE recovery_check_ins SET cravingIntensity = NULL, `trigger` = NULL, " +
            "helpfulAction = NULL, note = NULL WHERE goalHistoryKey = :historyKey"
    )
    suspend fun anonymizeCheckIns(historyKey: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrigger(trigger: RecoveryTriggerEntity): Long

    @Update
    suspend fun updateTrigger(trigger: RecoveryTriggerEntity)

    @Delete
    suspend fun deleteTrigger(trigger: RecoveryTriggerEntity)

    @Query("SELECT * FROM recovery_triggers WHERE goalId = :goalId ORDER BY sortOrder, id")
    suspend fun getTriggers(goalId: Int): List<RecoveryTriggerEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHelpfulAction(action: RecoveryHelpfulActionEntity): Long

    @Update
    suspend fun updateHelpfulAction(action: RecoveryHelpfulActionEntity)

    @Delete
    suspend fun deleteHelpfulAction(action: RecoveryHelpfulActionEntity)

    @Query("SELECT * FROM recovery_helpful_actions WHERE goalId = :goalId ORDER BY sortOrder, id")
    suspend fun getHelpfulActions(goalId: Int): List<RecoveryHelpfulActionEntity>

    @Insert
    suspend fun insertSupportContact(contact: RecoverySupportContactEntity): Long

    @Update
    suspend fun updateSupportContact(contact: RecoverySupportContactEntity)

    @Delete
    suspend fun deleteSupportContact(contact: RecoverySupportContactEntity)

    @Query("SELECT * FROM recovery_support_contacts WHERE goalId = :goalId ORDER BY name COLLATE NOCASE")
    suspend fun getSupportContacts(goalId: Int): List<RecoverySupportContactEntity>

    @Upsert
    suspend fun upsertMilestone(milestone: RecoveryMilestoneEntity): Long

    @Query("SELECT * FROM recovery_milestones WHERE goalId = :goalId ORDER BY thresholdDays, id")
    suspend fun getMilestones(goalId: Int): List<RecoveryMilestoneEntity>

    @Query("UPDATE recovery_milestones SET achievedAtEpochMillis = :achievedAt WHERE id = :milestoneId AND achievedAtEpochMillis IS NULL")
    suspend fun markMilestoneReached(milestoneId: Int, achievedAt: Long)

    @Upsert
    suspend fun upsertReminder(reminder: RecoveryReminderEntity): Long

    @Query("SELECT * FROM recovery_reminders WHERE goalId = :goalId ORDER BY type")
    suspend fun getReminders(goalId: Int): List<RecoveryReminderEntity>

    @Query("SELECT * FROM recovery_reminders WHERE enabled = 1 ORDER BY id")
    suspend fun getEnabledReminders(): List<RecoveryReminderEntity>

    @Query("DELETE FROM recovery_reminders WHERE id = :reminderId")
    suspend fun deleteReminder(reminderId: Int)

    @Transaction
    suspend fun archiveGoal(goalId: Int, updatedAt: Long) {
        updateGoalStatus(goalId, "ARCHIVED", updatedAt)
    }

    @Transaction
    suspend fun deleteGoalKeepingAnonymousHistory(goalId: Int, historyKey: String) {
        anonymizeCheckIns(historyKey)
        deleteGoalRow(goalId)
    }

    @Transaction
    suspend fun deleteGoalAndHistory(goalId: Int, historyKey: String) {
        deleteCheckIns(historyKey)
        deleteGoalRow(goalId)
    }
}
