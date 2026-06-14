package com.luistureo.voicereminderapp.core.alarm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactAlarmPermissionPolicyTest {

    @Test
    fun showsGuidanceOnAndroid12AndNewerWhenExactAlarmIsNotAllowed() {
        assertTrue(
            ExactAlarmPermissionPolicy.shouldShowGuidance(
                sdkInt = 31,
                android12SdkInt = 31,
                canScheduleExactAlarms = false
            )
        )
    }

    @Test
    fun doesNotShowGuidanceWhenPermissionIsAllowedOrSdkIsOlder() {
        assertFalse(
            ExactAlarmPermissionPolicy.shouldShowGuidance(
                sdkInt = 31,
                android12SdkInt = 31,
                canScheduleExactAlarms = true
            )
        )
        assertFalse(
            ExactAlarmPermissionPolicy.shouldShowGuidance(
                sdkInt = 30,
                android12SdkInt = 31,
                canScheduleExactAlarms = false
            )
        )
    }
}
