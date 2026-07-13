package com.luistureo.voicereminderapp.data.local.entity.recovery

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recovery_goals",
    indices = [Index(value = ["historyKey"], unique = true), Index("status")]
)
data class RecoveryGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val historyKey: String,
    val title: String,
    val category: String,
    val customCategory: String?,
    val startDateEpochDay: Long?,
    val targetDateEpochDay: Long?,
    val personalReason: String?,
    val motivations: String?,
    val reductionTrackingEnabled: Boolean,
    val status: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Entity(
    tableName = "recovery_check_ins",
    foreignKeys = [
        ForeignKey(
            entity = RecoveryGoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("goalId"),
        Index("goalHistoryKey"),
        Index(value = ["goalHistoryKey", "dateEpochDay"], unique = true),
        Index("dateEpochDay")
    ]
)
data class RecoveryCheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val goalId: Int?,
    val goalHistoryKey: String,
    val dateEpochDay: Long,
    val status: String,
    val cravingIntensity: Int?,
    val trigger: String?,
    val helpfulAction: String?,
    val note: String?,
    val reducedFrequency: Boolean,
    val resetsStreak: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Entity(
    tableName = "recovery_triggers",
    foreignKeys = [ForeignKey(
        entity = RecoveryGoalEntity::class,
        parentColumns = ["id"], childColumns = ["goalId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("goalId"), Index(value = ["goalId", "label"], unique = true)]
)
data class RecoveryTriggerEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val goalId: Int,
    val label: String,
    val enabled: Boolean,
    val sortOrder: Int
)

@Entity(
    tableName = "recovery_helpful_actions",
    foreignKeys = [ForeignKey(
        entity = RecoveryGoalEntity::class,
        parentColumns = ["id"], childColumns = ["goalId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("goalId"), Index(value = ["goalId", "label"], unique = true)]
)
data class RecoveryHelpfulActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val goalId: Int,
    val label: String,
    val enabled: Boolean,
    val sortOrder: Int
)

@Entity(
    tableName = "recovery_support_contacts",
    foreignKeys = [ForeignKey(
        entity = RecoveryGoalEntity::class,
        parentColumns = ["id"], childColumns = ["goalId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("goalId")]
)
data class RecoverySupportContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val goalId: Int,
    val name: String,
    val description: String?,
    val phone: String,
    val preferredAction: String
)

@Entity(
    tableName = "recovery_milestones",
    foreignKeys = [ForeignKey(
        entity = RecoveryGoalEntity::class,
        parentColumns = ["id"], childColumns = ["goalId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("goalId"), Index(value = ["goalId", "kind", "thresholdDays"], unique = true)]
)
data class RecoveryMilestoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val goalId: Int,
    val label: String,
    val thresholdDays: Int?,
    val kind: String,
    val enabled: Boolean,
    val achievedAtEpochMillis: Long?
)

@Entity(
    tableName = "recovery_reminders",
    foreignKeys = [ForeignKey(
        entity = RecoveryGoalEntity::class,
        parentColumns = ["id"], childColumns = ["goalId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("goalId"), Index(value = ["goalId", "type"], unique = true), Index("enabled")]
)
data class RecoveryReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val goalId: Int,
    val type: String,
    val timeMinutes: Int,
    val enabled: Boolean,
    val snoozeMinutes: Int,
    val updatedAtEpochMillis: Long
)
