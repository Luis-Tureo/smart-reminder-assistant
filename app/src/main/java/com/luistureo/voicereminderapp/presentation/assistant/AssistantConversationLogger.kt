package com.luistureo.voicereminderapp.presentation.assistant

import android.util.Log
import com.luistureo.voicereminderapp.domain.model.ReminderDraft

object AssistantConversationLogger {
    private const val TAG = "AssistantConversation"

    fun log(message: String) {
        runCatching {
            Log.d(TAG, message)
        }
        println("$TAG: $message")
    }

    fun logParsedInput(
        parsedDate: String?,
        parsedTime: String?,
        isUrgent: Boolean
    ) {
        log(
            "Parsed input: date=${parsedDate ?: "missing"}, " +
                "time=${parsedTime ?: "missing"}, urgent=$isUrgent"
        )
    }

    fun logSlotState(
        draft: ReminderDraft?,
        missingSlot: AssistantMissingSlot
    ) {
        log(
            "Slot state: titleDetail=${!draft?.text.isNullOrBlank()}, " +
                "date=${draft?.date ?: "missing"}, " +
                "time=${draft?.time ?: "missing"}, " +
                "urgent=${draft?.isUrgent == true}, " +
                "recurrence=${draft?.recurrence ?: "none"}, " +
                "missingSlot=$missingSlot"
        )
    }
}
