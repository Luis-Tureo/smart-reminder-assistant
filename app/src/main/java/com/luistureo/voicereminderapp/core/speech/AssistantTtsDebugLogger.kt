package com.luistureo.voicereminderapp.core.speech

import android.util.Log

object AssistantTtsDebugLogger {
    private const val TAG = "AssistantTts"

    fun log(message: String) {
        runCatching {
            Log.d(TAG, message)
        }
        println("$TAG: $message")
    }
}
