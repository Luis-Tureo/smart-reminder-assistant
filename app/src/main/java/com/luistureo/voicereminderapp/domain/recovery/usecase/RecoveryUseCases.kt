package com.luistureo.voicereminderapp.domain.recovery.usecase

import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryCheckIn
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryDashboard
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryDeletionMode
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryGoal
import com.luistureo.voicereminderapp.domain.recovery.repository.RecoveryRepository
import com.luistureo.voicereminderapp.domain.recovery.service.RecoveryMilestonePolicy
import com.luistureo.voicereminderapp.domain.recovery.service.RecoveryStatisticsCalculator
import java.time.LocalDate
import java.util.UUID

class SaveRecoveryGoalUseCase(private val repository: RecoveryRepository) {
    suspend operator fun invoke(goal: RecoveryGoal): Int {
        require(goal.title.trim().isNotBlank())
        require(goal.targetDate == null || goal.startDate == null || !goal.targetDate.isBefore(goal.startDate))
        val now = System.currentTimeMillis()
        val normalized = goal.copy(
            historyKey = goal.historyKey.ifBlank { UUID.randomUUID().toString() },
            title = goal.title.trim().take(80),
            customCategory = goal.customCategory?.trim()?.take(80)?.takeIf(String::isNotBlank),
            personalReason = goal.personalReason?.trim()?.take(2_000)?.takeIf(String::isNotBlank),
            motivations = goal.motivations?.trim()?.take(2_000)?.takeIf(String::isNotBlank),
            updatedAtEpochMillis = now,
            createdAtEpochMillis = goal.createdAtEpochMillis.takeIf { goal.id != 0 } ?: now
        )
        val id = repository.saveGoal(normalized)
        if (goal.id == 0) {
            RecoveryMilestonePolicy.defaultMilestones.forEach {
                repository.saveMilestone(it.copy(goalId = id))
            }
        }
        return id
    }
}

class RecordRecoveryCheckInUseCase(private val repository: RecoveryRepository) {
    suspend operator fun invoke(checkIn: RecoveryCheckIn): Int {
        require(checkIn.goalId != null && checkIn.goalId > 0)
        require(checkIn.goalHistoryKey.isNotBlank())
        require(checkIn.cravingIntensity == null || checkIn.cravingIntensity in 1..10)
        val existing = repository.getCheckIn(checkIn.goalHistoryKey, checkIn.date)
        val now = System.currentTimeMillis()
        val normalized = checkIn.copy(
            id = existing?.id ?: checkIn.id,
            trigger = checkIn.trigger?.trim()?.take(200)?.takeIf(String::isNotBlank),
            helpfulAction = checkIn.helpfulAction?.trim()?.take(200)?.takeIf(String::isNotBlank),
            note = checkIn.note?.trim()?.take(1_000)?.takeIf(String::isNotBlank),
            createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = now
        )
        val id = repository.saveCheckIn(normalized)
        val checkIns = repository.getCheckIns(checkIn.goalHistoryKey)
        repository.getMilestones(requireNotNull(checkIn.goalId)).filter {
            it.achievedAtEpochMillis == null && RecoveryMilestonePolicy.reached(it, checkIns)
        }.forEach { repository.markMilestoneReached(it.id, now) }
        return id
    }
}

class GetRecoveryDashboardUseCase(private val repository: RecoveryRepository) {
    suspend operator fun invoke(goal: RecoveryGoal, today: LocalDate = LocalDate.now()): RecoveryDashboard {
        val checkIns = repository.getCheckIns(goal.historyKey)
        return RecoveryDashboard(
            goal = goal,
            todayCheckIn = checkIns.firstOrNull { it.date == today },
            recentCheckIns = checkIns.take(7),
            statistics = RecoveryStatisticsCalculator.calculateAllTime(checkIns, today),
            helpfulActions = repository.getHelpfulActions(goal.id).filter { it.enabled },
            contacts = repository.getSupportContacts(goal.id)
        )
    }
}

class DeleteRecoveryGoalUseCase(private val repository: RecoveryRepository) {
    suspend operator fun invoke(goal: RecoveryGoal, mode: RecoveryDeletionMode) {
        require(goal.id > 0)
        repository.deleteGoal(goal, mode)
    }
}
