package com.luistureo.voicereminderapp.presentation.state

import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import com.luistureo.voicereminderapp.domain.model.ReminderRecurrence
import com.luistureo.voicereminderapp.domain.model.ReminderSource

// Estado estructurado del editor manual reutilizable por camara.
data class ReminderFormState(
    val reminderId: Int = 0,
    val title: String = "",
    val detail: String = "",
    val date: String = "",
    val time: String = "",
    val isUrgent: Boolean = false,
    val source: ReminderSource = ReminderSource.MANUAL,
    val recurrence: ReminderRecurrence? = null,
    val syncTargetProviders: Set<CalendarProvider> = emptySet()
)
