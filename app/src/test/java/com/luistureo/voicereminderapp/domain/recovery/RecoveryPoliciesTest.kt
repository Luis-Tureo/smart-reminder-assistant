package com.luistureo.voicereminderapp.domain.recovery

import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryCheckIn
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryCheckInStatus
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryMilestone
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryMilestoneKind
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryStatisticsRange
import com.luistureo.voicereminderapp.domain.recovery.service.RecoveryLapsePolicy
import com.luistureo.voicereminderapp.domain.recovery.service.RecoveryMilestonePolicy
import com.luistureo.voicereminderapp.domain.recovery.service.RecoveryStatisticsCalculator
import com.luistureo.voicereminderapp.domain.recovery.service.RecoveryStreakPolicy
import com.luistureo.voicereminderapp.domain.recovery.service.RecoveryWordingPolicy
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryPoliciesTest {
    @Test
    fun countsConsecutivePositiveDecisionsUntilConfirmedRestart() {
        val checkIns = listOf(
            checkIn(10, RecoveryCheckInStatus.ACHIEVED),
            checkIn(11, RecoveryCheckInStatus.DIFFICULTY_MANAGED),
            checkIn(12, RecoveryCheckInStatus.LAPSE, resets = true),
            checkIn(13, RecoveryCheckInStatus.ACHIEVED)
        )

        val (current, best) = RecoveryStreakPolicy.calculate(checkIns)

        assertEquals(1, current)
        assertEquals(2, best)
    }

    @Test
    fun unconfirmedLapseDoesNotEraseCurrentOrBestStreak() {
        val checkIns = listOf(
            checkIn(10, RecoveryCheckInStatus.ACHIEVED),
            checkIn(11, RecoveryCheckInStatus.LAPSE, resets = false),
            checkIn(12, RecoveryCheckInStatus.ACHIEVED)
        )

        assertEquals(2 to 2, RecoveryStreakPolicy.calculate(checkIns))
    }

    @Test
    fun lapseRestartDecisionOnlyChangesResetFlagAndPreservesPrivateHistory() {
        val original = checkIn(12, RecoveryCheckInStatus.LAPSE, resets = false).copy(
            id = 8,
            note = "Nota privada",
            trigger = "Estrés"
        )

        val restarted = RecoveryLapsePolicy.withRestartDecision(original, restart = true)

        assertTrue(restarted.resetsStreak)
        assertEquals(original.id, restarted.id)
        assertEquals("Nota privada", restarted.note)
        assertEquals("Estrés", restarted.trigger)
    }

    @Test
    fun statisticsUseRequestedRangeAndKeepMultipleMeasuresOfProgress() {
        val reference = LocalDate.of(2026, 7, 12)
        val data = listOf(
            checkIn(10, RecoveryCheckInStatus.ACHIEVED).copy(helpfulAction = "Caminar"),
            checkIn(11, RecoveryCheckInStatus.DIFFICULTY_MANAGED).copy(
                trigger = "Estrés", reducedFrequency = true
            ),
            checkIn(12, RecoveryCheckInStatus.LAPSE).copy(trigger = "Estrés"),
            RecoveryCheckIn(
                goalId = 1,
                goalHistoryKey = "history",
                date = LocalDate.of(2026, 6, 1),
                status = RecoveryCheckInStatus.ACHIEVED
            )
        )

        val stats = RecoveryStatisticsCalculator.calculate(
            data,
            RecoveryStatisticsRange.WEEK,
            reference
        )

        assertEquals(1, stats.successfulDays)
        assertEquals(2, stats.difficultDays)
        assertEquals(1, stats.reducedFrequencyDays)
        assertEquals(3, stats.checkIns)
        assertEquals(1, stats.helpfulActionsUsed)
        assertEquals("estrés" to 2, stats.commonTriggers.single())
    }

    @Test
    fun milestonesSupportFirstCheckInBuiltInThresholdAndCustomThreshold() {
        val entries = (1..3).map { day -> checkIn(9 + day, RecoveryCheckInStatus.ACHIEVED) }
        val first = RecoveryMilestone(
            goalId = 1,
            label = "Primer registro",
            kind = RecoveryMilestoneKind.FIRST_CHECK_IN
        )
        val three = RecoveryMilestone(
            goalId = 1,
            label = "3 días",
            thresholdDays = 3,
            kind = RecoveryMilestoneKind.DAYS
        )
        val custom = RecoveryMilestone(
            goalId = 1,
            label = "10 días",
            thresholdDays = 10,
            kind = RecoveryMilestoneKind.DAYS
        )

        assertTrue(RecoveryMilestonePolicy.reached(first, entries))
        assertTrue(RecoveryMilestonePolicy.reached(three, entries))
        assertFalse(RecoveryMilestonePolicy.reached(custom, entries))
        assertFalse(RecoveryMilestonePolicy.reached(three.copy(enabled = false), entries))
    }

    @Test
    fun wordingPolicyAcceptsSupportAndRejectsShameOrUnsafeAdvice() {
        assertTrue(RecoveryWordingPolicy.isSupportive("Un día difícil no elimina todo tu avance."))
        assertTrue(RecoveryWordingPolicy.isSupportive("Cada decisión positiva cuenta."))
        assertFalse(RecoveryWordingPolicy.isSupportive("Fracasaste."))
        assertFalse(RecoveryWordingPolicy.isSupportive("Perdiste todo."))
        assertFalse(RecoveryWordingPolicy.isSupportive("Volviste al principio."))
        assertFalse(RecoveryWordingPolicy.isSupportive("Sigue esta dosis."))
    }

    private fun checkIn(day: Int, status: RecoveryCheckInStatus, resets: Boolean = false) =
        RecoveryCheckIn(
            goalId = 1,
            goalHistoryKey = "history",
            date = LocalDate.of(2026, 7, day),
            status = status,
            resetsStreak = resets
        )
}
