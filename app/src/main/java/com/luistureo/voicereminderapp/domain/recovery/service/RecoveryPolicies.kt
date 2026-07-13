package com.luistureo.voicereminderapp.domain.recovery.service

import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryCheckIn
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryCheckInStatus
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryMilestone
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryMilestoneKind

object RecoveryLapsePolicy {
    fun withRestartDecision(checkIn: RecoveryCheckIn, restart: Boolean): RecoveryCheckIn {
        require(checkIn.status == RecoveryCheckInStatus.LAPSE)
        return checkIn.copy(resetsStreak = restart)
    }

    const val SUPPORTIVE_MESSAGE =
        "Puedes revisar lo ocurrido y continuar. Tu avance anterior sigue siendo importante."
}

object RecoveryMilestonePolicy {
    val defaultMilestones = listOf(
        RecoveryMilestone(0, 0, "Primer registro", null, RecoveryMilestoneKind.FIRST_CHECK_IN),
        RecoveryMilestone(0, 0, "1 día", 1, RecoveryMilestoneKind.DAYS),
        RecoveryMilestone(0, 0, "3 días", 3, RecoveryMilestoneKind.DAYS),
        RecoveryMilestone(0, 0, "1 semana", 7, RecoveryMilestoneKind.DAYS),
        RecoveryMilestone(0, 0, "2 semanas", 14, RecoveryMilestoneKind.DAYS),
        RecoveryMilestone(0, 0, "1 mes", 30, RecoveryMilestoneKind.DAYS)
    )

    fun reached(milestone: RecoveryMilestone, checkIns: List<RecoveryCheckIn>): Boolean {
        if (!milestone.enabled) return false
        return when (milestone.kind) {
            RecoveryMilestoneKind.FIRST_CHECK_IN -> checkIns.isNotEmpty()
            RecoveryMilestoneKind.DAYS -> {
                val progressDays = checkIns.count {
                    it.status == RecoveryCheckInStatus.ACHIEVED ||
                        it.status == RecoveryCheckInStatus.DIFFICULTY_MANAGED
                }
                progressDays >= (milestone.thresholdDays ?: Int.MAX_VALUE)
            }
        }
    }

    fun supportiveMessage(thresholdDays: Int?): String = when {
        thresholdDays == null -> "Cada decisión positiva cuenta."
        thresholdDays <= 1 -> "Has avanzado un día más."
        thresholdDays <= 7 -> "Tu esfuerzo de esta semana cuenta."
        else -> "Sigue usando las estrategias que te ayudan."
    }
}

object RecoveryWordingPolicy {
    private val forbidden = listOf(
        "fracasaste",
        "perdiste todo",
        "volviste al principio",
        "desintoxicación",
        "dosis",
        "deja tu medicamento"
    )

    fun isSupportive(text: String): Boolean = forbidden.none { text.lowercase().contains(it) }
}
