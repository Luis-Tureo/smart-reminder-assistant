package com.luistureo.voicereminderapp.data.mapper.recovery

import com.luistureo.voicereminderapp.data.local.entity.recovery.RecoveryCheckInEntity
import com.luistureo.voicereminderapp.data.local.entity.recovery.RecoveryGoalEntity
import com.luistureo.voicereminderapp.data.local.entity.recovery.RecoveryHelpfulActionEntity
import com.luistureo.voicereminderapp.data.local.entity.recovery.RecoveryMilestoneEntity
import com.luistureo.voicereminderapp.data.local.entity.recovery.RecoveryReminderEntity
import com.luistureo.voicereminderapp.data.local.entity.recovery.RecoverySupportContactEntity
import com.luistureo.voicereminderapp.data.local.entity.recovery.RecoveryTriggerEntity
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryCategory
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryCheckIn
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryCheckInStatus
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryContactAction
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryGoal
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryGoalStatus
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryHelpfulAction
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryMilestone
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryMilestoneKind
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryReminder
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryReminderType
import com.luistureo.voicereminderapp.domain.recovery.model.RecoverySupportContact
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryTrigger
import java.time.LocalDate

fun RecoveryGoalEntity.toDomain() = RecoveryGoal(
    id, historyKey, title, RecoveryCategory.valueOf(category), customCategory,
    startDateEpochDay?.let(LocalDate::ofEpochDay), targetDateEpochDay?.let(LocalDate::ofEpochDay),
    personalReason, motivations, reductionTrackingEnabled, RecoveryGoalStatus.valueOf(status),
    createdAtEpochMillis, updatedAtEpochMillis
)

fun RecoveryGoal.toEntity() = RecoveryGoalEntity(
    id, historyKey, title, category.name, customCategory, startDate?.toEpochDay(),
    targetDate?.toEpochDay(), personalReason, motivations, reductionTrackingEnabled,
    status.name, createdAtEpochMillis, updatedAtEpochMillis
)

fun RecoveryCheckInEntity.toDomain() = RecoveryCheckIn(
    id, goalId, goalHistoryKey, LocalDate.ofEpochDay(dateEpochDay),
    RecoveryCheckInStatus.valueOf(status), cravingIntensity, trigger, helpfulAction, note,
    reducedFrequency, resetsStreak, createdAtEpochMillis, updatedAtEpochMillis
)

fun RecoveryCheckIn.toEntity() = RecoveryCheckInEntity(
    id, goalId, goalHistoryKey, date.toEpochDay(), status.name, cravingIntensity,
    trigger, helpfulAction, note, reducedFrequency, resetsStreak,
    createdAtEpochMillis, updatedAtEpochMillis
)

fun RecoveryTriggerEntity.toDomain() = RecoveryTrigger(id, goalId, label, enabled, sortOrder)
fun RecoveryTrigger.toEntity() = RecoveryTriggerEntity(id, goalId, label, enabled, sortOrder)

fun RecoveryHelpfulActionEntity.toDomain() = RecoveryHelpfulAction(id, goalId, label, enabled, sortOrder)
fun RecoveryHelpfulAction.toEntity() = RecoveryHelpfulActionEntity(id, goalId, label, enabled, sortOrder)

fun RecoverySupportContactEntity.toDomain() = RecoverySupportContact(
    id, goalId, name, description, phone, RecoveryContactAction.valueOf(preferredAction)
)
fun RecoverySupportContact.toEntity() = RecoverySupportContactEntity(
    id, goalId, name, description, phone, preferredAction.name
)

fun RecoveryMilestoneEntity.toDomain() = RecoveryMilestone(
    id, goalId, label, thresholdDays, RecoveryMilestoneKind.valueOf(kind), enabled,
    achievedAtEpochMillis
)
fun RecoveryMilestone.toEntity() = RecoveryMilestoneEntity(
    id, goalId, label, thresholdDays, kind.name, enabled, achievedAtEpochMillis
)

fun RecoveryReminderEntity.toDomain() = RecoveryReminder(
    id, goalId, RecoveryReminderType.valueOf(type), timeMinutes, enabled,
    snoozeMinutes, updatedAtEpochMillis
)
fun RecoveryReminder.toEntity() = RecoveryReminderEntity(
    id, goalId, type.name, timeMinutes, enabled, snoozeMinutes, updatedAtEpochMillis
)
