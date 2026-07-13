package com.luistureo.voicereminderapp.presentation.recovery

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryUiResourceTest {
    @Test
    fun homeCardIsAfterNutritionAndOpensWorkingDashboard() {
        val layout = projectFile("app/src/main/res/layout/activity_main.xml").readText()
        val main = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/MainActivity.kt"
        ).readText()
        val strings = projectFile("app/src/main/res/values/wellness_home_strings.xml").readText()

        assertTrue(layout.contains("@+id/cardRecovery"))
        assertTrue(layout.indexOf("@+id/cardDailyRoutines") < layout.indexOf("@+id/cardNutrition"))
        assertTrue(layout.indexOf("@+id/cardNutrition") < layout.indexOf("@+id/cardRecovery"))
        assertTrue(main.contains("R.id.cardRecovery"))
        assertTrue(main.contains("Intent(this, RecoveryDashboardActivity::class.java)"))
        assertTrue(strings.contains("Dejar adicciones"))
        assertTrue(strings.contains("Seguimiento y apoyo personal"))
    }

    @Test
    fun manifestRegistersAllPrivateRecoveryScreensAndReceivers() {
        val manifest = projectFile("app/src/main/AndroidManifest.xml").readText()
        val components = listOf(
            "RecoveryDashboardActivity",
            "RecoveryGoalEditorActivity",
            "RecoveryCheckInActivity",
            "RecoverySupportActivity",
            "RecoveryToolsActivity",
            "RecoveryContactsActivity",
            "RecoveryStatisticsActivity",
            "RecoverySettingsActivity",
            "RecoveryAlarmReceiver",
            "RecoveryActionReceiver"
        )

        components.forEach { assertTrue("Falta $it", manifest.contains(it)) }
        val recoveryDeclarations = manifest.lines().filter { it.contains(".recovery.") }
        assertTrue(recoveryDeclarations.isNotEmpty())
        assertTrue(manifest.contains("android:theme=\"@style/Theme.VoiceReminderApp.Recovery\""))
    }

    @Test
    fun dashboardExposesGoalCheckInSupportToolsContactsStatisticsAndSettings() {
        val layout = projectFile(
            "app/src/main/res/layout/activity_recovery_dashboard.xml"
        ).readText()

        listOf(
            "spinnerRecoveryGoals",
            "btnRecoveryNewGoal",
            "btnRecoveryCheckIn",
            "btnRecoverySupportNow",
            "btnRecoveryTools",
            "btnRecoveryContacts",
            "btnRecoveryStatistics",
            "btnRecoverySettings",
            "recyclerRecoveryRecent"
        ).forEach { assertTrue("Falta $it", layout.contains("@+id/$it")) }
        assertTrue(layout.contains("@string/recovery_disclaimer"))
    }

    @Test
    fun goalAndCheckInKeepOptionalFieldsCollapsed() {
        val goal = projectFile(
            "app/src/main/res/layout/activity_recovery_goal_editor.xml"
        ).readText()
        val checkIn = projectFile(
            "app/src/main/res/layout/activity_recovery_check_in.xml"
        ).readText()

        assertTrue(goal.contains("@+id/containerRecoveryGoalAdditional"))
        assertTrue(checkIn.contains("@+id/containerRecoveryCheckInAdditional"))
        assertTrue(goal.substringAfter("@+id/containerRecoveryGoalAdditional")
            .substringBefore('>').contains("android:visibility=\"gone\""))
        assertTrue(checkIn.substringAfter("@+id/containerRecoveryCheckInAdditional")
            .substringBefore('>').contains("android:visibility=\"gone\""))
        assertTrue(checkIn.contains("@+id/radioRecoveryAchieved"))
        assertTrue(checkIn.contains("@+id/radioRecoveryManaged"))
        assertTrue(checkIn.contains("@+id/radioRecoveryLapse"))
        assertTrue(checkIn.contains("@+id/radioRecoverySkip"))
    }

    @Test
    fun supportShowsConfiguredActionsBeforeFixedSafeActions() {
        val layout = projectFile(
            "app/src/main/res/layout/activity_recovery_support.xml"
        ).readText()

        val configured = layout.indexOf("@+id/containerRecoveryConfiguredActions")
        assertTrue(configured > 0)
        assertTrue(configured < layout.indexOf("@+id/btnRecoveryRememberReasons"))
        assertTrue(configured < layout.indexOf("@+id/btnRecoveryContactSupport"))
        assertTrue(layout.contains("@+id/btnRecoveryWait"))
        assertTrue(layout.contains("@+id/btnRecoveryRecordFeeling"))
    }

    @Test
    fun settingsExposePrivacyIndependentVoiceBubbleAllRemindersAndDeletionModes() {
        val layout = projectFile(
            "app/src/main/res/layout/activity_recovery_settings.xml"
        ).readText()
        val source = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/recovery/RecoverySettingsActivity.kt"
        ).readText()

        assertTrue(layout.contains("@+id/radioRecoveryDiscreet"))
        assertTrue(layout.contains("@+id/radioRecoveryFull"))
        assertTrue(layout.contains("@+id/switchRecoveryVoice"))
        assertTrue(layout.contains("@+id/switchRecoveryBubble"))
        assertTrue(layout.contains("@+id/spinnerRecoveryReminderType"))
        assertTrue(source.contains("RecoveryReminderType.entries"))
        assertTrue(source.contains("DELETE_KEEP_ANONYMOUS_HISTORY"))
        assertTrue(source.contains("DELETE_ALL"))
        assertTrue(source.contains("RecoveryDeletionMode.ARCHIVE"))
    }

    private fun projectFile(path: String): File {
        val fromRoot = File(path)
        if (fromRoot.exists()) return fromRoot
        return File(path.removePrefix("app/"))
    }
}
