package com.luistureo.voicereminderapp

import android.app.Application
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarAutoSyncScheduler
import com.luistureo.voicereminderapp.data.migration.RemovedModuleCleanup

class SmartReminderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RemovedModuleCleanup.runOnce(this)
        CalendarAutoSyncScheduler.schedulePeriodic(this)
        CalendarAutoSyncScheduler.enqueueNow(this, trigger = "app_open")
    }
}
