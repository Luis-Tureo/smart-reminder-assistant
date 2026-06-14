package com.luistureo.voicereminderapp.core.alarm

object ExactAlarmPermissionPolicy {
    fun shouldShowGuidance(
        sdkInt: Int,
        android12SdkInt: Int,
        canScheduleExactAlarms: Boolean
    ): Boolean {
        return sdkInt >= android12SdkInt && !canScheduleExactAlarms
    }
}
