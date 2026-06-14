package com.luistureo.voicereminderapp.core.speech

object AssistantTtsFallbackPolicy {
    fun shouldUseLocalFallback(
        requestedText: String,
        remoteAudio: AssistantTtsAudio?
    ): Boolean {
        return requestedText.isBlank() || remoteAudio?.isPlayable != true
    }
}
