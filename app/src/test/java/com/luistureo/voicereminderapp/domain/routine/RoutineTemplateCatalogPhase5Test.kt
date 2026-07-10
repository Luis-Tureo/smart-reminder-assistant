package com.luistureo.voicereminderapp.domain.routine

import com.luistureo.voicereminderapp.domain.routine.factory.DefaultRoutineTemplateFactory
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTemplate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineTemplateCatalogPhase5Test {
    @Test fun builtInCatalogContainsTwelveCompleteRespectfulEditablePreviews() {
        val templates = DefaultRoutineTemplateFactory.create()
        assertEquals(12, templates.size)
        assertTrue(templates.all { it.description.isNotBlank() && it.benefitsExplanation.isNotBlank() })
        assertTrue(templates.all { it.estimatedTotalDurationMinutes > 0 && it.suggestedTasks.isNotEmpty() })
        assertTrue(templates.all { it.builtIn && !it.editable && it.builtInKey != null })
        val text = templates.joinToString { it.description + it.benefitsExplanation }.lowercase()
        assertFalse(text.contains("cura"))
        assertFalse(text.contains("diagnostica"))
    }

    @Test fun personalTemplatesCanBeCreatedDeletedAndBuiltInsCannotBeDeleted() = runBlocking {
        val repository = FakeRoutineRepository()
        repository.restoreBuiltInTemplates(DefaultRoutineTemplateFactory.create())
        val builtInId = repository.templates.first().id
        assertFalse(repository.deletePersonalTemplate(builtInId))
        val personalId = repository.saveTemplate(RoutineTemplate(name = "Personal", description = "Editable",
            benefitsExplanation = "Adáptala", suggestedTasks = emptyList(), builtIn = false, editable = true))
        assertTrue(repository.deletePersonalTemplate(personalId))
    }
}
