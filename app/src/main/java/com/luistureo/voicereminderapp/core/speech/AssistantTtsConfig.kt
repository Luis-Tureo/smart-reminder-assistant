package com.luistureo.voicereminderapp.core.speech

object AssistantTtsConfig {
    const val ENABLE_REMOTE_TTS: Boolean = true
    const val REMOTE_TTS_BACKEND_URL: String =
        "https://reminder-tts-backend-3ypzjittiq-uc.a.run.app/tts"
    const val REMOTE_TTS_VOICE: String = "es-US-Standard-A"

    fun isRemoteTtsEnabled(): Boolean {
        return ENABLE_REMOTE_TTS && REMOTE_TTS_BACKEND_URL.isNotBlank()
    }
}
