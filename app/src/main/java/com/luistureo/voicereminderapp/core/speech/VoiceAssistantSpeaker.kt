package com.luistureo.voicereminderapp.core.speech

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class VoiceAssistantSpeaker(
    context: Context
) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val speakerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var textToSpeech: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null

    private var pendingText: String? = null
    private var pendingOnFinished: (() -> Unit)? = null
    private var isLocalTtsReady = false

    private val tokenProvider = GoogleAuthTokenProvider(appContext)
    private val googleCloudTtsService = GoogleCloudTtsService(
        context = appContext,
        tokenProvider = tokenProvider
    )

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
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                pendingOnFinished?.invoke()
                clearPendingData()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                pendingOnFinished?.invoke()
                clearPendingData()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                pendingOnFinished?.invoke()
                clearPendingData()
            }
        })

        isLocalTtsReady = true

        val text = pendingText
        val onFinished = pendingOnFinished

        if (!text.isNullOrBlank()) {
            speakText(text, onFinished)
        }
    }

    fun speakText(
        message: String,
        onFinished: (() -> Unit)? = null
    ) {
        pendingText = message
        pendingOnFinished = onFinished

        stopCurrentPlayback()

        speakerScope.launch {
            try {
                val naturalMessage = message
                    .replace(".", ". ")
                    .replace(",", ", ")
                    .trim()

                val audioFile = googleCloudTtsService.synthesizeToTempFile(naturalMessage)

                playCloudAudio(
                    file = audioFile,
                    onFinished = onFinished
                )
            } catch (_: Exception) {
                speakWithLocalTtsFallback(message)
            }
        }
    }

    fun shutdown() {
        stopCurrentPlayback()

        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null

        isLocalTtsReady = false
        clearPendingData()
        speakerScope.cancel()
    }

    private fun configureLocalTts() {
        val preferredLocale = Locale("es", "ES")
        val fallbackLocale = Locale("es", "US")

        val localeResult = textToSpeech?.setLanguage(preferredLocale)

        if (
            localeResult == TextToSpeech.LANG_MISSING_DATA ||
            localeResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            textToSpeech?.setLanguage(fallbackLocale)
        }

        textToSpeech?.setPitch(1.0f)
        textToSpeech?.setSpeechRate(0.85f)
    }

    private fun speakWithLocalTtsFallback(message: String) {
        if (!isLocalTtsReady) {
            pendingOnFinished?.invoke()
            clearPendingData()
            return
        }

        val utteranceId = "voice_assistant_${System.currentTimeMillis()}"

        textToSpeech?.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            Bundle(),
            utteranceId
        )
    }

    private fun playCloudAudio(
        file: File,
        onFinished: (() -> Unit)?
    ) {
        stopCurrentPlayback()

        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)

            setOnPreparedListener {
                start()
            }

            setOnCompletionListener { player ->
                player.release()
                mediaPlayer = null
                file.delete()

                speakerScope.launch {
                    delay(150)
                    onFinished?.invoke()
                    clearPendingData()
                }
            }

            setOnErrorListener { player, _, _ ->
                player.release()
                mediaPlayer = null
                file.delete()

                speakWithLocalTtsFallback(pendingText.orEmpty())
                true
            }

            prepareAsync()
        }
    }

    private fun stopCurrentPlayback() {
        mediaPlayer?.apply {
            try {
                if (isPlaying) {
                    stop()
                }
            } catch (_: Exception) {
            }

            release()
        }

        mediaPlayer = null
    }

    private fun clearPendingData() {
        pendingText = null
        pendingOnFinished = null
    }
}