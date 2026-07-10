package com.luistureo.voicereminderapp.core.routine

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutinePostponePolicyTest {
    @Test
    fun exposesFiveTenThirtyAndSixtyMinutePresets() {
        assertEquals(listOf(5, 10, 30, 60), RoutinePostponePolicy.presetMinutes)
    }

    @Test
    fun presetOptionsCalculateTheirExpectedTriggerTimes() {
        val now = 1_000_000L

        listOf(5, 10, 30, 60).forEach { minutes ->
            assertEquals(
                now + minutes * 60_000L,
                RoutinePostponePolicy.triggerAt(now, minutes)
            )
        }
    }

    @Test
    fun customOptionUsesAnyValueInsideSupportedRange() {
        val now = 2_000_000L
        val customMinutes = 47

        assertEquals(customMinutes, RoutinePostponePolicy.normalize(customMinutes))
        assertEquals(
            now + customMinutes * 60_000L,
            RoutinePostponePolicy.triggerAt(now, customMinutes)
        )
    }

    @Test
    fun customBoundaryValuesAreAccepted() {
        assertEquals(
            RoutinePostponePolicy.MIN_CUSTOM_MINUTES,
            RoutinePostponePolicy.normalize(RoutinePostponePolicy.MIN_CUSTOM_MINUTES)
        )
        assertEquals(
            RoutinePostponePolicy.MAX_CUSTOM_MINUTES,
            RoutinePostponePolicy.normalize(RoutinePostponePolicy.MAX_CUSTOM_MINUTES)
        )
    }

    @Test
    fun missingOrOutOfRangeCustomValuesUseDefaultTenMinutes() {
        assertEquals(RoutinePostponePolicy.DEFAULT_MINUTES, RoutinePostponePolicy.normalize(null))
        assertEquals(RoutinePostponePolicy.DEFAULT_MINUTES, RoutinePostponePolicy.normalize(0))
        assertEquals(
            RoutinePostponePolicy.DEFAULT_MINUTES,
            RoutinePostponePolicy.normalize(RoutinePostponePolicy.MAX_CUSTOM_MINUTES + 1)
        )
    }
}
