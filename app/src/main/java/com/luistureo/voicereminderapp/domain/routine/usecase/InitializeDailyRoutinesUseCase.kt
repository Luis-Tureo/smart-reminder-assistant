package com.luistureo.voicereminderapp.domain.routine.usecase

import com.luistureo.voicereminderapp.domain.routine.factory.DefaultRoutineFactory
import com.luistureo.voicereminderapp.domain.routine.factory.DefaultRoutineTemplateFactory
import com.luistureo.voicereminderapp.domain.routine.repository.RoutineRepository

class InitializeDailyRoutinesUseCase(
    private val repository: RoutineRepository,
    private val nowProvider: () -> Long = System::currentTimeMillis
) {
    suspend operator fun invoke(): Boolean = repository.initializeDefaults(
        routines = DefaultRoutineFactory.create(nowProvider()),
        templates = DefaultRoutineTemplateFactory.create()
    )
}
