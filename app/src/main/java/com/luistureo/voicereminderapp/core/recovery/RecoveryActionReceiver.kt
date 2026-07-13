package com.luistureo.voicereminderapp.core.recovery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RecoveryActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra(EXTRA_ACTION)
            ?.let { runCatching { RecoveryNotificationAction.valueOf(it) }.getOrNull() }
            ?: return
        val reminderId = intent.getIntExtra(EXTRA_REMINDER_ID, 0)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        if (notificationId != 0) RecoveryNotificationHelper(context).cancel(
            reminderId.takeIf { it > 0 } ?: notificationId
        )
        if (action != RecoveryNotificationAction.POSTPONE || reminderId <= 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminder = RecoveryRuntime.repository(context).getEnabledReminders()
                    .firstOrNull { it.id == reminderId }
                if (reminder != null) RecoveryScheduler(context).scheduleSnooze(reminder)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "extra_recovery_reminder_id"
        const val EXTRA_ACTION = "extra_recovery_notification_action"
        const val EXTRA_NOTIFICATION_ID = "extra_recovery_notification_id"
    }
}
