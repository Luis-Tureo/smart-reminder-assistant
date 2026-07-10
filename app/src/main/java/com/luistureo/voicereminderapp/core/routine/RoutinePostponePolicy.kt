package com.luistureo.voicereminderapp.core.routine

object RoutinePostponePolicy {
    const val DEFAULT_MINUTES = 10
    const val MIN_CUSTOM_MINUTES = 1
    const val MAX_CUSTOM_MINUTES = 1_440
    val presetMinutes: List<Int> = listOf(5, 10, 30, 60)

    fun normalize(minutes: Int?): Int =
        minutes?.takeIf { it in MIN_CUSTOM_MINUTES..MAX_CUSTOM_MINUTES }
            ?: DEFAULT_MINUTES

    fun triggerAt(nowEpochMillis: Long, minutes: Int): Long =
        nowEpochMillis + normalize(minutes) * 60_000L
}
