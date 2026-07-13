package com.luistureo.voicereminderapp.core.modules

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModuleSelectionStoreInstrumentedTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        clearPreferences()
    }

    @After
    fun tearDown() {
        clearPreferences()
    }

    @Test
    fun selectionPersistsAcrossStoreInstancesAndUnknownIdsAreIgnored() {
        val firstStore = ModuleSelectionStore(context)
        assertFalse(firstStore.isSelectionCompleted())

        assertTrue(
            firstStore.saveSelection(
                setOf(
                    HomeModuleRegistry.QUICK_NOTES,
                    "unknown_module",
                    HomeModuleRegistry.CALENDAR
                )
            )
        )

        val restoredStore = ModuleSelectionStore(context)
        assertTrue(restoredStore.isSelectionCompleted())
        assertEquals(
            linkedSetOf(HomeModuleRegistry.CALENDAR, HomeModuleRegistry.QUICK_NOTES),
            restoredStore.selectedModuleIds()
        )
    }

    @Test
    fun emptySelectionIsRejectedWithoutCompletingOnboarding() {
        val store = ModuleSelectionStore(context)

        assertFalse(store.saveSelection(emptySet()))
        assertFalse(store.isSelectionCompleted())
        assertTrue(store.selectedModuleIds().isEmpty())
    }

    private fun clearPreferences() {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private companion object {
        const val PREFERENCES_NAME = "home_module_selection"
    }
}
