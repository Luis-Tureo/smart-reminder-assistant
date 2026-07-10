package com.luistureo.voicereminderapp.domain.routine

import com.luistureo.voicereminderapp.domain.routine.factory.DefaultRoutineFactory
import com.luistureo.voicereminderapp.domain.routine.model.RoutinePeriod
import com.luistureo.voicereminderapp.domain.routine.usecase.InitializeDailyRoutinesUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultRoutineFactoryTest {
    @Test
    fun createsMorningAfternoonAndNightRoutinesWithOrderedTasks() {
        val drafts = DefaultRoutineFactory.create(nowEpochMillis = 123L)

        assertEquals(
            listOf(RoutinePeriod.MORNING, RoutinePeriod.AFTERNOON, RoutinePeriod.NIGHT),
            drafts.map { it.routine.period }
        )
        assertTrue(drafts.all { it.routine.enabled })
        assertTrue(drafts.all { draft ->
            draft.tasks.map { it.orderPriority } == draft.tasks.indices.toList()
        })
    }

    @Test
    fun initializationIsIdempotent() = runBlocking {
        val repository = FakeRoutineRepository()
        val useCase = InitializeDailyRoutinesUseCase(repository) { 123L }

        assertTrue(useCase())
        assertFalse(useCase())
        assertEquals(3, repository.routines.size)
        assertEquals(12, repository.templates.size)
    }
}
