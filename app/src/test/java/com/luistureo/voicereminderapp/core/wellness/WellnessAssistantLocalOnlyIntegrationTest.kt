package com.luistureo.voicereminderapp.core.wellness

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WellnessAssistantLocalOnlyIntegrationTest {
    @Test
    fun coordinatorForcesLocalTtsWithoutRecognitionOrHttp() {
        val source = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/core/wellness/" +
                "WellnessAssistantCoordinator.kt"
        ).readText()

        assertTrue(source.contains("VoiceAssistantSpeaker"))
        assertTrue(source.contains("isRemoteTtsEnabled = false"))
        assertTrue(source.contains("remoteBackendUrl = \"\""))
        assertTrue(source.contains("LocalOnlyTtsService"))
        assertFalse(source.contains("RemoteAssistantTtsClient"))
        assertFalse(source.contains("SpeechRecognizer"))
        assertFalse(source.contains("OkHttp"))
        assertFalse(source.contains("http://", ignoreCase = true))
        assertFalse(source.contains("https://", ignoreCase = true))
    }

    @Test
    fun publicPresentationApiAcceptsOnlyCatalogIdentifiers() {
        val source = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/core/wellness/" +
                "WellnessAssistantCoordinator.kt"
        ).readText()

        assertTrue(source.contains("fun present(phrase: WellnessAssistantPhrase)"))
        assertFalse(source.contains("fun present(text: String)"))
        assertFalse(source.contains("fun speak(text: String)"))
    }

    private fun projectFile(path: String): File {
        val fromRoot = File(path)
        if (fromRoot.exists()) return fromRoot
        return File(path.removePrefix("app/"))
    }
}
