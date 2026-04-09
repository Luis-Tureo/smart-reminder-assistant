package com.luistureo.voicereminderapp.data.mapper

import com.luistureo.voicereminderapp.core.utils.ReminderTypeResolver
import com.luistureo.voicereminderapp.data.local.entity.ReminderEntity
import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.model.ReminderType

// Convierte Entity a Domain
fun ReminderEntity.toDomain(): Reminder {
    val storedType = ReminderType.entries.firstOrNull { it.name == type } ?: ReminderType.DEFAULT

    return Reminder(
        id = id,
        title = title,
        detail = detail,
        date = date,
        time = time,
        isCompleted = isCompleted,
        type = when (storedType) {
            ReminderType.DEFAULT -> ReminderTypeResolver.resolve(title, detail)
            else -> storedType
        }
    )
}

// Convierte Domain a Entity
fun Reminder.toEntity(): ReminderEntity {
    return ReminderEntity(
        id = id,
        title = title,
        detail = detail,
        date = date,
        time = time,
        isCompleted = isCompleted,
        type = type.name
    )
}
