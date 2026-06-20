package com.luistureo.voicereminderapp.core.speech

import android.util.Log
import com.luistureo.voicereminderapp.BuildConfig

object AssistantTtsDebugLogger {
    private const val TAG = "AssistantTts"

    fun log(message: String) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            Log.d(TAG, message)
        }
    }
}
