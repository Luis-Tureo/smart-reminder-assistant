package com.luistureo.voicereminderapp.core.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantTtsConfigTest {

    @Test
    fun remoteTtsBackendIsConfiguredForCloudRunEndpoint() {
        assertEquals(
            "https://reminder-tts-backend-3ypzjittiq-uc.a.run.app/tts",
            AssistantTtsConfig.REMOTE_TTS_BACKEND_URL
        )
        assertTrue(AssistantTtsConfig.isRemoteTtsEnabled())
    }

    @Test
    fun remoteTtsUsesSingleFixedCloudVoice() {
        assertEquals("es-US-Standard-A", AssistantTtsConfig.REMOTE_TTS_VOICE)
    }

    @Test
    fun configuredRemoteTtsRoutesToBackend() {
        assertTrue(
            AssistantTtsRoutingPolicy.shouldTryRemote(
                isRemoteEnabled = AssistantTtsConfig.isRemoteTtsEnabled(),
                backendUrl = AssistantTtsConfig.REMOTE_TTS_BACKEND_URL,
                text = "Hola"
            )
        )
    }
}
