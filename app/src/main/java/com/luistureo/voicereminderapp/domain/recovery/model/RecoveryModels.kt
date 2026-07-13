package com.luistureo.voicereminderapp.domain.recovery.model

import java.time.LocalDate

enum class RecoveryCategory {
    ALCOHOL,
    NICOTINE,
    GAMBLING,
    GAMING,
    SCREEN_USE,
    COMPULSIVE_SHOPPING,
    OTHER
}

enum class RecoveryGoalStatus { ACTIVE, PAUSED, ARCHIVED }

enum class RecoveryCheckInStatus {
    ACHIEVED,
    DIFFICULTY_MANAGED,
    LAPSE,
    PREFER_NOT_TO_REGISTER
}

enum class RecoveryContactAction { CALL, SMS, VIEW }

enum class RecoveryMilestoneKind { FIRST_CHECK_IN, DAYS }

enum class RecoveryReminderType {
    DAILY_CHECK_IN,
    PERSONAL_MOTIVATION,
    MILESTONE,
    HIGH_RISK_TIME,
    SUPPORT
}

enum class RecoveryDeletionMode {
    ARCHIVE,
    DELETE_KEEP_ANONYMOUS_HISTORY,
    DELETE_ALL
}

enum class RecoveryStatisticsRange { DAY, WEEK, MONTH, YEAR }

data class RecoveryGoal(
    val id: Int = 0,
    val historyKey: String = "",
    val title: String,
    val category: RecoveryCategory,
    val customCategory: String? = null,
    val startDate: LocalDate? = null,
    val targetDate: LocalDate? = null,
    val personalReason: String? = null,
    val motivations: String? = null,
    val reductionTrackingEnabled: Boolean = false,
    val status: RecoveryGoalStatus = RecoveryGoalStatus.ACTIVE,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
)

data class RecoveryCheckIn(
    val id: Int = 0,
    val goalId: Int?,
    val goalHistoryKey: String,
    val date: LocalDate,
    val status: RecoveryCheckInStatus,
    val cravingIntensity: Int? = null,
    val trigger: String? = null,
    val helpfulAction: String? = null,
    val note: String? = null,
    val reducedFrequency: Boolean = false,
    val resetsStreak: Boolean = false,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
)

data class RecoveryTrigger(
    val id: Int = 0,
    val goalId: Int,
    val label: String,
    val enabled: Boolean = true,
    val sortOrder: Int = 0
)

data class RecoveryHelpfulAction(
    val id: Int = 0,
    val goalId: Int,
    val label: String,
    val enabled: Boolean = true,
    val sortOrder: Int = 0
)

data class RecoverySupportContact(
    val id: Int = 0,
    val goalId: Int,
    val name: String,
    val description: String? = null,
    val phone: String,
    val preferredAction: RecoveryContactAction = RecoveryContactAction.CALL
)

data class RecoveryMilestone(
    val id: Int = 0,
    val goalId: Int,
    val label: String,
    val thresholdDays: Int? = null,
    val kind: RecoveryMilestoneKind,
    val enabled: Boolean = true,
    val achievedAtEpochMillis: Long? = null
)

data class RecoveryReminder(
    val id: Int = 0,
    val goalId: Int,
    val type: RecoveryReminderType,
    val timeMinutes: Int,
    val enabled: Boolean = true,
    val snoozeMinutes: Int = 10,
    val updatedAtEpochMillis: Long = System.currentTimeMillis()
)

data class RecoveryStatistics(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val successfulDays: Int,
    val difficultDays: Int,
    val reducedFrequencyDays: Int,
    val checkIns: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val helpfulActionsUsed: Int,
    val commonTriggers: List<Pair<String, Int>>
)

data class RecoveryDashboard(
    val goal: RecoveryGoal,
    val todayCheckIn: RecoveryCheckIn?,
    val recentCheckIns: List<RecoveryCheckIn>,
    val statistics: RecoveryStatistics,
    val helpfulActions: List<RecoveryHelpfulAction>,
    val contacts: List<RecoverySupportContact>
)
