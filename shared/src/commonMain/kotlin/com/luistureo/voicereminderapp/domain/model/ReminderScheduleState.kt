package com.luistureo.voicereminderapp.domain.model

// Estado persistido de programacion para alarmas y alertas urgentes.
data class ReminderScheduleState(
    val nextTriggerAtEpochMillis: Long? = null,
    val lastTriggeredAtEpochMillis: Long? = null,
    val activeAlertAtEpochMillis: Long? = null,
    val activeAlertRepeatCount: Int = 0,
    val nextUrgentRepeatAtEpochMillis: Long? = null
)
