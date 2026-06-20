package com.luistureo.voicereminderapp.core.calendar.unified

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarAuthManager
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarErrorCode
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarReminderSynchronizer
import com.luistureo.voicereminderapp.core.calendar.microsoft.MicrosoftCalendarAuthController
import com.luistureo.voicereminderapp.core.calendar.microsoft.MicrosoftCalendarAuthProvider
import com.luistureo.voicereminderapp.core.calendar.microsoft.MicrosoftCalendarErrorCode
import com.luistureo.voicereminderapp.core.calendar.microsoft.MicrosoftGraphApiException
import com.luistureo.voicereminderapp.data.local.database.ReminderDatabase
import com.luistureo.voicereminderapp.data.repository.ReminderRepositoryImpl
import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CalendarAutoSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    private val stateStore = CalendarAutoSyncStateStore(appContext)
    private val repository = ReminderRepositoryImpl(
        ReminderDatabase.getDatabase(appContext).reminderDao()
    )
    private val googleAuthManager = GoogleCalendarAuthManager(appContext)
    private val googleSynchronizer = GoogleCalendarReminderSynchronizer(
        context = appContext,
        reminderRepository = repository,
        authManager = googleAuthManager
    )
    private val microsoftAuthController = MicrosoftCalendarAuthProvider.get(appContext)
    private val unifiedSynchronizer = UnifiedCalendarSynchronizer(
        context = appContext,
        reminderRepository = repository,
        googleCalendarSynchronizer = googleSynchronizer
    )

    override suspend fun doWork(): Result = executionMutex.withLock {
        val trigger = inputData.getString(KEY_TRIGGER) ?: TRIGGER_PERIODIC
        CalendarSyncLogger.autoSyncStarted(trigger)
        val now = System.currentTimeMillis()
        val start = LocalDate.now().minusDays(30)
            .atStartOfDay(ZoneId.systemDefault()).toInstant()
        val end = start.plus(SYNC_WINDOW)

        syncGoogle(now, start, end)
        microsoftAuthController.awaitConnectionRefresh()
        syncMicrosoft(now, start, end)
        CalendarSyncLogger.autoSyncFinished(trigger)
        Result.success()
    }

    private suspend fun syncGoogle(
        nowEpochMillis: Long,
        timeMin: java.time.Instant,
        timeMax: java.time.Instant
    ) {
        val decision = CalendarAutoSyncPolicy.decision(
            hasSession = googleAuthManager.hasConnectedSession(),
            syncEnabled = googleAuthManager.isSyncEnabled,
            state = stateStore.get(CalendarProvider.GOOGLE_CALENDAR),
            nowEpochMillis = nowEpochMillis
        )
        if (decision != CalendarAutoSyncDecision.RUN) {
            CalendarSyncLogger.autoSyncSkipped(CalendarProvider.GOOGLE_CALENDAR, decision.name)
            return
        }
        stateStore.recordAttempt(CalendarProvider.GOOGLE_CALENDAR, nowEpochMillis)
        runCatching {
            val importedCount = googleSynchronizer.importEvents(timeMin, timeMax)
            val summary = googleSynchronizer.syncPendingReminders()
            summary.failureCode?.let { throw CalendarAutoSyncGoogleFailure(it) }
            importedCount to summary
        }.onSuccess { (importedCount, summary) ->
            googleAuthManager.clearConnectionError()
            stateStore.recordSuccess(CalendarProvider.GOOGLE_CALENDAR, System.currentTimeMillis())
            CalendarSyncLogger.autoSyncProviderSuccess(
                provider = CalendarProvider.GOOGLE_CALENDAR,
                importedCount = importedCount,
                updatedCount = summary.syncedCount
            )
        }.onFailure { error ->
            val code = (error as? CalendarAutoSyncGoogleFailure)?.code
                ?: GoogleCalendarErrorCode.fromSyncFailure(error)
            googleAuthManager.markConnectionError(code)
            val blockedUntil = stateStore.recordFailure(
                CalendarProvider.GOOGLE_CALENDAR,
                code.value,
                System.currentTimeMillis()
            )
            CalendarSyncLogger.autoSyncProviderFailure(
                CalendarProvider.GOOGLE_CALENDAR,
                code.value,
                blockedUntil
            )
        }
    }

    private suspend fun syncMicrosoft(
        nowEpochMillis: Long,
        timeMin: java.time.Instant,
        timeMax: java.time.Instant
    ) {
        val decision = CalendarAutoSyncPolicy.decision(
            hasSession = microsoftAuthController.hasSession,
            syncEnabled = microsoftAuthController.isSyncEnabled,
            state = stateStore.get(CalendarProvider.MICROSOFT_CALENDAR),
            nowEpochMillis = nowEpochMillis
        )
        if (decision != CalendarAutoSyncDecision.RUN) {
            CalendarSyncLogger.autoSyncSkipped(
                CalendarProvider.MICROSOFT_CALENDAR,
                decision.name
            )
            return
        }
        stateStore.recordAttempt(CalendarProvider.MICROSOFT_CALENDAR, nowEpochMillis)
        runCatching {
            val summary = unifiedSynchronizer.syncMicrosoftCalendar(timeMin, timeMax)
            summary.microsoftFailureCode?.let {
                throw CalendarAutoSyncMicrosoftFailure(
                    it,
                    summary.microsoftRetryAfterMillis
                )
            }
            summary
        }.onSuccess { summary ->
            microsoftAuthController.clearConnectionError()
            stateStore.recordSuccess(
                CalendarProvider.MICROSOFT_CALENDAR,
                System.currentTimeMillis()
            )
            CalendarSyncLogger.autoSyncProviderSuccess(
                provider = CalendarProvider.MICROSOFT_CALENDAR,
                importedCount = summary.importedCount,
                updatedCount = summary.syncedCount - summary.importedCount
            )
        }.onFailure { error ->
            val code = (error as? CalendarAutoSyncMicrosoftFailure)?.code
                ?: MicrosoftCalendarErrorCode.fromFailure(error)
            microsoftAuthController.markConnectionError(code)
            val retryAfterMillis = (error as? CalendarAutoSyncMicrosoftFailure)
                ?.retryAfterMillis ?: generateSequence(error) { it.cause }
                .filterIsInstance<MicrosoftGraphApiException>()
                .firstOrNull()?.retryAfterSeconds?.times(1_000L)
            val blockedUntil = stateStore.recordFailure(
                provider = CalendarProvider.MICROSOFT_CALENDAR,
                errorCode = code.value,
                nowEpochMillis = System.currentTimeMillis(),
                retryAfterMillis = retryAfterMillis
            )
            CalendarSyncLogger.autoSyncProviderFailure(
                CalendarProvider.MICROSOFT_CALENDAR,
                code.value,
                blockedUntil
            )
        }
    }

    private suspend fun MicrosoftCalendarAuthController.awaitConnectionRefresh(): Boolean =
        suspendCancellableCoroutine { continuation ->
            refreshConnectionState { connected ->
                if (continuation.isActive) continuation.resume(connected)
            }
        }

    private class CalendarAutoSyncGoogleFailure(val code: GoogleCalendarErrorCode) :
        IllegalStateException(code.value)

    private class CalendarAutoSyncMicrosoftFailure(
        val code: MicrosoftCalendarErrorCode,
        val retryAfterMillis: Long?
    ) :
        IllegalStateException(code.value)

    companion object {
        const val KEY_TRIGGER = "calendar_auto_sync_trigger"
        const val TRIGGER_PERIODIC = "periodic"
        private val SYNC_WINDOW: Duration = Duration.ofDays(210)
        private val executionMutex = Mutex()
    }
}

object CalendarAutoSyncScheduler {
    private const val PERIODIC_WORK_NAME = "calendar_auto_sync_periodic"
    private const val IMMEDIATE_WORK_NAME = "calendar_auto_sync_immediate"

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<CalendarAutoSyncWorker>(
            CalendarAutoSyncPolicy.PERIODIC_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        )
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .setInputData(workDataOf(CalendarAutoSyncWorker.KEY_TRIGGER to "periodic"))
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        CalendarSyncLogger.autoSyncScheduled(CalendarAutoSyncPolicy.PERIODIC_INTERVAL_MINUTES)
    }

    fun enqueueNow(context: Context, trigger: String) {
        val request = OneTimeWorkRequestBuilder<CalendarAutoSyncWorker>()
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .setInputData(workDataOf(CalendarAutoSyncWorker.KEY_TRIGGER to trigger))
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
