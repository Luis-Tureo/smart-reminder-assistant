package com.luistureo.voicereminderapp.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "routines",
    indices = [Index("period"), Index("enabled")]
)
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val description: String,
    val category: String,
    val icon: String,
    val color: Int,
    val enabled: Boolean,
    val period: String,
    val startTimeMinutes: Int?,
    val deadlineTimeMinutes: Int?,
    val assistantMode: String,
    val voiceEnabled: Boolean,
    val motivationBubbleEnabled: Boolean,
    val motivationScheduleMinutes: Int?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

@Entity(
    tableName = "routine_tasks",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("routineId"), Index(value = ["routineId", "orderPriority"], unique = true)]
)
data class RoutineTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val routineId: Int,
    val title: String,
    val description: String?,
    val orderPriority: Int,
    val completed: Boolean,
    val completedOnEpochDay: Long?,
    val optionalTimeMinutes: Int?,
    val estimatedDurationMinutes: Int?,
    val notes: String?
)

@Entity(
    tableName = "routine_daily_executions",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("routineId"), Index(value = ["routineId", "dateEpochDay"], unique = true)]
)
data class RoutineDailyExecutionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val dateEpochDay: Long,
    val routineId: Int,
    val state: String,
    val updatedAtEpochMillis: Long,
    val assistantGuidanceMode: String? = null
)

@Entity(
    tableName = "routine_history",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("routineId"), Index(value = ["routineId", "dateEpochDay"], unique = true)]
)
data class RoutineHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val dateEpochDay: Long,
    val routineId: Int,
    val completedTasks: Int,
    val totalTasks: Int,
    val completionPercentage: Double,
    val finalState: String,
    val assistantGuidanceMode: String? = null,
    val periodAtExecution: String? = null,
    val routineNameAtExecution: String? = null,
    val pendingTaskTitles: String = "",
    val completedAtEpochMillis: Long? = null
)

@Entity(tableName = "routine_templates")
data class RoutineTemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val description: String,
    val benefitsExplanation: String,
    val period: String = "MORNING",
    val estimatedTotalDurationMinutes: Int = 0,
    val icon: String? = null,
    val color: Int? = null,
    val category: String = "Organización",
    val editable: Boolean = true,
    val builtIn: Boolean = true,
    val builtInKey: String? = null
)

@Entity(
    tableName = "routine_template_tasks",
    foreignKeys = [
        ForeignKey(
            entity = RoutineTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("templateId"), Index(value = ["templateId", "orderPriority"], unique = true)]
)
data class RoutineTemplateTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val templateId: Int,
    val title: String,
    val description: String?,
    val orderPriority: Int,
    val estimatedDurationMinutes: Int?
)

@Entity(
    tableName = "routine_suggestions",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("routineId"), Index(value = ["routineId", "type", "createdAtEpochDay"])]
)
data class RoutineSuggestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val routineId: Int,
    val type: String,
    val message: String,
    val primaryAction: String,
    val secondaryAction: String,
    val createdAtEpochDay: Long,
    val dismissedAtEpochDay: Long?,
    val active: Boolean
)
