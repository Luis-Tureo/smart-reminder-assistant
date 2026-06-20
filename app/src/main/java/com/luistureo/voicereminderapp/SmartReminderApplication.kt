package com.luistureo.voicereminderapp

import android.app.Application
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarAutoSyncScheduler

class SmartReminderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CalendarAutoSyncScheduler.schedulePeriodic(this)
        CalendarAutoSyncScheduler.enqueueNow(this, trigger = "app_open")
    }
}
