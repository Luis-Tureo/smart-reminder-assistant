package com.luistureo.voicereminderapp.data.mapper

import com.luistureo.voicereminderapp.data.local.entity.ReminderEntity
import com.luistureo.voicereminderapp.domain.model.Reminder

// Convierte Entity a Domain
fun ReminderEntity.toDomain(): Reminder {
    return Reminder(
        id = id,
        text = text,
        date = date,
        time = time,
        isCompleted = isCompleted
    )
}

// Convierte Domain a Entity
fun Reminder.toEntity(): ReminderEntity {
    return ReminderEntity(
        id = id,
        text = text,
        date = date,
        time = time,
        isCompleted = isCompleted
    )
}