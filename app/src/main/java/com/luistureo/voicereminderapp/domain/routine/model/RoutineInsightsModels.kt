package com.luistureo.voicereminderapp.domain.routine.model

import java.time.LocalDate

enum class RoutineHistoryRange { DAY, WEEK, MONTH, YEAR }
enum class RoutineChartType { BAR, CIRCULAR, CALENDAR, PERCENTAGE_LIST }

data class RoutineStreakSettings(
    val countPartialDays: Boolean = false,
    val partialThresholdPercentage: Int = 80
)

data class RoutinePeriodPercentage(
    val period: RoutinePeriod,
    val percentage: Int
)

data class RoutineStatistics(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val completedRoutines: Int,
    val partiallyCompletedRoutines: Int,
    val skippedRoutines: Int,
    val notCompletedRoutines: Int,
    val taskCompletionPercentage: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val mostCompletedRoutine: String?,
    val mostPendingTask: String?,
    val periodPercentages: List<RoutinePeriodPercentage>,
    val dailyStates: Map<LocalDate, RoutineExecutionState>
)

data class RoutineSuggestionSettings(
    val enabled: Boolean = true,
    val preferredHour: Int = 18,
    val preferredMinute: Int = 0,
    val showBubble: Boolean = true,
    val speak: Boolean = false
)
