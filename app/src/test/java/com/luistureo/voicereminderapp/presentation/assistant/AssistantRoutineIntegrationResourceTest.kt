package com.luistureo.voicereminderapp.presentation.assistant

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantRoutineIntegrationResourceTest {
    @Test
    fun routineFlowReusesExistingSpeakerBubbleAndVisualStates() {
        val activity = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/assistant/AssistantActivity.kt"
        ).readText()

        assertTrue(activity.contains("VoiceAssistantSpeaker"))
        assertTrue(activity.contains("AssistantDialogueBubbleView"))
        assertTrue(activity.contains("AssistantVisualState.LISTENING"))
        assertTrue(activity.contains("AssistantVisualState.THINKING"))
        assertTrue(activity.contains("AssistantVisualState.SPEAKING"))
        assertTrue(activity.contains("AssistantVisualState.SUCCESS"))
        assertTrue(activity.contains("AssistantVisualState.ERROR"))
        assertFalse(activity.contains("ReminderVoiceAssistant"))
    }

    @Test
    fun taskIsConfirmedOnlyAfterButtonOrExplicitVoicePolicy() {
        val activity = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/assistant/AssistantActivity.kt"
        ).readText()

        assertTrue(activity.contains("routineCompletedButton.setOnClickListener"))
        assertTrue(activity.contains("RoutineVoiceConfirmationPolicy.confirmsCompletion"))
        assertTrue(activity.contains("routineSessionViewModel.confirmCurrentTask()"))
    }

    @Test
    fun guidedNotificationStartsAssistantWithoutNotificationTrampoline() {
        val notifications = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/core/notification/NotificationHelper.kt"
        ).readText()
        val plan = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/core/routine/RoutineNotificationPlanFactory.kt"
        ).readText()

        assertTrue(notifications.contains("PendingIntent.getActivity"))
        assertTrue(notifications.contains("AssistantActivity::class.java"))
        assertTrue(plan.contains("RoutineAssistantMode.SIMPLE_DISPLAY"))
        assertTrue(plan.contains("startWithAssistant"))
    }

    private fun projectFile(path: String): File {
        val fromRoot = File(path)
        if (fromRoot.exists()) return fromRoot
        return File(path.removePrefix("app/"))
    }
}
