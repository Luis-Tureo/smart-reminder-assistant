package com.luistureo.voicereminderapp.data.migration

import org.junit.Assert.assertEquals
import org.junit.Test

class RemovedModuleCleanupTest {

    @Test
    fun usesLegacyIdentifiersWhenCancellingRetiredArtifacts() {
        assertEquals(300_124, RemovedModuleCleanup.loanAlarmRequestCode(12, 4))
        assertEquals(500_346, RemovedModuleCleanup.routineAlarmRequestCode(34, 6))
        assertEquals(400_012, RemovedModuleCleanup.loanNotificationId(12))
        assertEquals(500_343, RemovedModuleCleanup.routineNotificationId(34, 3))
    }
}
