package com.luistureo.voicereminderapp.domain.recovery

import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryCategory
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryCheckIn
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryCheckInStatus
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryContactAction
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryGoal
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryHelpfulAction
import com.luistureo.voicereminderapp.domain.recovery.model.RecoverySupportContact
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryTrigger
import com.luistureo.voicereminderapp.domain.recovery.usecase.RecordRecoveryCheckInUseCase
import com.luistureo.voicereminderapp.domain.recovery.usecase.SaveRecoveryGoalUseCase
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryUseCasesTest {
    @Test
    fun createsGoalWithPrivateLocalKeyAndDefaultMilestones() = runBlocking {
        val repository = FakeRecoveryRepository()

        val id = SaveRecoveryGoalUseCase(repository)(
            RecoveryGoal(title = "Mi meta privada", category = RecoveryCategory.OTHER)
        )

        assertEquals(1, id)
        assertEquals("Mi meta privada", repository.goals.single().title)
        assertTrue(repository.goals.single().historyKey.isNotBlank())
        assertEquals(6, repository.milestones.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsGoalWithoutTitle() {
        runBlocking {
            SaveRecoveryGoalUseCase(FakeRecoveryRepository())(
                RecoveryGoal(title = "  ", category = RecoveryCategory.OTHER)
            )
        }
    }

    @Test
    fun savesFastCheckInWithEveryOptionalFieldEmpty() = runBlocking {
        val repository = repositoryWithGoal()
        val goal = repository.goals.single()

        RecordRecoveryCheckInUseCase(repository)(
            RecoveryCheckIn(
                goalId = goal.id,
                goalHistoryKey = goal.historyKey,
                date = LocalDate.of(2026, 7, 12),
                status = RecoveryCheckInStatus.ACHIEVED
            )
        )

        val saved = repository.checkIns.single()
        assertNull(saved.cravingIntensity)
        assertNull(saved.trigger)
        assertNull(saved.helpfulAction)
        assertNull(saved.note)
        assertFalse(saved.reducedFrequency)
    }

    @Test
    fun updatesExistingCheckInForSameGoalAndDayWithoutDuplicatingIt() = runBlocking {
        val repository = repositoryWithGoal()
        val goal = repository.goals.single()
        val date = LocalDate.of(2026, 7, 12)
        val useCase = RecordRecoveryCheckInUseCase(repository)

        useCase(RecoveryCheckIn(goalId = goal.id, goalHistoryKey = goal.historyKey,
            date = date, status = RecoveryCheckInStatus.ACHIEVED))
        val originalId = repository.checkIns.single().id
        useCase(RecoveryCheckIn(goalId = goal.id, goalHistoryKey = goal.historyKey,
            date = date, status = RecoveryCheckInStatus.DIFFICULTY_MANAGED,
            cravingIntensity = 6, trigger = "Estrés", helpfulAction = "Caminar"))

        assertEquals(1, repository.checkIns.size)
        assertEquals(originalId, repository.checkIns.single().id)
        assertEquals(RecoveryCheckInStatus.DIFFICULTY_MANAGED, repository.checkIns.single().status)
        assertEquals(6, repository.checkIns.single().cravingIntensity)
    }

    @Test
    fun lapsePreservesEveryPreviousSuccessfulDay() = runBlocking {
        val repository = repositoryWithGoal()
        val goal = repository.goals.single()
        val useCase = RecordRecoveryCheckInUseCase(repository)
        useCase(checkIn(goal, LocalDate.of(2026, 7, 10), RecoveryCheckInStatus.ACHIEVED))
        useCase(checkIn(goal, LocalDate.of(2026, 7, 11), RecoveryCheckInStatus.ACHIEVED))
        useCase(checkIn(goal, LocalDate.of(2026, 7, 12), RecoveryCheckInStatus.LAPSE)
            .copy(resetsStreak = true, note = "Puedo continuar"))

        assertEquals(3, repository.checkIns.size)
        assertEquals(2, repository.checkIns.count { it.status == RecoveryCheckInStatus.ACHIEVED })
        assertTrue(repository.checkIns.any { it.status == RecoveryCheckInStatus.LAPSE })
    }

    @Test
    fun storesAndRemovesUserTriggersHelpfulActionsAndSupportContacts() = runBlocking {
        val repository = repositoryWithGoal()
        val goalId = repository.goals.single().id
        val triggerId = repository.saveTrigger(RecoveryTrigger(goalId = goalId, label = "Estrés"))
        val actionId = repository.saveHelpfulAction(
            RecoveryHelpfulAction(goalId = goalId, label = "Salir a caminar")
        )
        val contactId = repository.saveSupportContact(
            RecoverySupportContact(
                goalId = goalId,
                name = "Persona de confianza",
                phone = "+56900000000",
                preferredAction = RecoveryContactAction.SMS
            )
        )

        assertEquals("Estrés", repository.getTriggers(goalId).single().label)
        assertEquals("Salir a caminar", repository.getHelpfulActions(goalId).single().label)
        assertEquals(RecoveryContactAction.SMS, repository.getSupportContacts(goalId).single().preferredAction)

        repository.deleteTrigger(repository.triggers.single { it.id == triggerId })
        repository.deleteHelpfulAction(repository.helpfulActions.single { it.id == actionId })
        repository.deleteSupportContact(repository.contacts.single { it.id == contactId })
        assertTrue(repository.getTriggers(goalId).isEmpty())
        assertTrue(repository.getHelpfulActions(goalId).isEmpty())
        assertTrue(repository.getSupportContacts(goalId).isEmpty())
    }

    private suspend fun repositoryWithGoal(): FakeRecoveryRepository {
        val repository = FakeRecoveryRepository()
        SaveRecoveryGoalUseCase(repository)(goal())
        return repository
    }

    private fun goal() = RecoveryGoal(
        title = "Meta",
        category = RecoveryCategory.SCREEN_USE,
        historyKey = "history-1"
    )

    private fun checkIn(goal: RecoveryGoal, date: LocalDate, status: RecoveryCheckInStatus) =
        RecoveryCheckIn(
            goalId = goal.id,
            goalHistoryKey = goal.historyKey,
            date = date,
            status = status
        )
}
