package com.luistureo.voicereminderapp.core.speech

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.result.ActivityResultLauncher

class SpeechRecognizerManager {

    // Inicia el reconocedor de voz del sistema
    fun startRecognition(
        launcher: ActivityResultLauncher<Intent>
    ) {
        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CL")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your reminder")
        }

        launcher.launch(speechIntent)
    }

    // Extrae el texto reconocido desde el resultado del intent
    fun extractResult(resultCode: Int, data: Intent?): String? {
        if (resultCode != Activity.RESULT_OK) return null

        val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        return results?.firstOrNull()
    }
}