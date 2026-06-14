package com.luistureo.voicereminderapp.core.speech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantTtsRoutingPolicyTest {

    @Test
    fun doesNotTryRemoteWhenDisabled() {
        assertFalse(
            AssistantTtsRoutingPolicy.shouldTryRemote(
                isRemoteEnabled = false,
                backendUrl = "https://example.test/tts",
                text = "Hola"
            )
        )
    }

    @Test
    fun doesNotTryRemoteWhenBackendUrlIsMissing() {
        assertFalse(
            AssistantTtsRoutingPolicy.shouldTryRemote(
                isRemoteEnabled = true,
                backendUrl = "",
                text = "Hola"
            )
        )
    }

    @Test
    fun doesNotTryRemoteForEmptyText() {
        assertFalse(
            AssistantTtsRoutingPolicy.shouldTryRemote(
                isRemoteEnabled = true,
                backendUrl = "https://example.test/tts",
                text = "   "
            )
        )
    }

    @Test
    fun triesRemoteOnlyWhenExplicitlyEnabledAndConfigured() {
        assertTrue(
            AssistantTtsRoutingPolicy.shouldTryRemote(
                isRemoteEnabled = true,
                backendUrl = "https://example.test/tts",
                text = "Hola"
            )
        )
    }
}
