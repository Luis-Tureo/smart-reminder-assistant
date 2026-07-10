package com.luistureo.voicereminderapp.domain.usecase

import com.luistureo.voicereminderapp.core.reminder.ReminderTemporalValidationPolicy
import com.luistureo.voicereminderapp.core.utils.DateTimeFormatter
import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.model.ReminderDraft
import com.luistureo.voicereminderapp.domain.model.ReminderSource
import com.luistureo.voicereminderapp.domain.repository.ReminderRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPastScheduleUseCaseTest {

    private val now = requireNotNull(
        DateTimeFormatter.parseDateTimeToEpochMillis("10/07/2026", "12:00")
    )

    @Test
    fun manualReminderFormBlocksPastDateTime() = runBlocking {
        val repository = FakeReminderRepository()
        val useCase = SaveReminderDraftUseCase(repository, currentTimeMillis = { now })

        val error = runCatching {
            useCase(draft(source = ReminderSource.MANUAL, date = "09/07/2026", time = "09:00"))
        }.exceptionOrNull()

        assertEquals(ReminderTemporalValidationPolicy.PAST_SCHEDULE_MESSAGE, error?.message)
        assertTrue(repository.savedReminders.isEmpty())
    }

    @Test
    fun assistantCreatedReminderBlocksPastDateTime() = runBlocking {
        val repository = FakeReminderRepository()
        val useCase = SaveReminderDraftUseCase(repository, currentTimeMillis = { now })

        val error = runCatching {
            useCase(draft(source = ReminderSource.VOICE, date = "10/07/2026", time = "11:30"))
        }.exceptionOrNull()

        assertEquals(ReminderTemporalValidationPolicy.PAST_SCHEDULE_MESSAGE, error?.message)
        assertTrue(repository.savedReminders.isEmpty())
    }

    @Test
    fun ocrCreatedReminderBlocksPastDateTime() = runBlocking {
        val repository = FakeReminderRepository()
        val useCase = SaveReminderDraftUseCase(repository, currentTimeMillis = { now })

        val error = runCatching {
            useCase(draft(source = ReminderSource.CAMERA, date = "10/07/2026", time = "08:15"))
        }.exceptionOrNull()

        assertEquals(ReminderTemporalValidationPolicy.PAST_SCHEDULE_MESSAGE, error?.message)
        assertTrue(repository.savedReminders.isEmpty())
    }

    @Test
    fun pastedTextReminderBlocksPastDateTime() = runBlocking {
        val repository = FakeReminderRepository()
        val useCase = SaveReminderDraftUseCase(repository, currentTimeMillis = { now })

        val error = runCatching {
            useCase(draft(source = ReminderSource.MANUAL, date = "10/07/2026", time = "10:45"))
        }.exceptionOrNull()

        assertEquals(ReminderTemporalValidationPolicy.PAST_SCHEDULE_MESSAGE, error?.message)
        assertTrue(repository.savedReminders.isEmpty())
    }

    @Test
    fun futureDateTimeCreationIsSaved() = runBlocking {
        val repository = FakeReminderRepository()
        val useCase = SaveReminderDraftUseCase(repository, currentTimeMillis = { now })

        val savedReminder = useCase(
            draft(source = ReminderSource.MANUAL, date = "11/07/2026", time = "09:00")
        )

        assertNotNull(savedReminder)
        assertEquals(1, repository.savedReminders.size)
    }

    @Test
    fun editingPendingReminderToPastDateTimeIsBlocked() = runBlocking {
        val repository = FakeReminderRepository()
        val existingReminder = reminder(
            id = 7,
            scheduledAtEpochMillis = requireNotNull(
                DateTimeFormatter.parseDateTimeToEpochMillis("11/07/2026", "09:00")
            ),
            isCompleted = false
        )
        repository.seed(existingReminder)
        val useCase = UpdateReminderUseCase(repository, currentTimeMillis = { now })

        val error = runCatching {
            useCase(
                existingReminder.copy(
                    scheduledAtEpochMillis = requireNotNull(
                        DateTimeFormatter.parseDateTimeToEpochMillis("10/07/2026", "09:00")
                    )
                )
            )
        }.exceptionOrNull()

        assertEquals(ReminderTemporalValidationPolicy.PAST_SCHEDULE_MESSAGE, error?.message)
        assertEquals(existingReminder.scheduledAtEpochMillis, repository.savedReminders.first().scheduledAtEpochMillis)
    }

    @Test
    fun completedReminderCanRemainInPastWhenEdited() = runBlocking {
        val repository = FakeReminderRepository()
        val existingReminder = reminder(
            id = 8,
            scheduledAtEpochMillis = requireNotNull(
                DateTimeFormatter.parseDateTimeToEpochMillis("09/07/2026", "09:00")
            ),
            isCompleted = true
        )
        repository.seed(existingReminder)
        val useCase = UpdateReminderUseCase(repository, currentTimeMillis = { now })

        useCase(existingReminder.copy(detail = "detalle actualizado"))

        assertEquals("detalle actualizado", repository.savedReminders.first().detail)
    }

    private fun draft(
        source: ReminderSource,
        date: String,
        time: String
    ): ReminderDraft {
        return ReminderDraft(
            title = "Comprar pan",
            text = "Comprar pan",
            date = date,
            time = time,
            source = source
        )
    }

    private fun reminder(
        id: Int,
        scheduledAtEpochMillis: Long,
        isCompleted: Boolean
    ): Reminder {
        return Reminder(
            id = id,
            title = "Comprar pan",
            detail = "Comprar pan",
            scheduledAtEpochMillis = scheduledAtEpochMillis,
            isCompleted = isCompleted
        )
    }

    private class FakeReminderRepository : ReminderRepository {
        val savedReminders = mutableListOf<Reminder>()
        private var nextId = 1

        fun seed(reminder: Reminder) {
            savedReminders += reminder
            nextId = maxOf(nextId, reminder.id + 1)
        }

        override suspend fun insertReminder(reminder: Reminder): Int {
            val id = nextId++
            savedReminders += reminder.copy(id = id)
            return id
        }

        override suspend fun getAllReminders(): List<Reminder> = savedReminders.toList()

        override suspend fun getReminderById(reminderId: Int): Reminder? {
            return savedReminders.firstOrNull { it.id == reminderId }
        }

        override suspend fun deleteReminder(reminder: Reminder) {
            savedReminders.removeAll { it.id == reminder.id }
        }

        override suspend fun updateReminder(reminder: Reminder) {
            val index = savedReminders.indexOfFirst { it.id == reminder.id }
            if (index >= 0) {
                savedReminders[index] = reminder
            }
        }
    }
}
