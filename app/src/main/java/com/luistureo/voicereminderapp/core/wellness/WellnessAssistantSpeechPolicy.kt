package com.luistureo.voicereminderapp.core.wellness

object WellnessAssistantSpeechPolicy {
    private val allowedCatalogTexts: Set<String> = WellnessAssistantPhrase.entries
        .mapTo(linkedSetOf()) { it.catalogText }

    fun textFor(phrase: WellnessAssistantPhrase): String = phrase.catalogText

    fun isAllowedCatalogText(text: String): Boolean = text in allowedCatalogTexts
}
