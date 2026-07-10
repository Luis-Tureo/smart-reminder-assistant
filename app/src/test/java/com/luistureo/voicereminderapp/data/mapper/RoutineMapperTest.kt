package com.luistureo.voicereminderapp.data.mapper

import com.luistureo.voicereminderapp.data.local.entity.RoutineTemplateEntity
import com.luistureo.voicereminderapp.data.local.entity.RoutineTemplateTaskEntity
import com.luistureo.voicereminderapp.domain.routine.model.Routine
import com.luistureo.voicereminderapp.domain.routine.model.RoutineAssistantMode
import com.luistureo.voicereminderapp.domain.routine.model.RoutinePeriod
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class RoutineMapperTest {
    @Test
    fun routineEntityRoundTripPreservesDomainFields() {
        val routine = Routine(
            id = 7,
            name = "Mañana",
            description = "Inicio",
            category = "Salud",
            icon = "water_drop",
            color = 0xFF123456.toInt(),
            enabled = true,
            period = RoutinePeriod.MORNING,
            startTime = LocalTime.of(7, 30),
            deadlineTime = LocalTime.of(9, 0),
            assistantMode = RoutineAssistantMode.STEP_BY_STEP_GUIDE,
            voiceEnabled = true,
            motivationBubbleEnabled = false,
            motivationSchedule = LocalTime.of(7, 15),
            createdAtEpochMillis = 10L,
            updatedAtEpochMillis = 20L
        )

        assertEquals(routine, routine.toEntity().toDomain())
    }

    @Test
    fun templateMappingOrdersSuggestedTasks() {
        val entity = RoutineTemplateEntity(4, "Plantilla", "Descripción", "Beneficios")
        val tasks = listOf(
            RoutineTemplateTaskEntity(2, 4, "Segunda", null, 1, null),
            RoutineTemplateTaskEntity(1, 4, "Primera", null, 0, 5)
        )

        val mapped = entity.toDomain(tasks)

        assertEquals(listOf("Primera", "Segunda"), mapped.suggestedTasks.map { it.title })
        assertEquals("Beneficios", mapped.benefitsExplanation)
    }
}
