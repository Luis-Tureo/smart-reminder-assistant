package com.luistureo.voicereminderapp.presentation.routine

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RoutineUiFlowResourceTest {
    @Test
    fun homeShowsMiDiaAndRoutineCardInRequestedOrder() {
        val layout = projectFile("app/src/main/res/layout/activity_main.xml").readText()
        val strings = projectFile("app/src/main/res/values/strings.xml").readText()

        assertTrue(strings.contains("<string name=\"home_screen_title\">Mi día</string>"))
        assertTrue(strings.contains("Organiza tus tareas, rutinas y compromisos"))
        assertTrue(layout.contains("@+id/cardDailyRoutines"))
        assertTrue(layout.indexOf("@+id/cardCalendar") < layout.indexOf("@+id/cardLoan"))
        assertTrue(layout.indexOf("@+id/cardLoan") < layout.indexOf("@+id/cardDailyRoutines"))
    }

    @Test
    fun homeCardOpensRoutineDashboard() {
        val source = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/MainActivity.kt"
        ).readText()

        assertTrue(source.contains("R.id.cardDailyRoutines"))
        assertTrue(source.contains("Intent(this, RoutineDashboardActivity::class.java)"))
    }

    @Test
    fun dashboardAndDetailWireDefaultRoutineFlow() {
        val dashboardLayout = projectFile(
            "app/src/main/res/layout/activity_routine_dashboard.xml"
        ).readText()
        val dashboardSource = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/routine/RoutineDashboardActivity.kt"
        ).readText()
        val detailLayout = projectFile(
            "app/src/main/res/layout/activity_routine_detail.xml"
        ).readText()

        assertTrue(dashboardLayout.contains("@+id/recyclerRoutineDashboard"))
        assertTrue(dashboardSource.contains("viewModel.loadDashboard()"))
        assertTrue(dashboardSource.contains("RoutineDetailActivity::class.java"))
        assertTrue(detailLayout.contains("@+id/recyclerRoutineTasks"))
        assertTrue(detailLayout.contains("@+id/btnCompleteRoutine"))
        assertTrue(detailLayout.contains("@+id/btnEditRoutine"))
    }

    @Test
    fun editorSettingsAndTemplatesExposeRequiredActions() {
        val editor = projectFile("app/src/main/res/layout/activity_routine_editor.xml").readText()
        val settings = projectFile("app/src/main/res/layout/activity_routine_settings.xml").readText()
        val templates = projectFile("app/src/main/res/layout/activity_routine_templates.xml").readText()
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()

        assertTrue(editor.contains("@+id/btnAddRoutineTask"))
        assertTrue(editor.contains("@+id/recyclerRoutineEditorTasks"))
        assertTrue(editor.contains("@+id/btnSaveRoutine"))
        assertTrue(settings.contains("@+id/groupRoutineAssistantMode"))
        assertTrue(settings.contains("@+id/checkRoutineMotivationBubbles"))
        assertTrue(settings.contains("@+id/checkRoutineAssistantVoice"))
        assertTrue(templates.contains("@+id/recyclerRoutineTemplates"))
        assertTrue(manifest.contains(".presentation.routine.RoutineDashboardActivity"))
        assertTrue(manifest.contains(".presentation.routine.RoutineDetailActivity"))
        assertTrue(manifest.contains(".presentation.routine.RoutineEditorActivity"))
        assertTrue(manifest.contains(".presentation.routine.RoutineSettingsActivity"))
        assertTrue(manifest.contains(".presentation.routine.RoutineTemplatesActivity"))
    }

    private fun projectFile(path: String): File {
        val fromRoot = File(path)
        if (fromRoot.exists()) return fromRoot
        return File(path.removePrefix("app/"))
    }
}
