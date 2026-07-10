package com.luistureo.voicereminderapp.domain.routine

import com.luistureo.voicereminderapp.domain.routine.model.*
import com.luistureo.voicereminderapp.domain.routine.service.RoutineStatisticsCalculator
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RoutineStatisticsCalculatorTest {
    private val today = LocalDate.of(2026, 7, 10)

    @Test fun calculatesDailyWeeklyMonthlyAndYearlyFromStoredTotals() {
        val histories = listOf(
            history(today, 4, 5, RoutineExecutionState.PARTIALLY_COMPLETED),
            history(today.minusDays(2), 5, 5, RoutineExecutionState.COMPLETED),
            history(today.minusMonths(2), 2, 4, RoutineExecutionState.PARTIALLY_COMPLETED)
        )
        assertEquals(80, calculate(histories, RoutineHistoryRange.DAY).taskCompletionPercentage)
        assertEquals(90, calculate(histories, RoutineHistoryRange.WEEK).taskCompletionPercentage)
        assertEquals(90, calculate(histories, RoutineHistoryRange.MONTH).taskCompletionPercentage)
        assertEquals(79, calculate(histories, RoutineHistoryRange.YEAR).taskCompletionPercentage)
    }

    @Test fun emptyHistoryAvoidsDivisionByZero() {
        assertEquals(0, calculate(emptyList(), RoutineHistoryRange.DAY).taskCompletionPercentage)
    }

    @Test fun historicalSnapshotDoesNotDependOnCurrentRoutineDefinition() {
        val stored = history(today, 2, 4, RoutineExecutionState.PARTIALLY_COMPLETED)
        assertEquals(50, calculate(listOf(stored), RoutineHistoryRange.DAY).taskCompletionPercentage)
    }

    @Test fun streakExcludesPartialByDefaultAndCanUseThreshold() {
        val histories = listOf(
            history(today.minusDays(1), 5, 5, RoutineExecutionState.COMPLETED),
            history(today, 4, 5, RoutineExecutionState.PARTIALLY_COMPLETED)
        )
        assertEquals(0, calculate(histories, RoutineHistoryRange.WEEK).currentStreak)
        val enabled = RoutineStatisticsCalculator.calculate(histories, RoutineHistoryRange.WEEK, today,
            RoutineStreakSettings(true, 80))
        assertEquals(2, enabled.currentStreak)
        assertEquals(2, enabled.bestStreak)
        assertFalse(RoutineStatisticsCalculator.supportiveStreakMessage(0).contains("perdiste", true))
    }

    @Test fun exposesFourNativeChartTypes() {
        assertEquals(4, RoutineChartType.entries.size)
    }

    private fun calculate(histories: List<RoutineHistory>, range: RoutineHistoryRange) =
        RoutineStatisticsCalculator.calculate(histories, range, today)

    private fun history(date: LocalDate, completed: Int, total: Int, state: RoutineExecutionState) =
        RoutineHistory(date = date, routineId = 1, completedTasks = completed, totalTasks = total,
            completionPercentage = completed * 100.0 / total, finalState = state,
            periodAtExecution = RoutinePeriod.MORNING, routineNameAtExecution = "Mañana")
}
