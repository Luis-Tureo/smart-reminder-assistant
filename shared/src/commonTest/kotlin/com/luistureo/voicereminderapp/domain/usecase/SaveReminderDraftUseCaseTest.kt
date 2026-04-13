package com.luistureo.voicereminderapp.domain.usecase

import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.model.ReminderDraft
import com.luistureo.voicereminderapp.domain.model.ReminderScheduleState
import com.luistureo.voicereminderapp.domain.repository.ReminderRepository
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SaveReminderDraftUseCaseTest {

    @Test
    fun invoke_insertsReminderAndGeneratesFallbackTitle() {
        val repository = FakeReminderRepository()
        val useCase = SaveReminderDraftUseCase(repository)

        val reminder = runSuspend {
            useCase(
                ReminderDraft(
                    text = "Pagar cuentas del mes",
                    date = "05/03/2026",
                    time = "09:07"
                )
            )
        }

        assertEquals(1, reminder.id)
        assertEquals("Pagar cuentas del mes", reminder.title)
        assertEquals("Pagar cuentas del mes", reminder.detail)
        assertTrue(repository.reminders.isNotEmpty())
    }

    @Test
    fun invoke_updatesReminderAndPreservesCompletionState() {
        val repository = FakeReminderRepository(
            mutableListOf(
                Reminder(
                    id = 7,
                    title = "Consulta",
                    detail = "Consulta medica",
                    scheduledAtEpochMillis = 1L,
                    isCompleted = true,
                    scheduleState = ReminderScheduleState()
                )
            )
        )
        val useCase = SaveReminderDraftUseCase(repository)

        val reminder = runSuspend {
            useCase(
                ReminderDraft(
                    reminderId = 7,
                    title = "Consulta anual",
                    text = "Consulta medica anual",
                    date = "05/03/2026",
                    time = "10:30"
                )
            )
        }

        assertEquals(7, reminder.id)
        assertEquals("Consulta anual", reminder.title)
        assertTrue(reminder.isCompleted)
        assertEquals("Consulta medica anual", repository.reminders.first().detail)
    }

    private class FakeReminderRepository(
        val reminders: MutableList<Reminder> = mutableListOf()
    ) : ReminderRepository {

        override suspend fun insertReminder(reminder: Reminder): Int {
            val nextId = (reminders.maxOfOrNull { it.id } ?: 0) + 1
            reminders += reminder.copy(id = nextId)
            return nextId
        }

        override suspend fun getAllReminders(): List<Reminder> {
            return reminders.toList()
        }

        override suspend fun getReminderById(reminderId: Int): Reminder? {
            return reminders.firstOrNull { it.id == reminderId }
        }

        override suspend fun deleteReminder(reminder: Reminder) {
            reminders.removeAll { it.id == reminder.id }
        }

        override suspend fun updateReminder(reminder: Reminder) {
            reminders.replaceAll { existingReminder ->
                if (existingReminder.id == reminder.id) reminder else existingReminder
            }
        }
    }
}

private fun <T> runSuspend(block: suspend () -> T): T {
    var completionResult: Result<T>? = null

    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext

        override fun resumeWith(result: Result<T>) {
            completionResult = result
        }
    })

    return completionResult!!.getOrThrow()
}
