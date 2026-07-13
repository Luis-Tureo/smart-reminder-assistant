package com.luistureo.voicereminderapp.presentation.notes

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickNotesResourceContractTest {

    @Test
    fun homeCardAndRegistryOpenPrivateWorkingQuickNotesScreens() {
        val homeLayout = projectFile("app/src/main/res/layout/activity_main.xml").readText()
        val main = source("MainActivity.kt")
        val registry = source("core/modules/HomeModuleRegistry.kt")
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()

        assertTrue(homeLayout.contains("@+id/cardQuickNotes"))
        assertTrue(
            homeLayout.indexOf("@+id/cardRecovery") < homeLayout.indexOf("@+id/cardQuickNotes")
        )
        assertTrue(main.contains("quickNotesCard = findViewById(R.id.cardQuickNotes)"))
        assertTrue(main.contains("openRegisteredModule(HomeModuleRegistry.QUICK_NOTES)"))
        assertTrue(registry.contains("const val QUICK_NOTES = \"quick_notes\""))
        assertTrue(registry.contains("R.id.cardQuickNotes"))
        assertTrue(registry.contains("presentation.notes.QuickNotesActivity"))

        assertPrivateActivity(manifest, ".presentation.notes.QuickNotesActivity")
        assertPrivateActivity(manifest, ".presentation.notes.QuickNoteEditorActivity")
        assertTrue(
            manifest.contains(
                "android:parentActivityName=\".presentation.notes.QuickNotesActivity\""
            )
        )
    }

    @Test
    fun dashboardExposesSearchExclusiveFiltersCompactListAndDistinctEmptyStates() {
        val layout = resource("layout/activity_quick_notes.xml")
        val activity = source("presentation/notes/QuickNotesActivity.kt")
        val strings = resource("values/quick_notes_strings.xml")

        listOf(
            "btnQuickNotesBack",
            "inputQuickNotesSearch",
            "chipGroupQuickNotesFilters",
            "chipQuickNotesAll",
            "chipQuickNotesPinned",
            "chipQuickNotesArchived",
            "recyclerQuickNotes",
            "quickNotesEmptyContainer",
            "tvQuickNotesEmptyTitle",
            "tvQuickNotesEmptyMessage",
            "fabNewQuickNote"
        ).forEach { id -> assertTrue("Falta $id", layout.contains("@+id/$id")) }

        assertTrue(layout.contains("app:singleSelection=\"true\""))
        assertTrue(layout.contains("app:selectionRequired=\"true\""))
        assertTrue(layout.contains("android:layout_height=\"match_parent\""))
        assertFalse(layout.contains("NestedScrollView"))
        assertTrue(activity.contains("viewModel.setQuery"))
        assertTrue(activity.contains("QuickNoteFilter.PINNED"))
        assertTrue(activity.contains("QuickNoteFilter.ARCHIVED"))
        assertTrue(activity.contains("adapter.submitList(state.notes)"))
        assertTrue(activity.contains("QuickNoteEditorActivity.intent(this, note?.id)"))

        listOf(
            "quick_notes_empty_title",
            "quick_notes_no_results_title",
            "quick_notes_no_pinned_title",
            "quick_notes_no_archived_title"
        ).forEach { name -> assertTrue(strings.contains("<string name=\"$name\"")) }
    }

    @Test
    fun noteCardUsesDiffingLargeActionsAndTextAlongsideColor() {
        val layout = resource("layout/item_quick_note.xml")
        val adapter = source("presentation/notes/QuickNotesAdapter.kt")

        listOf(
            "cardQuickNoteItem",
            "tvQuickNoteTitle",
            "tvQuickNotePreview",
            "tvQuickNotePinned",
            "viewQuickNoteColor",
            "tvQuickNoteColorLabel",
            "tvQuickNoteUpdated",
            "btnQuickNotePin",
            "btnQuickNoteArchive",
            "btnQuickNoteDelete"
        ).forEach { id -> assertTrue("Falta $id", layout.contains("@+id/$id")) }

        assertTrue(layout.contains("android:clickable=\"true\""))
        assertTrue(layout.contains("android:focusable=\"true\""))
        assertTrue(layout.countOccurrences("android:layout_height=\"48dp\"") >= 3)
        assertTrue(layout.contains("android:importantForAccessibility=\"no\""))
        assertTrue(adapter.contains("ListAdapter<QuickNote"))
        assertTrue(adapter.contains("DiffUtil.ItemCallback<QuickNote>"))
        assertTrue(adapter.contains("colorLabel.isVisible = color != null"))
        assertTrue(adapter.contains("card.contentDescription"))
    }

    @Test
    fun editorKeepsOptionalControlsCollapsedAndAnnouncesAutosaveState() {
        val layout = resource("layout/activity_quick_note_editor.xml")
        val activity = source("presentation/notes/QuickNoteEditorActivity.kt")
        val viewModel = source("presentation/notes/QuickNoteEditorViewModel.kt")
        val strings = resource("values/quick_notes_strings.xml")

        listOf(
            "inputQuickNoteTitle",
            "inputQuickNoteContent",
            "tvQuickNoteSaveStatus",
            "btnQuickNoteOptions",
            "quickNoteOptionsContainer",
            "checkQuickNotePinned",
            "inputQuickNoteColor",
            "btnQuickNoteShare",
            "btnQuickNoteArchiveEditor",
            "btnQuickNoteDeleteEditor",
            "btnQuickNoteDone"
        ).forEach { id -> assertTrue("Falta $id", layout.contains("@+id/$id")) }

        val optionsDeclaration = layout.substringAfter("@+id/quickNoteOptionsContainer")
            .substringBefore('>')
        assertTrue(optionsDeclaration.contains("android:visibility=\"gone\""))
        assertTrue(layout.contains("android:accessibilityLiveRegion=\"polite\""))
        assertTrue(layout.contains("android:importantForAutofill=\"noExcludeDescendants\""))
        assertTrue(layout.countOccurrences("flagNoPersonalizedLearning") >= 2)
        assertTrue(activity.contains("private var isBinding = false"))
        assertTrue(activity.contains("onSaveInstanceState"))
        assertTrue(activity.contains("viewModel.requestBack()"))

        assertTrue(viewModel.contains("SavedStateHandle"))
        assertTrue(viewModel.contains("Channel<Unit>(Channel.CONFLATED)"))
        assertTrue(viewModel.contains("Mutex()"))
        assertTrue(viewModel.contains("delay(autosaveDebounceMillis)"))
        assertTrue(viewModel.contains("persistLatest()"))
        assertTrue(viewModel.contains("QuickNoteValidator.normalizeOrNull"))
        assertTrue(viewModel.contains("saveState = QuickNoteSaveState.ERROR"))
        assertTrue(strings.contains("<string name=\"quick_note_saving\">"))
        assertTrue(strings.contains("<string name=\"quick_note_saved\">"))
        assertTrue(strings.contains("<string name=\"quick_note_save_failed\">"))
    }

    @Test
    fun shareIsExplicitTextOnlyAndRequiresMeaningfulContent() {
        val activity = source("presentation/notes/QuickNoteEditorActivity.kt")
        val viewModel = source("presentation/notes/QuickNoteEditorViewModel.kt")

        val shareAction = activity.substringAfter("private fun openShareSheet")
            .substringBefore("private fun installBackHandler")
        val shareRequest = viewModel.substringAfter("fun requestShare()")
            .substringBefore("fun setArchivedAndFinish")

        assertTrue(activity.contains("R.id.btnQuickNoteShare"))
        assertTrue(activity.contains("viewModel.requestShare()"))
        assertTrue(shareRequest.contains("QuickNoteValidator.normalizeOrNull"))
        assertTrue(shareRequest.contains("QuickNoteEditorEvent.ShowValidation"))
        assertTrue(shareAction.contains("Intent(Intent.ACTION_SEND)"))
        assertTrue(shareAction.contains("type = \"text/plain\""))
        assertTrue(shareAction.contains("Intent.EXTRA_TEXT"))
        assertTrue(shareAction.contains("Intent.createChooser"))
        assertFalse(shareAction.contains("startActivity(shareIntent)"))
    }

    @Test
    fun quickNotesStayInDedicatedRoomStorageWithoutSyncNetworkAiOrSensitiveLogs() {
        val roots = listOf(
            "core/notes",
            "domain/notes",
            "data/local/entity/notes",
            "data/local/dao/notes",
            "data/mapper/notes",
            "data/repository/notes",
            "presentation/notes"
        ).map(::sourceDirectory)
        val content = roots
            .flatMap { root -> root.walkTopDown().filter { it.extension == "kt" }.toList() }
            .joinToString("\n") { it.readText() }
        val database = source("data/local/database/ReminderDatabase.kt")
        val provider = source("data/repository/notes/QuickNoteRepositoryProvider.kt")

        assertTrue(database.contains("QuickNoteEntity::class"))
        assertTrue(database.contains("abstract fun quickNoteDao(): QuickNoteDao"))
        assertTrue(provider.contains("ReminderDatabase.getDatabase"))
        assertTrue(provider.contains(".quickNoteDao()"))

        listOf(
            "core.calendar",
            "GoogleCalendar",
            "MicrosoftCalendar",
            "Firebase",
            "okhttp",
            "https://",
            "http://",
            "RemoteAssistantTtsClient",
            "Log.d(",
            "Log.i(",
            "Log.w(",
            "Log.e("
        ).forEach { forbidden ->
            assertFalse("Dependencia prohibida: $forbidden", content.contains(forbidden))
        }
    }

    private fun assertPrivateActivity(manifest: String, activityName: String) {
        val marker = "android:name=\"$activityName\""
        assertTrue("Falta $activityName", manifest.contains(marker))
        val declaration = manifest.substringAfter(marker)
            .substringBefore("<activity")
        assertTrue("$activityName debe ser privada", declaration.contains("android:exported=\"false\""))
    }

    private fun String.countOccurrences(value: String): Int =
        windowed(value.length, 1).count { it == value }

    private fun source(relativePath: String): String =
        sourceDirectory("").resolve(relativePath).readText()

    private fun resource(relativePath: String): String =
        projectFile("app/src/main/res/$relativePath").readText()

    private fun sourceDirectory(relativePath: String): File =
        projectFile("app/src/main/java/com/luistureo/voicereminderapp/$relativePath")

    private fun projectFile(path: String): File {
        val fromRoot = File(path)
        if (fromRoot.exists()) return fromRoot
        return File(path.removePrefix("app/"))
    }
}
