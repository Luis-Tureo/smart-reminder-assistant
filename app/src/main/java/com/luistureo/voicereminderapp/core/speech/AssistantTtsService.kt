package com.luistureo.voicereminderapp.core.speech

interface AssistantTtsService {
    suspend fun synthesize(
        text: String,
        voice: AssistantVoiceOption? = null
    ): AssistantTtsAudio?
}
