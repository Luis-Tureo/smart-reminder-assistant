package com.luistureo.voicereminderapp.core.calendar.unified

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

object CalendarConflictPolicy {
    private const val CONFLICT_WINDOW_MINUTES = 5L

    data class Candidate(
        val id: String,
        val startAtEpochMillis: Long?,
        val isAllDay: Boolean
    ) {
        val date: LocalDate?
            get() = startAtEpochMillis?.let {
                Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            }
    }

    data class ConflictGroup(
        val id: String,
        val candidates: List<Candidate>
    )

    fun findConflicts(candidates: List<Candidate>): List<ConflictGroup> {
        val timedCandidates = candidates
            .filter { !it.isAllDay && it.startAtEpochMillis != null && it.date != null }
            .sortedBy { it.startAtEpochMillis }

        val groups = mutableListOf<MutableList<Candidate>>()

        timedCandidates.forEach { candidate ->
            val matchingGroup = groups.firstOrNull { group ->
                group.any { existing -> areConflicting(existing, candidate) }
            }

            if (matchingGroup == null) {
                groups += mutableListOf(candidate)
            } else {
                matchingGroup += candidate
            }
        }

        return groups
            .filter { it.size >= 2 }
            .map { group ->
                ConflictGroup(
                    id = group
                        .sortedBy { it.id }
                        .joinToString(separator = "|") { it.id },
                    candidates = group
                )
            }
    }

    fun occurrenceCandidateId(reminderId: Int, occurrenceAtEpochMillis: Long): String {
        return "$reminderId:$occurrenceAtEpochMillis"
    }

    fun areConflicting(first: Candidate, second: Candidate): Boolean {
        if (first.isAllDay || second.isAllDay) return false
        val firstStart = first.startAtEpochMillis ?: return false
        val secondStart = second.startAtEpochMillis ?: return false
        if (first.date != second.date) return false

        val diffMinutes = abs(firstStart - secondStart) / 60_000L
        return diffMinutes < CONFLICT_WINDOW_MINUTES
    }
}
