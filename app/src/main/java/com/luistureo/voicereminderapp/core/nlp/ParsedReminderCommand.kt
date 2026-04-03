package com.luistureo.voicereminderapp.core.nlp

data class ParsedReminderCommand(
    val isReminder: Boolean,
    val reminderText: String? = null,
    val detectedDateText: String? = null,
    val detectedTimeText: String? = null,
    val originalMessage: String
)