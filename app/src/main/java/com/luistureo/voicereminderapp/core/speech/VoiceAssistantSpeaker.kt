package com.luistureo.voicereminderapp.core.speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class VoiceAssistantSpeaker(
    context: Context
) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private var textToSpeech: TextToSpeech? = null
    private var pendingText: String? = null
    private var pendingOnFinished: (() -> Unit)? = null
    private var isReady = false

    init {
        textToSpeech = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            pendingOnFinished?.invoke()
            clearPendingData()
            return
        }

        val localeResult = textToSpeech?.setLanguage(Locale("es", "CL"))

        if (
            localeResult == TextToSpeech.LANG_MISSING_DATA ||
            localeResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            textToSpeech?.setLanguage(Locale("es", "ES"))
        }

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

        isReady = true

        val text = pendingText
        if (!text.isNullOrBlank()) {
            speakInternal(text)
        }
    }

    // Reproduce un texto cualquiera con voz
    fun speakText(
        message: String,
        onFinished: (() -> Unit)? = null
    ) {
        pendingText = message
        pendingOnFinished = onFinished

        if (isReady) {
            speakInternal(message)
        }
    }

    // Libera recursos del motor TTS
    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        isReady = false
        clearPendingData()
    }

    private fun speakInternal(message: String) {
        val utteranceId = "voice_assistant_${System.currentTimeMillis()}"

        textToSpeech?.speak(
            message,
            TextToSpeech.QUEUE_FLUSH,
            Bundle(),
            utteranceId
        )
    }

    private fun clearPendingData() {
        pendingText = null
        pendingOnFinished = null
    }
}