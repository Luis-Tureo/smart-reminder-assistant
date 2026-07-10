package com.luistureo.voicereminderapp.domain.routine.service

import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionState
import com.luistureo.voicereminderapp.domain.routine.model.RoutineHistory
import com.luistureo.voicereminderapp.domain.routine.model.RoutineHistoryRange
import com.luistureo.voicereminderapp.domain.routine.model.RoutinePeriod
import com.luistureo.voicereminderapp.domain.routine.model.RoutinePeriodPercentage
import com.luistureo.voicereminderapp.domain.routine.model.RoutineStatistics
import com.luistureo.voicereminderapp.domain.routine.model.RoutineStreakSettings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

object RoutineStatisticsCalculator {
    fun range(range: RoutineHistoryRange, reference: LocalDate): Pair<LocalDate, LocalDate> = when (range) {
        RoutineHistoryRange.DAY -> reference to reference
        RoutineHistoryRange.WEEK -> reference.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) to
            reference.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        RoutineHistoryRange.MONTH -> reference.withDayOfMonth(1) to
            reference.withDayOfMonth(reference.lengthOfMonth())
        RoutineHistoryRange.YEAR -> reference.withDayOfYear(1) to
            reference.withDayOfYear(reference.lengthOfYear())
    }

    fun calculate(
        histories: List<RoutineHistory>,
        range: RoutineHistoryRange,
        reference: LocalDate,
        streakSettings: RoutineStreakSettings = RoutineStreakSettings()
    ): RoutineStatistics {
        val (start, end) = range(range, reference)
        val bounded = histories.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
        val completedTasks = bounded.sumOf { it.completedTasks }
        val totalTasks = bounded.sumOf { it.totalTasks }
        val percentage = if (totalTasks == 0) 0 else
            ((completedTasks * 100.0) / totalTasks).roundToInt().coerceIn(0, 100)
        val allByDate = histories.groupBy { it.date }
        val qualifyingDates = allByDate.filterValues { day -> qualifies(day, streakSettings) }.keys
        val best = longestConsecutive(qualifyingDates)
        val current = currentConsecutive(qualifyingDates, reference)
        val periodPercentages = RoutinePeriod.entries.map { period ->
            val values = bounded.filter { it.periodAtExecution == period }
            val periodCompleted = values.sumOf { it.completedTasks }
            val periodTotal = values.sumOf { it.totalTasks }
            RoutinePeriodPercentage(
                period,
                if (periodTotal == 0) 0 else ((periodCompleted * 100.0) / periodTotal).roundToInt()
            )
        }
        return RoutineStatistics(
            startDate = start,
            endDate = end,
            completedRoutines = bounded.count { it.finalState == RoutineExecutionState.COMPLETED },
            partiallyCompletedRoutines = bounded.count {
                it.finalState == RoutineExecutionState.PARTIALLY_COMPLETED
            },
            skippedRoutines = bounded.count { it.finalState == RoutineExecutionState.SKIPPED },
            notCompletedRoutines = bounded.count {
                it.finalState == RoutineExecutionState.NOT_COMPLETED
            },
            taskCompletionPercentage = percentage,
            currentStreak = current,
            bestStreak = best,
            mostCompletedRoutine = bounded.filter {
                it.finalState == RoutineExecutionState.COMPLETED
            }.groupingBy { it.routineNameAtExecution.orEmpty() }.eachCount()
                .filterKeys(String::isNotBlank).maxByOrNull { it.value }?.key,
            mostPendingTask = bounded.flatMap { it.pendingTaskTitles }.groupingBy { it }.eachCount()
                .maxByOrNull { it.value }?.key,
            periodPercentages = periodPercentages,
            dailyStates = bounded.groupBy { it.date }.mapValues { (_, day) -> dayState(day) }
        )
    }

    fun supportiveStreakMessage(currentStreak: Int): String =
        if (currentStreak > 0) "Continúa a tu ritmo." else "Hoy puedes comenzar una nueva racha."

    private fun qualifies(day: List<RoutineHistory>, settings: RoutineStreakSettings): Boolean {
        if (day.isEmpty()) return false
        return day.all { history ->
            history.finalState == RoutineExecutionState.COMPLETED ||
                settings.countPartialDays &&
                history.finalState == RoutineExecutionState.PARTIALLY_COMPLETED &&
                history.completionPercentage >= settings.partialThresholdPercentage
        }
    }

    private fun longestConsecutive(dates: Set<LocalDate>): Int {
        var best = 0
        var current = 0
        var previous: LocalDate? = null
        dates.sorted().forEach { date ->
            current = if (previous?.plusDays(1) == date) current + 1 else 1
            best = maxOf(best, current)
            previous = date
        }
        return best
    }

    private fun currentConsecutive(dates: Set<LocalDate>, reference: LocalDate): Int {
        var date = reference
        var count = 0
        while (date in dates) {
            count++
            date = date.minusDays(1)
        }
        return count
    }

    private fun dayState(day: List<RoutineHistory>): RoutineExecutionState = when {
        day.all { it.finalState == RoutineExecutionState.COMPLETED } -> RoutineExecutionState.COMPLETED
        day.any { it.finalState == RoutineExecutionState.PARTIALLY_COMPLETED } ->
            RoutineExecutionState.PARTIALLY_COMPLETED
        day.any { it.finalState == RoutineExecutionState.NOT_COMPLETED } ->
            RoutineExecutionState.NOT_COMPLETED
        day.any { it.finalState == RoutineExecutionState.SKIPPED } -> RoutineExecutionState.SKIPPED
        else -> RoutineExecutionState.NOT_COMPLETED
    }
}
