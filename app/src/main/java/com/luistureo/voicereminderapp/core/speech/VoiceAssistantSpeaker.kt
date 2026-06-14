package com.luistureo.voicereminderapp.core.speech

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

class VoiceAssistantSpeaker(
    context: Context,
    private val remoteTtsService: AssistantTtsService = RemoteAssistantTtsClient(),
    private val audioPlayer: AssistantAudioPlayer = AndroidAssistantAudioPlayer(context),
    private val isRemoteTtsEnabled: Boolean = AssistantTtsConfig.isRemoteTtsEnabled(),
    private val remoteBackendUrl: String = AssistantTtsConfig.REMOTE_TTS_BACKEND_URL
) : TextToSpeech.OnInitListener {

    interface PlaybackListener {
        fun onPlaybackStarted()
        fun onPlaybackFinished()
    }

    interface FallbackListener {
        fun onLocalFallback(message: String)
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val speakerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var textToSpeech: TextToSpeech? = null

    private var pendingText: String? = null
    private var pendingOnFinished: (() -> Unit)? = null
    private var isLocalTtsReady = false
    private var isPlaybackActive = false
    private var playbackListener: PlaybackListener? = null
    private var fallbackListener: FallbackListener? = null
    private var playbackRequestId = 0L
    private val localFallbackVoiceOption: AssistantVoiceOption = AssistantVoiceOption.default
    private var hasNotifiedLocalFallback = false

    init {
        textToSpeech = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            pendingOnFinished?.invoke()
            clearPendingData()
            return
        }

        configureLocalTts()

        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                notifyPlaybackStarted()
            }

            override fun onDone(utteranceId: String?) {
                notifyPlaybackFinished()
                pendingOnFinished?.invoke()
                clearPendingData()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                notifyPlaybackFinished()
                pendingOnFinished?.invoke()
                clearPendingData()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                notifyPlaybackFinished()
                pendingOnFinished?.invoke()
                clearPendingData()
            }
        })

        isLocalTtsReady = true

        val text = pendingText
        val onFinished = pendingOnFinished

        if (!text.isNullOrBlank()) {
            speakWithLocalTtsFallback(text, localFallbackVoiceOption, onFinished)
        }
    }

    fun setPlaybackListener(listener: PlaybackListener?) {
        playbackListener = listener
    }

    fun setFallbackListener(listener: FallbackListener?) {
        fallbackListener = listener
    }

    fun resetFallbackNoticeForSession() {
        hasNotifiedLocalFallback = false
    }

    fun speakText(
        message: String,
        onFinished: (() -> Unit)? = null
    ) {
        val cleanMessage = message.trim()
        if (cleanMessage.isBlank()) {
            onFinished?.invoke()
            return
        }

        val requestId = ++playbackRequestId
        clearPendingData()
        AssistantTtsDebugLogger.log(
            "Assistant remote voice fixed: ${AssistantTtsConfig.REMOTE_TTS_VOICE}"
        )

        stopCurrentPlayback()

        if (!AssistantTtsRoutingPolicy.shouldTryRemote(isRemoteTtsEnabled, remoteBackendUrl, cleanMessage)) {
            useLocalTtsFallback(
                message = cleanMessage,
                reason = AssistantTtsFallbackReason.REMOTE_DISABLED_OR_UNCONFIGURED,
                onFinished = onFinished
            )
            return
        }

        speakerScope.launch {
            AssistantTtsDebugLogger.log(
                "Remote TTS started: voice=${AssistantTtsConfig.REMOTE_TTS_VOICE}"
            )
            val remoteAudio = runCatching {
                remoteTtsService.synthesize(cleanMessage, localFallbackVoiceOption)
            }.getOrElse { error ->
                AssistantTtsDebugLogger.log("Remote TTS service failed: ${error.javaClass.simpleName}")
                null
            }
            if (requestId != playbackRequestId) return@launch

            var fallbackReason = AssistantTtsFallbackReason.REMOTE_UNAVAILABLE
            if (!AssistantTtsFallbackPolicy.shouldUseLocalFallback(cleanMessage, remoteAudio)) {
                AssistantTtsDebugLogger.log("Remote TTS audio received")
                val didStartRemotePlayback = audioPlayer.play(
                    audio = remoteAudio ?: return@launch,
                    onStarted = {
                        AssistantTtsDebugLogger.log("Remote TTS audio playback started")
                        notifyPlaybackStarted()
                    },
                    onFinished = {
                        if (requestId == playbackRequestId) {
                            notifyPlaybackFinished()
                            onFinished?.invoke()
                            clearPendingData()
                        }
                    },
                    onError = {
                        if (requestId == playbackRequestId) {
                            AssistantTtsDebugLogger.log("Remote TTS audio playback failed")
                            notifyPlaybackFinished()
                            useLocalTtsFallback(
                                message = cleanMessage,
                                reason = AssistantTtsFallbackReason.REMOTE_PLAYBACK_FAILED,
                                onFinished = onFinished
                            )
                        }
                    }
                )

                if (didStartRemotePlayback) {
                    return@launch
                }

                AssistantTtsDebugLogger.log("Remote TTS audio playback failed to start")
                fallbackReason = AssistantTtsFallbackReason.REMOTE_PLAYBACK_FAILED
            }

            useLocalTtsFallback(
                message = cleanMessage,
                reason = fallbackReason,
                onFinished = onFinished
            )
        }
    }

    fun shutdown() {
        speakerScope.cancel()
        stopCurrentPlayback()
        notifyPlaybackFinished()

        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        audioPlayer.release()

        isLocalTtsReady = false
        clearPendingData()
    }

    private fun useLocalTtsFallback(
        message: String,
        reason: AssistantTtsFallbackReason,
        onFinished: (() -> Unit)?
    ) {
        AssistantTtsDebugLogger.log("Local fallback used: reason=$reason")
        notifyLocalFallback(reason)
        speakWithLocalTtsFallback(message, localFallbackVoiceOption, onFinished)
    }

    private fun configureLocalTts() {
        val selectedVoice = selectBestLocalSpanishVoice()
        if (selectedVoice != null && textToSpeech?.setVoice(selectedVoice) == TextToSpeech.SUCCESS) {
            textToSpeech?.setLanguage(selectedVoice.locale)
        } else {
            configureFallbackSpanishLocale()
        }

        applyLocalVoiceTuning(localFallbackVoiceOption)
    }

    private fun configureFallbackSpanishLocale() {
        val supportedLocale = LocalTtsVoiceSelector.preferredLocales().firstOrNull { locale ->
            val result = textToSpeech?.setLanguage(locale)
            result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
        }

        if (supportedLocale == null) {
            textToSpeech?.setLanguage(Locale.getDefault())
        }
    }

    private fun selectBestLocalSpanishVoice(): Voice? {
        val voices = runCatching { textToSpeech?.voices.orEmpty() }.getOrDefault(emptySet())
        val voicesByCandidate = voices.associateBy { voice ->
            voice.toLocalTtsVoiceCandidate()
        }
        return LocalTtsVoiceSelector.selectBestSpanishVoice(voicesByCandidate.keys)
            ?.let { voicesByCandidate[it] }
    }

    private fun Voice.toLocalTtsVoiceCandidate(): LocalTtsVoiceCandidate {
        return LocalTtsVoiceCandidate(
            name = name.orEmpty(),
            locale = locale ?: Locale.getDefault(),
            quality = quality,
            latency = latency,
            requiresNetwork = isNetworkConnectionRequired
        )
    }

    private fun speakWithLocalTtsFallback(
        message: String,
        voiceOption: AssistantVoiceOption,
        onFinished: (() -> Unit)?
    ) {
        pendingText = message
        pendingOnFinished = onFinished

        if (!isLocalTtsReady) {
            return
        }

        applyLocalVoiceTuning(voiceOption)
        val utteranceId = "voice_assistant_${System.currentTimeMillis()}"

        textToSpeech?.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            Bundle(),
            utteranceId
        )
    }

    private fun applyLocalVoiceTuning(option: AssistantVoiceOption) {
        textToSpeech?.setPitch(option.localPitch)
        textToSpeech?.setSpeechRate(option.localSpeechRate)
    }

    private fun stopCurrentPlayback() {
        val hadActivePlayback = isPlaybackActive

        audioPlayer.stop()

        try {
            textToSpeech?.stop()
        } catch (_: Exception) {
        }

        if (hadActivePlayback) {
            notifyPlaybackFinished()
        }
    }

    private fun clearPendingData() {
        pendingText = null
        pendingOnFinished = null
    }

    private fun notifyLocalFallback(reason: AssistantTtsFallbackReason) {
        val message = AssistantTtsFallbackNoticePolicy.messageFor(reason) ?: return
        if (hasNotifiedLocalFallback) return

        hasNotifiedLocalFallback = true
        mainHandler.post {
            fallbackListener?.onLocalFallback(message)
        }
    }

    private fun notifyPlaybackStarted() {
        if (isPlaybackActive) return

        isPlaybackActive = true
        mainHandler.post {
            playbackListener?.onPlaybackStarted()
        }
    }

    private fun notifyPlaybackFinished() {
        if (!isPlaybackActive) return

        isPlaybackActive = false
        mainHandler.post {
            playbackListener?.onPlaybackFinished()
        }
    }
}
