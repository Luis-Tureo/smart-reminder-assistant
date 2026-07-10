package com.luistureo.voicereminderapp.presentation.routine

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutinePhase5ResourceTest {
    @Test fun progressAndSuggestionsStayInsideRoutineModule() {
        val manifest = file("app/src/main/AndroidManifest.xml").readText()
        val dashboard = file("app/src/main/res/layout/activity_routine_dashboard.xml").readText()
        assertTrue(manifest.contains("RoutineProgressActivity"))
        assertTrue(manifest.contains("RoutineSuggestionsActivity"))
        assertTrue(dashboard.contains("btnRoutineProgress"))
        assertTrue(dashboard.contains("btnRoutineSuggestions"))
        assertFalse(file("app/src/main/res/layout/activity_main.xml").readText().contains("RoutineProgressActivity"))
    }

    @Test fun preferencesAreLocalAndCoverChartsStreakSuggestionsTemplatesAndMessages() {
        val source = file(
            "app/src/main/java/com/luistureo/voicereminderapp/core/routine/RoutinePreferenceStore.kt"
        ).readText()
        assertTrue(source.contains("getChartType"))
        assertTrue(source.contains("getStreakSettings"))
        assertTrue(source.contains("getSuggestionSettings"))
        assertTrue(source.contains("setTemplateVisibility"))
        assertTrue(source.contains("setMotivationMessages"))
        assertTrue(source.contains("Context.MODE_PRIVATE"))
    }

    @Test fun chartsUseNativeCanvasWithoutLargeDependency() {
        val source = file(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/routine/RoutineChartView.kt"
        ).readText()
        assertTrue(source.contains("Canvas"))
        assertTrue(source.contains("contentDescription"))
        assertFalse(file("app/build.gradle.kts").readText().contains("MPAndroidChart"))
    }

    private fun file(path: String): File = File(path).takeIf(File::exists)
        ?: File(path.removePrefix("app/"))
}
