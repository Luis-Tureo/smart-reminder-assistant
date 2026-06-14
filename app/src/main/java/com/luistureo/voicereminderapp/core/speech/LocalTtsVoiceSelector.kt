package com.luistureo.voicereminderapp.core.speech

import java.util.Locale

data class LocalTtsVoiceCandidate(
    val name: String,
    val locale: Locale,
    val quality: Int,
    val latency: Int,
    val requiresNetwork: Boolean
)

object LocalTtsVoiceSelector {
    private val preferredSpanishLocales = listOf(
        Locale.forLanguageTag("es-CL"),
        Locale.forLanguageTag("es-ES"),
        Locale.forLanguageTag("es-US"),
        Locale.forLanguageTag("es-MX")
    )

    fun selectBestSpanishVoice(
        voices: Collection<LocalTtsVoiceCandidate>
    ): LocalTtsVoiceCandidate? {
        return voices
            .filter { it.locale.language.equals("es", ignoreCase = true) }
            .maxByOrNull { it.score() }
    }

    fun preferredLocales(): List<Locale> = preferredSpanishLocales

    private fun LocalTtsVoiceCandidate.score(): Int {
        val normalizedName = name.lowercase(Locale.ROOT)
        val localeScore = when {
            locale == Locale.forLanguageTag("es-CL") -> 500
            locale == Locale.forLanguageTag("es-ES") -> 450
            locale == Locale.forLanguageTag("es-US") -> 425
            locale == Locale.forLanguageTag("es-MX") -> 400
            locale.language.equals("es", ignoreCase = true) -> 300
            else -> 0
        }
        val offlineScore = if (requiresNetwork) -260 else 220
        val qualityScore = quality / 5
        val latencyScore = -(latency / 5)
        val naturalScore = if (naturalVoiceHints.any { normalizedName.contains(it) }) 120 else 0
        val femaleScore = if (femaleVoiceHints.any { normalizedName.contains(it) }) 90 else 0

        return localeScore + offlineScore + qualityScore + latencyScore + naturalScore + femaleScore
    }

    private val naturalVoiceHints = listOf(
        "neural",
        "natural",
        "premium",
        "enhanced",
        "studio",
        "wavenet"
    )

    private val femaleVoiceHints = listOf(
        "female",
        "femen",
        "mujer",
        "paulina",
        "maria",
        "monica",
        "lucia",
        "laura",
        "carmen",
        "elena"
    )
}
