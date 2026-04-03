package com.luistureo.voicereminderapp.core.nlp

class ReminderCommandParser(
    private val entityExtractor: ReminderEntityExtractor,
    private val intentDetector: ReminderIntentDetector,
    private val textCleaner: ReminderTextCleaner
) {

    suspend fun parse(
        userMessage: String,
        referenceTimeMillis: Long
    ): ParsedReminderCommand {
        val isReminder = intentDetector.isReminderIntent(userMessage)

        if (!isReminder) {
            return ParsedReminderCommand(
                isReminder = false,
                originalMessage = userMessage
            )
        }

        val annotations = entityExtractor.extractDateTimeEntities(
            text = userMessage,
            referenceTimeMillis = referenceTimeMillis
        )

        val reminderText = textCleaner.removeDetectedSpans(
            originalText = userMessage,
            annotations = annotations
        ).ifBlank { null }

        val dateTimeFragments = annotations.map { annotation ->
            userMessage.substring(annotation.start, annotation.end)
        }

        val mergedTemporalText = dateTimeFragments.joinToString(" ").trim().ifBlank { null }

        return ParsedReminderCommand(
            isReminder = true,
            reminderText = reminderText,
            detectedDateText = mergedTemporalText,
            detectedTimeText = mergedTemporalText,
            originalMessage = userMessage
        )
    }
}