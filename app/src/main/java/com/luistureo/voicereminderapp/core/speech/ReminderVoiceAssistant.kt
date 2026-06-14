package com.luistureo.voicereminderapp.core.speech

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class ReminderVoiceAssistant(
    private val context: Context
) : TextToSpeech.OnInitListener {

    private var textToSpeech: TextToSpeech? = null
    private var pendingText: String? = null
    private var onFinished: (() -> Unit)? = null
    private var isInitialized = false

    init {
        textToSpeech = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            onFinished?.invoke()
            return
        }

        val primaryLocaleResult = textToSpeech?.setLanguage(Locale.forLanguageTag("es-CL"))

        if (
            primaryLocaleResult == TextToSpeech.LANG_MISSING_DATA ||
            primaryLocaleResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            textToSpeech?.setLanguage(Locale.forLanguageTag("es-ES"))
        }

        isInitialized = true

        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                onFinished?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onFinished?.invoke()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                onFinished?.invoke()
            }
        })

        pendingText?.let { text ->
            speakInternal(text)
            pendingText = null
        }
    }

    // Habla el contenido del recordatorio con fecha y hora
    fun speakReminder(
        reminderText: String,
        reminderDate: String,
        reminderTime: String,
        onFinished: (() -> Unit)? = null
    ) {
        this.onFinished = onFinished

        val speechText = buildSpeechText(
            reminderText = reminderText,
            reminderDate = reminderDate,
            reminderTime = reminderTime
        )

        if (isInitialized) {
            speakInternal(speechText)
        } else {
            pendingText = speechText
        }
    }

    // Detiene y libera el motor TTS
    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }

    private fun speakInternal(text: String) {
        val utteranceId = "reminder_${System.currentTimeMillis()}"

        textToSpeech?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            Bundle(),
            utteranceId
        )
    }

    private fun buildSpeechText(
        reminderText: String,
        reminderDate: String,
        reminderTime: String
    ): String {
        val spokenDate = formatDateForSpeech(reminderDate)
        val spokenTime = formatTimeForSpeech(reminderTime)

        return "Tienes un recordatorio. $reminderText. Programado para el $spokenDate a las $spokenTime."
    }

    private fun formatDateForSpeech(date: String): String {
        val parts = date.split("/")
        if (parts.size != 3) return date

        val day = parts[0].toIntOrNull() ?: return date
        val month = parts[1].toIntOrNull() ?: return date
        val year = parts[2].toIntOrNull() ?: return date

        val monthName = when (month) {
            1 -> "enero"
            2 -> "febrero"
            3 -> "marzo"
            4 -> "abril"
            5 -> "mayo"
            6 -> "junio"
            7 -> "julio"
            8 -> "agosto"
            9 -> "septiembre"
            10 -> "octubre"
            11 -> "noviembre"
            12 -> "diciembre"
            else -> return date
        }

        return "$day de $monthName de $year"
    }

    private fun formatTimeForSpeech(time: String): String {
        val parts = time.split(":")
        if (parts.size != 2) return time

        val hour = parts[0].toIntOrNull() ?: return time
        val minute = parts[1].toIntOrNull() ?: return time

        return if (minute == 0) {
            "$hour en punto"
        } else {
            "$hour con $minute"
        }
    }
}
