package com.luistureo.voicereminderapp.domain.recovery.service

import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryCheckIn
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryCheckInStatus
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryStatistics
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryStatisticsRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

object RecoveryStatisticsCalculator {
    fun bounds(range: RecoveryStatisticsRange, reference: LocalDate): Pair<LocalDate, LocalDate> =
        when (range) {
            RecoveryStatisticsRange.DAY -> reference to reference
            RecoveryStatisticsRange.WEEK ->
                reference.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) to
                    reference.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            RecoveryStatisticsRange.MONTH -> reference.withDayOfMonth(1) to
                reference.withDayOfMonth(reference.lengthOfMonth())
            RecoveryStatisticsRange.YEAR -> reference.withDayOfYear(1) to
                reference.withDayOfYear(reference.lengthOfYear())
        }

    fun calculate(
        allCheckIns: List<RecoveryCheckIn>,
        range: RecoveryStatisticsRange,
        reference: LocalDate
    ): RecoveryStatistics {
        val normalized = allCheckIns
            .groupBy { it.date }
            .mapValues { (_, entries) -> entries.maxBy { it.updatedAtEpochMillis } }
            .values
        val (start, end) = bounds(range, reference)
        val bounded = normalized.filter { it.date in start..end }
        val streaks = RecoveryStreakPolicy.calculate(normalized)
        return RecoveryStatistics(
            startDate = start,
            endDate = end,
            successfulDays = bounded.count { it.status == RecoveryCheckInStatus.ACHIEVED },
            difficultDays = bounded.count {
                it.status == RecoveryCheckInStatus.DIFFICULTY_MANAGED ||
                    it.status == RecoveryCheckInStatus.LAPSE
            },
            reducedFrequencyDays = bounded.count { it.reducedFrequency },
            checkIns = bounded.size,
            currentStreak = streaks.first,
            bestStreak = streaks.second,
            helpfulActionsUsed = bounded.count { !it.helpfulAction.isNullOrBlank() },
            commonTriggers = bounded.mapNotNull { it.trigger?.trim()?.takeIf(String::isNotBlank) }
                .groupingBy { it.lowercase() }
                .eachCount()
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(5)
                .map { it.key to it.value }
        )
    }

    fun calculateAllTime(
        allCheckIns: List<RecoveryCheckIn>,
        reference: LocalDate
    ): RecoveryStatistics {
        val normalized = allCheckIns
            .groupBy { it.date }
            .mapValues { (_, entries) -> entries.maxBy { it.updatedAtEpochMillis } }
            .values
        val fallback = calculate(allCheckIns, RecoveryStatisticsRange.YEAR, reference)
        if (normalized.isEmpty()) return fallback
        val streaks = RecoveryStreakPolicy.calculate(normalized)
        return fallback.copy(
            startDate = normalized.minOf { it.date },
            endDate = normalized.maxOf { it.date },
            successfulDays = normalized.count { it.status == RecoveryCheckInStatus.ACHIEVED },
            difficultDays = normalized.count {
                it.status == RecoveryCheckInStatus.DIFFICULTY_MANAGED ||
                    it.status == RecoveryCheckInStatus.LAPSE
            },
            reducedFrequencyDays = normalized.count { it.reducedFrequency },
            checkIns = normalized.size,
            currentStreak = streaks.first,
            bestStreak = streaks.second,
            helpfulActionsUsed = normalized.count { !it.helpfulAction.isNullOrBlank() },
            commonTriggers = normalized.mapNotNull { it.trigger?.trim()?.takeIf(String::isNotBlank) }
                .groupingBy { it.lowercase() }
                .eachCount()
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(5)
                .map { it.key to it.value }
        )
    }
}

object RecoveryStreakPolicy {
    fun calculate(checkIns: Collection<RecoveryCheckIn>): Pair<Int, Int> {
        var current = 0
        var best = 0
        var previousDate: LocalDate? = null
        checkIns.groupBy { it.date }
            .mapValues { (_, entries) -> entries.maxBy { it.updatedAtEpochMillis } }
            .values
            .sortedBy { it.date }
            .forEach { checkIn ->
                if (previousDate != null && checkIn.date != previousDate?.plusDays(1)) {
                    current = 0
                }
                when {
                    checkIn.status == RecoveryCheckInStatus.LAPSE && checkIn.resetsStreak -> current = 0
                    checkIn.status == RecoveryCheckInStatus.ACHIEVED ||
                        checkIn.status == RecoveryCheckInStatus.DIFFICULTY_MANAGED -> {
                        current += 1
                        best = maxOf(best, current)
                    }
                }
                previousDate = checkIn.date
            }
        return current to best
    }
}
