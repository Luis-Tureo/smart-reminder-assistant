package com.luistureo.voicereminderapp.core.speech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantTtsFallbackPolicyTest {

    @Test
    fun usesRemoteAudioWhenItIsPlayable() {
        val audio = AssistantTtsAudio(
            bytes = byteArrayOf(1),
            mimeType = "audio/mpeg"
        )

        assertFalse(AssistantTtsFallbackPolicy.shouldUseLocalFallback("Hola", audio))
    }

    @Test
    fun fallsBackForEmptyTextMissingAudioOrInvalidAudio() {
        assertTrue(AssistantTtsFallbackPolicy.shouldUseLocalFallback("", null))
        assertTrue(AssistantTtsFallbackPolicy.shouldUseLocalFallback("Hola", null))
        assertTrue(
            AssistantTtsFallbackPolicy.shouldUseLocalFallback(
                "Hola",
                AssistantTtsAudio(bytes = byteArrayOf(), mimeType = "audio/mpeg")
            )
        )
        assertTrue(
            AssistantTtsFallbackPolicy.shouldUseLocalFallback(
                "Hola",
                AssistantTtsAudio(bytes = byteArrayOf(1), mimeType = "application/json")
            )
        )
    }
}
