package com.luistureo.voicereminderapp.core.routine

import android.content.Context
import com.luistureo.voicereminderapp.core.notification.NotificationHelper
import com.luistureo.voicereminderapp.core.speech.VoiceAssistantSpeaker
import com.luistureo.voicereminderapp.domain.routine.model.Routine
import com.luistureo.voicereminderapp.domain.routine.model.RoutineAssistantMode
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionState
import java.time.LocalDate

class RoutineMotivationCoordinator(private val context: Context) {
    fun deliver(
        routine: Routine,
        state: RoutineExecutionState,
        date: LocalDate,
        allowVoice: Boolean = true
    ) {
        RoutineAssistantEventHooks.emit(
            RoutineAssistantEvent(
                routineId = routine.id,
                assistantMode = routine.assistantMode,
                state = state,
                occurredAtEpochMillis = System.currentTimeMillis()
            )
        )
        val preferredName = RoutinePreferenceStore(context).getPreferredName()
        val preferenceStore = RoutinePreferenceStore(context)
        val messageEnabled = when (state) {
            RoutineExecutionState.COMPLETED -> preferenceStore.completionMessagesEnabled()
            RoutineExecutionState.PARTIALLY_COMPLETED -> preferenceStore.partialMessagesEnabled()
            RoutineExecutionState.NOT_COMPLETED,
            RoutineExecutionState.SKIPPED -> preferenceStore.missedMessagesEnabled()
            else -> true
        }
        if (!messageEnabled) return
        val message = RoutineNotificationPlanFactory.motivationMessage(
            routine,
            state,
            preferredName
        ) ?: return
        RoutineNotificationPlanFactory.motivation(routine, state, preferredName)?.let { plan ->
            NotificationHelper(context).showRoutineNotification(
                plan = plan,
                routineId = routine.id,
                dateEpochDay = date.toEpochDay(),
                alarmType = RoutineAlarmType.PENDING_TASKS
            )
        }
        if (
            allowVoice &&
            routine.voiceEnabled &&
            routine.assistantMode != RoutineAssistantMode.SIMPLE_DISPLAY
        ) {
            val speaker = VoiceAssistantSpeaker(context)
            speaker.speakText(message) { speaker.shutdown() }
        }
    }
}
