package com.luistureo.voicereminderapp.data.repository

import com.luistureo.voicereminderapp.data.local.dao.ReminderDao
import com.luistureo.voicereminderapp.data.mapper.toDomain
import com.luistureo.voicereminderapp.data.mapper.toEntity
import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.repository.ReminderRepository

class ReminderRepositoryImpl(
    private val reminderDao: ReminderDao
) : ReminderRepository {

    override suspend fun insertReminder(reminder: Reminder) {
        reminderDao.insertReminder(reminder.toEntity())
    }

    override suspend fun getAllReminders(): List<Reminder> {
        return reminderDao.getAllReminders().map { it.toDomain() }
    }

    override suspend fun deleteReminder(reminder: Reminder) {
        reminderDao.deleteReminder(reminder.toEntity())
    }

    override suspend fun updateReminder(reminder: Reminder) {
        reminderDao.updateReminder(reminder.toEntity())
    }
}