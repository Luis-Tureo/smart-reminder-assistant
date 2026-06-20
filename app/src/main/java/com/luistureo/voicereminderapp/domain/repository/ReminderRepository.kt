package com.luistureo.voicereminderapp.domain.repository

import com.luistureo.voicereminderapp.domain.model.Reminder

interface ReminderRepository {

    suspend fun insertReminder(reminder: Reminder): Int

    suspend fun getAllReminders(): List<Reminder>

    suspend fun getAllRemindersIncludingHidden(): List<Reminder> = getAllReminders()

    suspend fun getReminderById(reminderId: Int): Reminder?

    suspend fun deleteReminder(reminder: Reminder)

    suspend fun updateReminder(reminder: Reminder)
}
