package com.luistureo.voicereminderapp.presentation.assistant

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class AssistantVoiceSelectorUiTest {

    @Test
    fun assistantLayoutDoesNotExposeVoiceSelectorButton() {
        val layout = readProjectFile("src/main/res/layout/activity_assistant.xml")

        assertFalse(layout.contains("btnAssistantVoice"))
        assertFalse(layout.contains("assistant_change_voice"))
    }

    @Test
    fun assistantActivityDoesNotUseVoiceSelectorFlow() {
        val activity = readProjectFile(
            "src/main/java/com/luistureo/voicereminderapp/presentation/assistant/AssistantActivity.kt"
        )

        assertFalse(activity.contains("showVoiceSelectorDialog"))
        assertFalse(activity.contains("previewVoiceOption"))
        assertFalse(activity.contains("AssistantVoiceSelectionPolicy"))
    }

    private fun readProjectFile(modulePath: String): String {
        val candidates = listOf(
            File(modulePath),
            File("app", modulePath)
        )
        return candidates.first { it.exists() }.readText()
    }
}
