package com.luistureo.voicereminderapp.core.recovery

import android.content.Context
import com.luistureo.voicereminderapp.data.local.database.ReminderDatabase
import com.luistureo.voicereminderapp.data.repository.recovery.RecoveryRepositoryImpl
import com.luistureo.voicereminderapp.domain.recovery.repository.RecoveryRepository

object RecoveryRuntime {
    @Volatile
    private var overrideFactory: ((Context) -> RecoveryRepository)? = null

    fun installFactory(factory: ((Context) -> RecoveryRepository)?) {
        overrideFactory = factory
    }

    fun repository(context: Context): RecoveryRepository {
        overrideFactory?.let { return it(context.applicationContext) }
        return RecoveryRepositoryImpl(
            ReminderDatabase.getDatabase(context.applicationContext).recoveryDao()
        )
    }
}
