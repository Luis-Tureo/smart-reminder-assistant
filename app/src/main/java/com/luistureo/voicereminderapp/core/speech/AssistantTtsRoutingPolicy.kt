package com.luistureo.voicereminderapp.core.speech

object AssistantTtsRoutingPolicy {
    fun shouldTryRemote(
        isRemoteEnabled: Boolean,
        backendUrl: String,
        text: String
    ): Boolean {
        return isRemoteEnabled && backendUrl.isNotBlank() && text.isNotBlank()
    }
}
