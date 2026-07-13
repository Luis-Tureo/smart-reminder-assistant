package com.luistureo.voicereminderapp.core.recovery

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryPrivacyAndIntegrationTest {
    @Test
    fun notificationUsesDiscreetDefaultPrivateVisibilityAndRequiredActions() {
        val helper = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/core/recovery/RecoveryNotificationHelper.kt"
        ).readText()
        val strings = projectFile(
            "app/src/main/res/values/recovery_strings.xml"
        ).readText()

        assertTrue(strings.contains("Tienes una revisión personal pendiente."))
        assertTrue(helper.contains("RecoveryNotificationTextMode.FULL"))
        assertTrue(helper.contains("VISIBILITY_PRIVATE"))
        assertTrue(helper.contains("setPublicVersion"))
        assertTrue(helper.contains("RecoveryNotificationAction.REGISTER"))
        assertTrue(helper.contains("RecoveryNotificationAction.SUPPORT"))
        assertTrue(helper.contains("RecoveryNotificationAction.POSTPONE"))
        assertTrue(helper.contains("RecoveryNotificationAction.SKIP"))
        assertFalse(helper.contains("goal.title"))
        assertFalse(helper.contains("goal.category"))
        assertFalse(helper.contains("personalReason"))
        assertFalse(helper.contains("cravingIntensity"))
    }

    @Test
    fun preferenceDefaultsAreDiscreetWithVoiceOffAndBubbleIndependent() {
        val source = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/core/recovery/RecoveryPreferenceStore.kt"
        ).readText()

        assertTrue(source.contains("RecoveryNotificationTextMode.DISCREET.name"))
        assertTrue(source.contains("getBoolean(KEY_VOICE, false)"))
        assertTrue(source.contains("getBoolean(KEY_BUBBLE, true)"))
        assertTrue(source.contains("setVoiceEnabled"))
        assertTrue(source.contains("setBubbleEnabled"))
    }

    @Test
    fun assistantForcesLocalTtsAndNeverStartsRecognition() {
        val coordinator = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/core/wellness/WellnessAssistantCoordinator.kt"
        ).readText()
        val recoverySources = recoverySourceText()

        assertTrue(coordinator.contains("VoiceAssistantSpeaker"))
        assertTrue(coordinator.contains("isRemoteTtsEnabled = false"))
        assertTrue(coordinator.contains("remoteBackendUrl = \"\""))
        assertFalse(recoverySources.contains("SpeechRecognizer"))
        assertFalse(recoverySources.contains("startListening"))
    }

    @Test
    fun contactsRequireConfirmationAndNeverCallOrMessageAutomatically() {
        val contacts = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/recovery/RecoveryContactsActivity.kt"
        ).readText()
        val actionReceiver = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/core/recovery/RecoveryActionReceiver.kt"
        ).readText()

        assertTrue(contacts.contains("confirmCall"))
        assertTrue(contacts.contains("confirmSms"))
        assertTrue(contacts.contains("AlertDialog.Builder"))
        assertTrue(contacts.contains("Intent.ACTION_DIAL"))
        assertTrue(contacts.contains("Intent.ACTION_SENDTO"))
        assertTrue(contacts.contains("ContactsContract.CommonDataKinds.Phone.CONTENT_URI"))
        assertFalse(contacts.contains("Intent.ACTION_CALL"))
        assertFalse(contacts.contains("android.permission.CALL_PHONE"))
        assertFalse(actionReceiver.contains("ACTION_DIAL"))
        assertFalse(actionReceiver.contains("ACTION_SENDTO"))
        assertFalse(actionReceiver.contains("ContactsContract"))
    }

    @Test
    fun recoveryHasNoCalendarSyncNetworkExternalAiOrPrivateLogging() {
        val sources = recoverySourceText()
        val forbidden = listOf(
            "GoogleCalendar",
            "MicrosoftCalendar",
            "UnifiedCalendar",
            "CalendarSynchronizer",
            "okhttp",
            "Retrofit",
            "RemoteAssistantTtsClient",
            "Firebase",
            "http://",
            "https://",
            "Log.d(",
            "Log.i(",
            "Log.e(",
            "Timber."
        )

        forbidden.forEach { token -> assertFalse("Integración prohibida: $token", sources.contains(token)) }
    }

    @Test
    fun roomModelContainsSevenEntitiesAndPreservesAnonymousHistoryWithSetNull() {
        val entities = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/data/local/entity/recovery/RecoveryEntities.kt"
        ).readText()
        val dao = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/data/local/dao/recovery/RecoveryDao.kt"
        ).readText()

        listOf(
            "RecoveryGoalEntity",
            "RecoveryCheckInEntity",
            "RecoveryTriggerEntity",
            "RecoveryHelpfulActionEntity",
            "RecoverySupportContactEntity",
            "RecoveryMilestoneEntity",
            "RecoveryReminderEntity"
        ).forEach { assertTrue("Falta $it", entities.contains("data class $it")) }
        assertTrue(entities.contains("onDelete = ForeignKey.SET_NULL"))
        assertTrue(dao.contains("anonymizeCheckIns"))
        assertTrue(dao.contains("cravingIntensity = NULL"))
        assertTrue(dao.contains("helpfulAction = NULL"))
        assertTrue(dao.contains("deleteGoalKeepingAnonymousHistory"))
        assertTrue(dao.contains("deleteGoalAndHistory"))
    }

    @Test
    fun checkInUseCaseLoadsExistingDailyRowBeforeUpsert() {
        val useCases = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/domain/recovery/usecase/RecoveryUseCases.kt"
        ).readText()
        val repository = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/data/repository/recovery/RecoveryRepositoryImpl.kt"
        ).readText()

        assertTrue(useCases.contains("repository.getCheckIn(checkIn.goalHistoryKey, checkIn.date)"))
        assertTrue(useCases.contains("id = existing?.id ?: checkIn.id"))
        assertTrue(repository.contains("dao.upsertCheckIn"))
    }

    @Test
    fun lapseFlowUsesSupportiveTextAndOffersToolsAndReminderReview() {
        val source = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/recovery/RecoveryCheckInActivity.kt"
        ).readText()
        val strings = projectFile("app/src/main/res/values/recovery_strings.xml").readText()

        assertTrue(strings.contains("Un día difícil no elimina todo tu avance."))
        assertTrue(strings.contains("Tu avance anterior sigue siendo importante."))
        assertTrue(source.contains("showLapseReflection"))
        assertTrue(source.contains("showRestartDecision"))
        assertTrue(source.contains("RecoveryToolsActivity::class.java"))
        assertTrue(source.contains("RecoverySettingsActivity::class.java"))
        assertFalse(strings.contains("Fracasaste"))
        assertFalse(strings.contains("Perdiste todo"))
        assertFalse(strings.contains("Volviste al principio"))
    }

    private fun recoverySourceText(): String {
        val roots = listOf(
            "app/src/main/java/com/luistureo/voicereminderapp/core/recovery",
            "app/src/main/java/com/luistureo/voicereminderapp/domain/recovery",
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/recovery",
            "app/src/main/java/com/luistureo/voicereminderapp/data/local/dao/recovery",
            "app/src/main/java/com/luistureo/voicereminderapp/data/local/entity/recovery",
            "app/src/main/java/com/luistureo/voicereminderapp/data/mapper/recovery",
            "app/src/main/java/com/luistureo/voicereminderapp/data/repository/recovery"
        )
        return roots.flatMap { path -> projectFile(path).walkTopDown().filter { it.isFile }.toList() }
            .joinToString("\n") { it.readText() }
    }

    private fun projectFile(path: String): File {
        val fromRoot = File(path)
        if (fromRoot.exists()) return fromRoot
        return File(path.removePrefix("app/"))
    }
}
