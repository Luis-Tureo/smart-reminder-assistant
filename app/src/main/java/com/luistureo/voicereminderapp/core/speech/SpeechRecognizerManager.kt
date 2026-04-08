package com.luistureo.voicereminderapp.core.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class SpeechRecognizerManager(
    private val context: Context
) {

    interface Listener {
        fun onPartialTranscription(text: String)
        fun onFinalTranscription(text: String)
        fun onNoMatch()
        fun onRecognitionError()
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var listener: Listener? = null
    private var activeSessionId: Long? = null
    private var lastSessionId: Long = 0L
    private var lastPartialText: String = ""

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun isListeningOrStarting(): Boolean {
        return activeSessionId != null
    }

    fun startRecognition() {
        if (!isRecognitionAvailable()) {
            listener?.onRecognitionError()
            return
        }

        cancelRecognition(notifyListener = false)

        val sessionId = ++lastSessionId
        activeSessionId = sessionId
        lastPartialText = ""

        createRecognizer(sessionId)

        runCatching {
            speechRecognizer?.startListening(buildRecognitionIntent())
        }.onFailure {
            clearSession(sessionId)
            destroyRecognizer()
            listener?.onRecognitionError()
        }
    }

    fun cancelRecognition(notifyListener: Boolean = false) {
        val hadActiveSession = activeSessionId != null

        activeSessionId = null
        lastPartialText = ""

        runCatching {
            speechRecognizer?.cancel()
        }

        destroyRecognizer()

        if (notifyListener && hadActiveSession) {
            listener?.onRecognitionError()
        }
    }

    fun destroy() {
        listener = null
        cancelRecognition(notifyListener = false)
    }

    private fun createRecognizer(sessionId: Long) {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    if (!isActiveSession(sessionId)) return

                    clearSession(sessionId)
                    destroyRecognizer()

                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> listener?.onNoMatch()

                        else -> listener?.onRecognitionError()
                    }
                }

                override fun onResults(results: Bundle?) {
                    if (!isActiveSession(sessionId)) return

                    val finalText = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()

                    clearSession(sessionId)
                    destroyRecognizer()

                    if (finalText.isNullOrBlank()) {
                        listener?.onNoMatch()
                        return
                    }

                    listener?.onFinalTranscription(finalText)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (!isActiveSession(sessionId)) return

                    val partialText = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()

                    if (partialText.isBlank() || partialText == lastPartialText) return

                    lastPartialText = partialText
                    listener?.onPartialTranscription(partialText)
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
    }

    private fun buildRecognitionIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CL")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-CL")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1400L)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                1100L
            )
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 800L)
        }
    }

    private fun isActiveSession(sessionId: Long): Boolean {
        return activeSessionId == sessionId
    }

    private fun clearSession(sessionId: Long) {
        if (activeSessionId != sessionId) return

        activeSessionId = null
        lastPartialText = ""
    }

    private fun destroyRecognizer() {
        runCatching {
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
    }
}
