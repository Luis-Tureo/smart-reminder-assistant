package com.luistureo.voicereminderapp.core.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class LocalTtsVoiceSelectorTest {

    @Test
    fun prefersOfflineNaturalFemaleSpanishVoiceWhenAvailable() {
        val roboticVoice = LocalTtsVoiceCandidate(
            name = "es-es-standard-male",
            locale = Locale.forLanguageTag("es-ES"),
            quality = 300,
            latency = 300,
            requiresNetwork = false
        )
        val naturalFemaleVoice = LocalTtsVoiceCandidate(
            name = "es-es-natural-female-maria",
            locale = Locale.forLanguageTag("es-ES"),
            quality = 300,
            latency = 300,
            requiresNetwork = false
        )

        val selected = LocalTtsVoiceSelector.selectBestSpanishVoice(
            listOf(roboticVoice, naturalFemaleVoice)
        )

        assertEquals(naturalFemaleVoice, selected)
    }

    @Test
    fun prefersOfflineVoiceOverNetworkVoiceForFreeLocalDefault() {
        val networkVoice = LocalTtsVoiceCandidate(
            name = "es-es-natural-female-network",
            locale = Locale.forLanguageTag("es-ES"),
            quality = 500,
            latency = 100,
            requiresNetwork = true
        )
        val offlineVoice = LocalTtsVoiceCandidate(
            name = "es-es-standard",
            locale = Locale.forLanguageTag("es-ES"),
            quality = 300,
            latency = 300,
            requiresNetwork = false
        )

        val selected = LocalTtsVoiceSelector.selectBestSpanishVoice(
            listOf(networkVoice, offlineVoice)
        )

        assertEquals(offlineVoice, selected)
    }

    @Test
    fun ignoresNonSpanishVoices() {
        val selected = LocalTtsVoiceSelector.selectBestSpanishVoice(
            listOf(
                LocalTtsVoiceCandidate(
                    name = "en-us-natural-female",
                    locale = Locale.forLanguageTag("en-US"),
                    quality = 500,
                    latency = 100,
                    requiresNetwork = false
                )
            )
        )

        assertNull(selected)
    }
}
