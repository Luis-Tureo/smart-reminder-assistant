package com.luistureo.voicereminderapp.presentation.modules

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleSelectionContractTest {

    @Test
    fun firstLaunchSelectionRunsBeforeHomeInflationAndRequiresAValidSelection() {
        val main = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/MainActivity.kt"
        ).readText()
        val onCreate = main.substringAfter("override fun onCreate(savedInstanceState: Bundle?)")
            .substringBefore("override fun onResume()")

        assertTrue(onCreate.contains("!moduleSelectionStore.isSelectionCompleted()"))
        assertTrue(onCreate.contains("moduleSelectionStore.selectedModuleIds().isEmpty()"))
        assertTrue(onCreate.contains("ModuleSelectionActivity.firstLaunchIntent(this)"))
        assertTrue(onCreate.indexOf("ModuleSelectionStore(applicationContext)") < onCreate.indexOf("setContentView"))
        assertTrue(onCreate.indexOf("firstLaunchIntent(this)") < onCreate.indexOf("setContentView"))
        assertTrue(onCreate.indexOf("finish()") < onCreate.indexOf("setContentView"))
        assertTrue(onCreate.indexOf("return") < onCreate.indexOf("setContentView"))
    }

    @Test
    fun reselectLoadsStoredDraftAndOnlyAppliesVisibilityAfterSuccessfulSave() {
        val main = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/MainActivity.kt"
        ).readText()
        val selection = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/modules/" +
                "ModuleSelectionActivity.kt"
        ).readText()
        val resultContract = main.substringAfter("private val moduleSelectionLauncher")
            .substringBefore("private val googleCalendarSignInLauncher")

        assertTrue(main.contains("moduleSelectionLauncher.launch(ModuleSelectionActivity.editIntent(this))"))
        assertTrue(selection.contains("else store.selectedModuleIds()"))
        assertTrue(resultContract.contains("result.resultCode == RESULT_OK"))
        assertTrue(resultContract.contains("applyHomeModuleVisibility()"))
        assertFalse(resultContract.contains("RESULT_CANCELED"))
    }

    @Test
    fun cancelAndBackDiscardDraftWithoutWritingPreferences() {
        val selection = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/modules/" +
                "ModuleSelectionActivity.kt"
        ).readText()
        val headerContract = selection.substringAfter("private fun setupHeader()")
            .substringBefore("private fun setupList")

        assertTrue(headerContract.contains("back.isVisible = !isFirstLaunch"))
        assertTrue(headerContract.contains("back.setOnClickListener { finish() }"))
        assertFalse(headerContract.contains("store.saveSelection"))
        assertEquals(1, selection.countOccurrences("store.saveSelection("))
        assertTrue(selection.contains("setResult(RESULT_OK)"))
        assertFalse(selection.contains("setResult(RESULT_CANCELED)"))
    }

    @Test
    fun persistenceSanitizesAndAtomicallyStoresCompletionWithSelectedIds() {
        val store = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/core/modules/" +
                "ModuleSelectionStore.kt"
        ).readText()
        val saveContract = store.substringAfter("fun saveSelection(moduleIds: Set<String>)")
            .substringBefore("companion object")

        assertTrue(store.contains("?.toSet()"))
        assertTrue(store.contains("HomeModuleRegistry.sanitizeIds(stored)"))
        assertTrue(saveContract.contains("HomeModuleRegistry.sanitizeIds(moduleIds)"))
        assertTrue(saveContract.contains("if (sanitized.isEmpty()) return false"))
        assertTrue(saveContract.contains("putStringSet(KEY_SELECTED_HOME_MODULES, sanitized)"))
        assertTrue(saveContract.contains("putBoolean(KEY_MODULE_SELECTION_COMPLETED, true)"))
        assertTrue(saveContract.contains(".commit()"))
        assertEquals(1, saveContract.countOccurrences("preferences.edit()"))
    }

    @Test
    fun homeFiltersOnlyRegisteredModuleCardsAndKeepsCanonicalLayoutOrder() {
        val main = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/MainActivity.kt"
        ).readText()
        val layout = projectFile("app/src/main/res/layout/activity_main.xml").readText()
        val visibilityContract = main.substringAfter("private fun applyHomeModuleVisibility()")
            .substringBefore("private fun openRegisteredModule")
        val canonicalCardIds = listOf(
            "cardCalendar",
            "cardLoan",
            "cardDailyRoutines",
            "cardNutrition",
            "cardRecovery",
            "cardQuickNotes"
        )

        assertTrue(visibilityContract.contains("HomeModuleRegistry.modules.forEach"))
        assertTrue(
            visibilityContract.contains(
                "findViewById<View>(module.homeCardId).isVisible = module.id in selected"
            )
        )
        listOf(
            "assistantReminderCard",
            "reselectModulesButton",
            "cardAssistantReminder",
            "btnReselectModules",
            "cardNextDayPreview",
            "homeEmptyStateCard"
        ).forEach { excluded ->
            assertFalse("No debe condicionar $excluded", visibilityContract.contains(excluded))
        }

        val positions = canonicalCardIds.map { id ->
            layout.indexOf("@+id/$id").also { position ->
                assertTrue("Falta $id en Home", position >= 0)
            }
        }
        assertEquals(positions.sorted(), positions)
        assertTrue(layout.contains("@+id/cardAssistantReminder"))
        assertTrue(layout.contains("@+id/btnReselectModules"))
    }

    @Test
    fun selectorResourcesExposeExplicitSaveValidationAndCancelControls() {
        val selectionLayout = projectFile(
            "app/src/main/res/layout/activity_module_selection.xml"
        ).readText()
        val itemLayout = projectFile("app/src/main/res/layout/item_module_selection.xml").readText()
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()
        val selectorDeclaration = manifest
            .substringAfter("android:name=\".presentation.modules.ModuleSelectionActivity\"")
            .substringBefore("/>")
        val launcherDeclaration = manifest
            .substringAfter("android:name=\".MainActivity\"")
            .substringBefore("</activity>")

        listOf(
            "btnModuleSelectionBack",
            "recyclerModuleSelection",
            "tvModuleSelectionValidation",
            "btnSaveModuleSelection"
        ).forEach { id -> assertTrue(selectionLayout.contains("@+id/$id")) }
        assertTrue(itemLayout.contains("@+id/checkModuleSelected"))
        assertTrue(selectorDeclaration.contains("android:exported=\"false\""))
        assertFalse(selectorDeclaration.contains("android.intent.action.MAIN"))
        assertFalse(selectorDeclaration.contains("android.intent.category.LAUNCHER"))
        assertTrue(launcherDeclaration.contains("android.intent.action.MAIN"))
        assertTrue(launcherDeclaration.contains("android.intent.category.LAUNCHER"))
    }

    private fun String.countOccurrences(value: String): Int =
        windowed(value.length, 1).count { it == value }

    private fun projectFile(path: String): File {
        val fromRoot = File(path)
        if (fromRoot.exists()) return fromRoot
        return File(path.removePrefix("app/"))
    }
}
