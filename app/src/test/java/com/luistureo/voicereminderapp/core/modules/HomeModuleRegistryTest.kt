package com.luistureo.voicereminderapp.core.modules

import com.luistureo.voicereminderapp.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeModuleRegistryTest {

    private val canonicalIds = listOf(
        HomeModuleRegistry.CALENDAR,
        HomeModuleRegistry.LOANS,
        HomeModuleRegistry.ROUTINES,
        HomeModuleRegistry.NUTRITION,
        HomeModuleRegistry.RECOVERY,
        HomeModuleRegistry.QUICK_NOTES
    )

    @Test
    fun registryContainsExactlyTheSixStableAvailableModuleIds() {
        assertEquals(6, HomeModuleRegistry.modules.size)
        assertEquals(canonicalIds, HomeModuleRegistry.modules.map(HomeModuleDefinition::id))
        assertEquals(canonicalIds.toSet(), HomeModuleRegistry.knownIds)
        assertTrue(HomeModuleRegistry.modules.all(HomeModuleDefinition::isAvailable))
        assertEquals(6, HomeModuleRegistry.modules.map(HomeModuleDefinition::homeCardId).distinct().size)
        assertEquals(
            6,
            HomeModuleRegistry.modules.map(HomeModuleDefinition::destinationClassName).distinct().size
        )
    }

    @Test
    fun registryKeepsCanonicalOrderIndependentFromSelectionOrder() {
        assertEquals((0..5).toList(), HomeModuleRegistry.modules.map(HomeModuleDefinition::displayOrder))

        val selected = HomeModuleRegistry.selectedModules(
            listOf(
                HomeModuleRegistry.QUICK_NOTES,
                HomeModuleRegistry.NUTRITION,
                HomeModuleRegistry.CALENDAR
            )
        )

        assertEquals(
            listOf(
                HomeModuleRegistry.CALENDAR,
                HomeModuleRegistry.NUTRITION,
                HomeModuleRegistry.QUICK_NOTES
            ),
            selected.map(HomeModuleDefinition::id)
        )
    }

    @Test
    fun sanitizeIdsRemovesUnknownDuplicatesAndRestoresCanonicalOrder() {
        val sanitized = HomeModuleRegistry.sanitizeIds(
            listOf(
                "unknown_module",
                HomeModuleRegistry.RECOVERY,
                HomeModuleRegistry.CALENDAR,
                HomeModuleRegistry.RECOVERY,
                "",
                HomeModuleRegistry.LOANS
            )
        )

        assertEquals(
            linkedSetOf(
                HomeModuleRegistry.CALENDAR,
                HomeModuleRegistry.LOANS,
                HomeModuleRegistry.RECOVERY
            ),
            sanitized
        )
        assertFalse("unknown_module" in sanitized)
        assertFalse("" in sanitized)
    }

    @Test
    fun selectedModulesExposeSelectedCardsAndExcludeUnselectedCards() {
        val selected = HomeModuleRegistry.selectedModules(
            setOf(
                HomeModuleRegistry.LOANS,
                HomeModuleRegistry.QUICK_NOTES,
                "not_registered"
            )
        )

        assertEquals(
            listOf(HomeModuleRegistry.LOANS, HomeModuleRegistry.QUICK_NOTES),
            selected.map(HomeModuleDefinition::id)
        )
        assertFalse(selected.any { it.id == HomeModuleRegistry.CALENDAR })
        assertFalse(selected.any { it.id == HomeModuleRegistry.ROUTINES })
        assertFalse(selected.any { it.id == HomeModuleRegistry.NUTRITION })
        assertFalse(selected.any { it.id == HomeModuleRegistry.RECOVERY })
    }

    @Test
    fun assistantAndGeneralHomeConfigurationAreOutsideSelectableRegistry() {
        val selectableCardIds = HomeModuleRegistry.modules
            .map(HomeModuleDefinition::homeCardId)
            .toSet()

        assertFalse(R.id.cardAssistantReminder in selectableCardIds)
        assertFalse(R.id.btnReselectModules in selectableCardIds)
        assertFalse(R.id.cardNextDayPreview in selectableCardIds)
        assertFalse(R.id.homeEmptyStateCard in selectableCardIds)
    }
}
