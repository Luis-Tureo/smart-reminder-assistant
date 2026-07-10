package com.luistureo.voicereminderapp.core.routine

import com.luistureo.voicereminderapp.domain.routine.repository.RoutineRepository
import com.luistureo.voicereminderapp.domain.routine.service.RoutineSuggestionEngine
import com.luistureo.voicereminderapp.domain.routine.service.RoutineSuggestionPolicy
import java.time.LocalDate
import java.time.LocalTime

class RoutineSuggestionCoordinator(
    private val repository: RoutineRepository,
    private val preferences: RoutinePreferenceStore,
    private val engine: RoutineSuggestionEngine = RoutineSuggestionEngine()
) {
    suspend fun evaluate(today: LocalDate = LocalDate.now(), now: LocalTime = LocalTime.now()):
        com.luistureo.voicereminderapp.domain.routine.model.RoutineSuggestion? {
        val settings = preferences.getSuggestionSettings()
        if (!settings.enabled || repository.countSuggestionsForDay(today) >= 1) return null
        val routines = repository.getRoutines().filter { it.enabled }
        for (routine in routines) {
            val history = repository.getHistoryBetween(today.minusDays(30), today)
                .filter { it.routineId == routine.id }
            val suggestions = engine.evaluate(
                routine,
                repository.getTasks(routine.id),
                history,
                today,
                preferences.recentPostponeCount(routine.id)
            )
            for (suggestion in suggestions) {
                val latest = repository.getLatestSuggestion(routine.id, suggestion.type.name)
                if (RoutineSuggestionPolicy.canCreate(
                        settings, now, repository.countSuggestionsForDay(today), latest, today
                    )) {
                    repository.saveSuggestion(suggestion)
                    return suggestion
                }
            }
        }
        return null
    }
}
