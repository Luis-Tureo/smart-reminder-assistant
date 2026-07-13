package com.luistureo.voicereminderapp.core.wellness

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.luistureo.voicereminderapp.core.speech.AssistantTtsAudio
import com.luistureo.voicereminderapp.core.speech.AssistantTtsService
import com.luistureo.voicereminderapp.core.speech.AssistantVoiceOption
import com.luistureo.voicereminderapp.core.speech.VoiceAssistantSpeaker
import com.luistureo.voicereminderapp.presentation.assistant.AssistantDialogueBubbleView

class WellnessAssistantCoordinator(
    context: Context,
    private val bubbleView: AssistantDialogueBubbleView,
    voiceEnabled: Boolean = false,
    bubbleEnabled: Boolean = false,
    bubbleDurationMillis: Long = DEFAULT_BUBBLE_DURATION_MILLIS
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speaker: VoiceAssistantSpeaker? = null
    private var voiceEnabled: Boolean = voiceEnabled
    private var bubbleEnabled: Boolean = bubbleEnabled
    private var bubbleDurationMillis: Long = normalizeBubbleDuration(bubbleDurationMillis)

    @Volatile
    private var released: Boolean = false

    private val hideBubble = Runnable {
        if (!released) bubbleView.hideBubble()
    }

    fun updateOutputSettings(
        voiceEnabled: Boolean,
        bubbleEnabled: Boolean,
        bubbleDurationMillis: Long = this.bubbleDurationMillis
    ) {
        if (released) return
        runOnMainThread {
            this.voiceEnabled = voiceEnabled
            this.bubbleEnabled = bubbleEnabled
            this.bubbleDurationMillis = normalizeBubbleDuration(bubbleDurationMillis)

            if (!voiceEnabled) releaseSpeaker()
            if (!bubbleEnabled) hideBubbleImmediately()
        }
    }

    fun setVoiceEnabled(enabled: Boolean) {
        updateOutputSettings(
            voiceEnabled = enabled,
            bubbleEnabled = bubbleEnabled
        )
    }

    fun setBubbleEnabled(enabled: Boolean) {
        updateOutputSettings(
            voiceEnabled = voiceEnabled,
            bubbleEnabled = enabled
        )
    }

    fun present(phrase: WellnessAssistantPhrase) {
        if (released) return
        val catalogText = WellnessAssistantSpeechPolicy.textFor(phrase)
        runOnMainThread {
            if (bubbleEnabled) showTemporaryBubble(catalogText) else hideBubbleImmediately()
            if (voiceEnabled) localSpeaker().speakText(catalogText)
        }
    }

    fun release() {
        if (released) return
        released = true
        if (Looper.myLooper() == Looper.getMainLooper()) {
            releaseOnMainThread()
        } else {
            mainHandler.post(::releaseOnMainThread)
        }
    }

    private fun showTemporaryBubble(text: String) {
        mainHandler.removeCallbacks(hideBubble)
        bubbleView.showMessage(
            text = text,
            animateText = true,
            playTypingSound = false
        )
        mainHandler.postDelayed(hideBubble, bubbleDurationMillis)
    }

    private fun hideBubbleImmediately() {
        mainHandler.removeCallbacks(hideBubble)
        bubbleView.stopAllEffects()
        bubbleView.hideBubble()
    }

    private fun localSpeaker(): VoiceAssistantSpeaker {
        return speaker ?: VoiceAssistantSpeaker(
            context = appContext,
            remoteTtsService = LocalOnlyTtsService,
            isRemoteTtsEnabled = false,
            remoteBackendUrl = ""
        ).also { speaker = it }
    }

    private fun releaseSpeaker() {
        speaker?.shutdown()
        speaker = null
    }

    private fun releaseOnMainThread() {
        mainHandler.removeCallbacksAndMessages(null)
        bubbleView.stopAllEffects()
        bubbleView.hideBubble()
        releaseSpeaker()
    }

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (!released) action()
        } else {
            mainHandler.post {
                if (!released) action()
            }
        }
    }

    private object LocalOnlyTtsService : AssistantTtsService {
        override suspend fun synthesize(
            text: String,
            voice: AssistantVoiceOption?
        ): AssistantTtsAudio? = null
    }

    companion object {
        const val DEFAULT_BUBBLE_DURATION_MILLIS: Long = 4_500L
        private const val MIN_BUBBLE_DURATION_MILLIS: Long = 1_500L
        private const val MAX_BUBBLE_DURATION_MILLIS: Long = 12_000L

        private fun normalizeBubbleDuration(durationMillis: Long): Long =
            durationMillis.coerceIn(
                MIN_BUBBLE_DURATION_MILLIS,
                MAX_BUBBLE_DURATION_MILLIS
            )
    }
}
