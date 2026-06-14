package com.luistureo.voicereminderapp.core.speech

enum class AssistantVoiceOption(
    val id: String,
    val backendVoiceValue: String?,
    val localPitch: Float,
    val localSpeechRate: Float
) {
    LOCAL_PHONE(
        id = "local_phone",
        backendVoiceValue = null,
        localPitch = 1.08f,
        localSpeechRate = 0.90f
    );

    companion object {
        val default: AssistantVoiceOption = LOCAL_PHONE
    }
}
