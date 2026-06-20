package com.luistureo.voicereminderapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.luistureo.voicereminderapp.domain.model.ReminderSource
import com.luistureo.voicereminderapp.domain.model.ReminderType

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val detail: String,
    val scheduledAtEpochMillis: Long,
    val isCompleted: Boolean = false,
    val type: String = ReminderType.DEFAULT.name,
    val isUrgent: Boolean = false,
    val source: String = ReminderSource.MANUAL.name,
    val recurrenceUnit: String? = null,
    val recurrenceInterval: Int = 1,
    val recurrenceWeekdays: String = "",
    val isRecurringActive: Boolean = true,
    val nextTriggerAtEpochMillis: Long? = null,
    val lastTriggeredAtEpochMillis: Long? = null,
    val activeAlertAtEpochMillis: Long? = null,
    val activeAlertRepeatCount: Int = 0,
    val nextUrgentRepeatAtEpochMillis: Long? = null,
    val googleCalendarEventId: String? = null,
    val googleCalendarSyncState: String = "PENDING",
    val googleCalendarLastSyncAtEpochMillis: Long? = null,
    val microsoftCalendarLastSyncAtEpochMillis: Long? = null,
    val microsoftCalendarEventId: String? = null,
    val externalIdsByProvider: String = "",
    val originProvider: String = "APP",
    val syncedProviders: String = "",
    val providerSyncStates: String = "",
    val syncedFingerprintsByProvider: String = "",
    val pendingCreateProviders: String = "",
    val pendingUpdateProviders: String = "",
    val pendingDeleteProviders: String = "",
    val meetingUrl: String? = null,
    val meetingProvider: String? = null,
    val isOnlineMeeting: Boolean = false,
    val meetingUrlsByProvider: String = "",
    val isSuspended: Boolean = false,
    val suspendedOccurrenceAtEpochMillis: Long? = null,
    val lastEditedSource: String = "APP",
    val externalEditNote: String? = null,
    val isAllDay: Boolean = false,
    val hiddenFromApp: Boolean = false
)
