package com.luistureo.voicereminderapp.domain.routine.service

import com.luistureo.voicereminderapp.domain.routine.model.RoutineSuggestion
import com.luistureo.voicereminderapp.domain.routine.model.RoutineSuggestionSettings
import java.time.LocalDate
import java.time.LocalTime

object RoutineSuggestionPolicy {
    fun canCreate(
        settings: RoutineSuggestionSettings,
        now: LocalTime,
        suggestionsCreatedToday: Int,
        latestSameSuggestion: RoutineSuggestion?,
        today: LocalDate,
        assistantSpeechActive: Boolean = false,
        taskInteractionActive: Boolean = false
    ): Boolean {
        if (!settings.enabled || suggestionsCreatedToday >= 1) return false
        if (assistantSpeechActive || taskInteractionActive) return false
        if (now.isBefore(LocalTime.of(settings.preferredHour, settings.preferredMinute))) return false
        if (latestSameSuggestion?.active == true) return false
        val lastDismissed = latestSameSuggestion?.dismissedAtEpochDay ?: return true
        return today.toEpochDay() - lastDismissed >= 7
    }

    fun output(settings: RoutineSuggestionSettings, assistantSpeechActive: Boolean): Pair<Boolean, Boolean> =
        if (assistantSpeechActive) false to false else settings.showBubble to settings.speak
}
