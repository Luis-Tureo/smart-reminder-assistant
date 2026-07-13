package com.luistureo.voicereminderapp.presentation.recovery

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.luistureo.voicereminderapp.core.recovery.RecoveryPreferenceStore
import com.luistureo.voicereminderapp.core.recovery.RecoveryRuntime
import com.luistureo.voicereminderapp.core.recovery.RecoveryScheduler
import com.luistureo.voicereminderapp.core.recovery.RecoveryNotificationHelper

class RecoveryViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = RecoveryViewModel(
        repository = RecoveryRuntime.repository(appContext),
        preferences = RecoveryPreferenceStore(appContext),
        scheduler = RecoveryScheduler(appContext),
        notificationHelper = RecoveryNotificationHelper(appContext)
    ) as T
}
