package com.luistureo.voicereminderapp.core.speech

object AssistantTtsFallbackNoticePolicy {
    const val OFFLINE_FALLBACK_MESSAGE =
        "No hay internet, se usar\u00e1 la voz nativa del sistema."

    fun shouldNotifyUser(reason: AssistantTtsFallbackReason): Boolean {
        return reason == AssistantTtsFallbackReason.REMOTE_UNAVAILABLE ||
                reason == AssistantTtsFallbackReason.REMOTE_PLAYBACK_FAILED
    }

    fun messageFor(reason: AssistantTtsFallbackReason): String? {
        return OFFLINE_FALLBACK_MESSAGE.takeIf { shouldNotifyUser(reason) }
    }
}
