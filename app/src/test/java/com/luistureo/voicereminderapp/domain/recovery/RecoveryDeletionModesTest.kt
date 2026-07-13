package com.luistureo.voicereminderapp.domain.recovery

import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryCategory
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryCheckIn
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryCheckInStatus
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryDeletionMode
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryGoal
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryGoalStatus
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryHelpfulAction
import com.luistureo.voicereminderapp.domain.recovery.model.RecoverySupportContact
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryTrigger
import com.luistureo.voicereminderapp.domain.recovery.usecase.DeleteRecoveryGoalUseCase
import com.luistureo.voicereminderapp.domain.recovery.usecase.SaveRecoveryGoalUseCase
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryDeletionModesTest {
    @Test
    fun archiveKeepsGoalAndCompleteHistoryButHidesItFromVisibleGoals() = runBlocking {
        val fixture = fixture()

        DeleteRecoveryGoalUseCase(fixture.repository)(fixture.goal, RecoveryDeletionMode.ARCHIVE)

        assertEquals(RecoveryGoalStatus.ARCHIVED, fixture.repository.goals.single().status)
        assertEquals(1, fixture.repository.checkIns.size)
        assertEquals("Nota sensible", fixture.repository.checkIns.single().note)
        assertTrue(fixture.repository.getGoals().isEmpty())
    }

    @Test
    fun deleteKeepingHistoryRemovesProfileAndChildrenAndAnonymizesCheckIn() = runBlocking {
        val fixture = fixture()

        DeleteRecoveryGoalUseCase(fixture.repository)(
            fixture.goal,
            RecoveryDeletionMode.DELETE_KEEP_ANONYMOUS_HISTORY
        )

        assertTrue(fixture.repository.goals.isEmpty())
        assertTrue(fixture.repository.triggers.isEmpty())
        assertTrue(fixture.repository.helpfulActions.isEmpty())
        assertTrue(fixture.repository.contacts.isEmpty())
        assertTrue(fixture.repository.reminders.isEmpty())
        assertTrue(fixture.repository.milestones.isEmpty())
        val history = fixture.repository.checkIns.single()
        assertNull(history.goalId)
        assertNull(history.cravingIntensity)
        assertNull(history.trigger)
        assertNull(history.helpfulAction)
        assertNull(history.note)
        assertEquals(RecoveryCheckInStatus.ACHIEVED, history.status)
        assertEquals(LocalDate.of(2026, 7, 12), history.date)
    }

    @Test
    fun deleteAllRemovesGoalChildrenAndEveryHistoricalCheckIn() = runBlocking {
        val fixture = fixture()

        DeleteRecoveryGoalUseCase(fixture.repository)(fixture.goal, RecoveryDeletionMode.DELETE_ALL)

        assertTrue(fixture.repository.goals.isEmpty())
        assertTrue(fixture.repository.checkIns.isEmpty())
        assertTrue(fixture.repository.triggers.isEmpty())
        assertTrue(fixture.repository.helpfulActions.isEmpty())
        assertTrue(fixture.repository.contacts.isEmpty())
        assertTrue(fixture.repository.milestones.isEmpty())
    }

    private suspend fun fixture(): Fixture {
        val repository = FakeRecoveryRepository()
        val id = SaveRecoveryGoalUseCase(repository)(
            RecoveryGoal(
                title = "Meta privada",
                category = RecoveryCategory.NICOTINE,
                historyKey = "history-delete"
            )
        )
        val goal = repository.getGoal(id)!!
        repository.saveCheckIn(
            RecoveryCheckIn(
                goalId = id,
                goalHistoryKey = goal.historyKey,
                date = LocalDate.of(2026, 7, 12),
                status = RecoveryCheckInStatus.ACHIEVED,
                cravingIntensity = 7,
                trigger = "Estrés",
                helpfulAction = "Caminar",
                note = "Nota sensible"
            )
        )
        repository.saveTrigger(RecoveryTrigger(goalId = id, label = "Estrés"))
        repository.saveHelpfulAction(RecoveryHelpfulAction(goalId = id, label = "Caminar"))
        repository.saveSupportContact(
            RecoverySupportContact(goalId = id, name = "Apoyo", phone = "+56900000000")
        )
        return Fixture(repository, goal)
    }

    private data class Fixture(
        val repository: FakeRecoveryRepository,
        val goal: RecoveryGoal
    )
}
