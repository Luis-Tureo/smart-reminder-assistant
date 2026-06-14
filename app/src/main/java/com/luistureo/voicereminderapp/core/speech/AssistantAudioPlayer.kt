package com.luistureo.voicereminderapp.core.speech

interface AssistantAudioPlayer {
    fun play(
        audio: AssistantTtsAudio,
        onStarted: () -> Unit,
        onFinished: () -> Unit,
        onError: () -> Unit
    ): Boolean

    fun stop()

    fun release()
}
