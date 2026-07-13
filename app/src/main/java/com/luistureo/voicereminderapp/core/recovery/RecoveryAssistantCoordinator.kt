package com.luistureo.voicereminderapp.core.recovery

import android.content.Context
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.speech.VoiceAssistantSpeaker

enum class RecoveryAssistantMessage(val stringRes: Int) {
    REASON(R.string.recovery_assistant_reason),
    POSITIVE(R.string.recovery_assistant_positive),
    TOOLS(R.string.recovery_assistant_tools),
    DIFFICULT(R.string.recovery_assistant_difficult)
}

class RecoveryAssistantCoordinator(private val context: Context) {
    /** Uses the existing TTS implementation with remote routing explicitly disabled. */
    fun speak(message: RecoveryAssistantMessage, onFinished: (() -> Unit)? = null) {
        if (!RecoveryPreferenceStore(context).voiceEnabled()) return
        val speaker = VoiceAssistantSpeaker(
            context = context.applicationContext,
            isRemoteTtsEnabled = false,
            remoteBackendUrl = ""
        )
        speaker.speakText(context.getString(message.stringRes)) {
            speaker.shutdown()
            onFinished?.invoke()
        }
    }
}
