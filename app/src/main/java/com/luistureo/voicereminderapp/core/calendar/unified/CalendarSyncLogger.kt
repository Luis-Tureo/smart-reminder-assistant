package com.luistureo.voicereminderapp.core.calendar.unified

import android.util.Log
import com.luistureo.voicereminderapp.BuildConfig
import com.luistureo.voicereminderapp.domain.model.CalendarProvider

object CalendarSyncLogger {
    const val TAG_CALENDAR_SYNC = "CalendarSync"
    const val TAG_CALENDAR_AUTO_SYNC = "CalendarAutoSync"
    const val TAG_GOOGLE_CALENDAR_SYNC = "GoogleCalendarSync"
    const val TAG_MICROSOFT_CALENDAR_SYNC = "MicrosoftCalendarSync"
    const val TAG_CALENDAR_AUTH = "CalendarAuth"
    const val TAG_CALENDAR_UI = "CalendarUi"
    const val TAG_CALENDAR_DUPLICATE_CHECK = "CalendarDuplicateCheck"
    const val TAG_CALENDAR_ERROR = "CalendarError"
    const val TAG_CALENDAR_QUOTA = "CalendarQuota"
    const val TAG_MEETING_LINK = "MeetingLink"

    fun buildMessage(
        provider: CalendarProvider?,
        action: String,
        fields: Map<String, Any?> = emptyMap()
    ): String {
        val baseFields = listOfNotNull(
            provider?.name?.let { "provider=$it" },
            "action=$action"
        )
        val extraFields = fields.mapNotNull { (key, value) ->
            sanitizeValue(key, value)?.let { "$key=$it" }
        }
        return (baseFields + extraFields).joinToString(separator = " ")
    }

    fun syncStarted(
        provider: CalendarProvider?,
        action: String,
        pendingCreateCount: Int = 0,
        pendingUpdateCount: Int = 0,
        pendingDeleteCount: Int = 0
    ) {
        safelyLog {
            Log.d(
                tagFor(provider),
                buildMessage(
                provider = provider,
                action = action,
                fields = mapOf(
                    "phase" to "start",
                    "pendingCreate" to pendingCreateCount,
                    "pendingUpdate" to pendingUpdateCount,
                    "pendingDelete" to pendingDeleteCount
                    )
                )
            )
        }
    }

    fun autoSyncScheduled(intervalMinutes: Long) {
        safelyLog {
            Log.d(
                TAG_CALENDAR_AUTO_SYNC,
                buildMessage(null, "scheduled", mapOf("intervalMinutes" to intervalMinutes))
            )
        }
    }

    fun autoSyncStarted(trigger: String) {
        safelyLog {
            Log.d(
                TAG_CALENDAR_AUTO_SYNC,
                buildMessage(null, "started", mapOf("trigger" to trigger))
            )
        }
    }

    fun autoSyncFinished(trigger: String) {
        safelyLog {
            Log.d(
                TAG_CALENDAR_AUTO_SYNC,
                buildMessage(null, "finished", mapOf("trigger" to trigger))
            )
        }
    }

    fun autoSyncSkipped(provider: CalendarProvider, reason: String) {
        safelyLog {
            Log.d(
                TAG_CALENDAR_AUTO_SYNC,
                buildMessage(provider, "skipped", mapOf("reason" to reason))
            )
        }
    }

    fun autoSyncProviderSuccess(
        provider: CalendarProvider,
        importedCount: Int,
        updatedCount: Int
    ) {
        safelyLog {
            Log.d(
                TAG_CALENDAR_AUTO_SYNC,
                buildMessage(
                    provider,
                    "provider_success",
                    mapOf("imported" to importedCount, "updated" to updatedCount)
                )
            )
        }
    }

    fun autoSyncProviderFailure(
        provider: CalendarProvider,
        errorCode: String,
        blockedUntilEpochMillis: Long
    ) {
        safelyLog {
            Log.e(
                TAG_CALENDAR_ERROR,
                buildMessage(
                    provider,
                    "automatic_sync_failed",
                    mapOf(
                        "errorCode" to errorCode,
                        "blockedUntil" to blockedUntilEpochMillis
                    )
                )
            )
        }
    }

    fun syncFinished(
        provider: CalendarProvider?,
        action: String,
        syncedCount: Int = 0,
        failedCount: Int = 0,
        pendingCount: Int = 0,
        completedDeleteCount: Int = 0,
        importedCount: Int = 0,
        updatedCount: Int = 0,
        skippedDuplicatesCount: Int = 0
    ) {
        safelyLog {
            Log.d(
                tagFor(provider),
                buildMessage(
                provider = provider,
                action = action,
                fields = mapOf(
                    "phase" to "end",
                    "synced" to syncedCount,
                    "failed" to failedCount,
                    "pending" to pendingCount,
                    "completedDelete" to completedDeleteCount,
                    "imported" to importedCount,
                    "updated" to updatedCount,
                    "skippedDuplicates" to skippedDuplicatesCount
                    )
                )
            )
        }
    }

    fun authStarted(provider: CalendarProvider) {
        safelyLog { Log.d(TAG_CALENDAR_AUTH, buildMessage(provider, "auth_started")) }
    }

    fun authResultReceived(provider: CalendarProvider, result: String) {
        safelyLog {
            Log.d(
                TAG_CALENDAR_AUTH,
                buildMessage(provider, "auth_result_received", mapOf("result" to result))
            )
        }
    }

    fun authLaunched(provider: CalendarProvider) {
        safelyLog { Log.d(TAG_CALENDAR_AUTH, buildMessage(provider, "auth_launched")) }
    }

    fun googleConnectedStateSaved() {
        safelyLog {
            Log.d(
                TAG_CALENDAR_AUTH,
                buildMessage(CalendarProvider.GOOGLE_CALENDAR, "connected_state_saved")
            )
        }
    }

    fun googleActivatedStateSaved() {
        safelyLog {
            Log.d(
                TAG_CALENDAR_AUTH,
                buildMessage(CalendarProvider.GOOGLE_CALENDAR, "activated_state_saved")
            )
        }
    }

    fun uiUpdated(provider: CalendarProvider, state: String) {
        safelyLog {
            Log.d(
                TAG_CALENDAR_UI,
                buildMessage(provider, "ui_updated", mapOf("state" to state))
            )
        }
    }

    fun authButtonPressed(provider: CalendarProvider) {
        safelyLog { Log.d(TAG_CALENDAR_UI, buildMessage(provider, "auth_button_pressed")) }
    }

    fun authCancelled(provider: CalendarProvider, reason: String? = null) {
        safelyLog {
            Log.d(
                TAG_CALENDAR_AUTH,
                buildMessage(provider, "auth_cancelled", mapOf("fallbackReason" to reason))
            )
        }
    }

    fun authSuccess(provider: CalendarProvider) {
        safelyLog { Log.d(TAG_CALENDAR_AUTH, buildMessage(provider, "auth_success")) }
    }

    fun providerStateChanged(
        provider: CalendarProvider,
        state: String,
        previousState: String? = null
    ) {
        safelyLog {
            Log.d(
                TAG_CALENDAR_UI,
                buildMessage(
                    provider,
                    "provider_state_changed",
                    mapOf("stateBefore" to previousState, "stateAfter" to state)
                )
            )
        }
    }

    fun deactivate(provider: CalendarProvider, phase: String) {
        safelyLog {
            Log.d(
                TAG_CALENDAR_AUTH,
                buildMessage(provider, "deactivate", mapOf("phase" to phase))
            )
        }
    }

    fun providerPaused(provider: CalendarProvider, sessionPreserved: Boolean) {
        safelyLog {
            Log.d(
                TAG_CALENDAR_AUTH,
                buildMessage(
                    provider,
                    "pause",
                    mapOf("sessionPreserved" to sessionPreserved)
                )
            )
        }
    }

    fun providerActivated(provider: CalendarProvider) {
        safelyLog { Log.d(TAG_CALENDAR_AUTH, buildMessage(provider, "activate")) }
    }

    fun providerDisconnected(provider: CalendarProvider) {
        safelyLog { Log.d(TAG_CALENDAR_AUTH, buildMessage(provider, "disconnect")) }
    }

    fun inlineErrorShown(provider: CalendarProvider, reason: String) {
        safelyLog {
            Log.w(
                TAG_CALENDAR_ERROR,
                buildMessage(
                    provider,
                    "inline_error_shown",
                    mapOf(
                        "errorCode" to reason
                    )
                )
            )
        }
    }

    fun inlineErrorCleared(provider: CalendarProvider) {
        safelyLog { Log.d(TAG_CALENDAR_UI, buildMessage(provider, "inline_error_cleared")) }
    }

    fun retryTapped(provider: CalendarProvider) {
        safelyLog { Log.d(TAG_CALENDAR_UI, buildMessage(provider, "retry_tapped")) }
    }

    fun localGoogleStateCleared(reason: String) {
        safelyLog {
            Log.d(
                TAG_CALENDAR_AUTH,
                buildMessage(
                    CalendarProvider.GOOGLE_CALENDAR,
                    "local_state_cleared",
                    mapOf("fallbackReason" to reason)
                )
            )
        }
    }

    fun googleDisabledFlagCleared() {
        safelyLog {
            Log.d(
                TAG_CALENDAR_AUTH,
                buildMessage(CalendarProvider.GOOGLE_CALENDAR, "disabled_flag_cleared")
            )
        }
    }

    fun googleStaleSessionDetected() {
        safelyLog {
            Log.w(
                TAG_CALENDAR_AUTH,
                buildMessage(CalendarProvider.GOOGLE_CALENDAR, "stale_session_detected")
            )
        }
    }

    fun googleSessionReused() {
        safelyLog {
            Log.d(
                TAG_CALENDAR_AUTH,
                buildMessage(CalendarProvider.GOOGLE_CALENDAR, "session_reused")
            )
        }
    }

    fun googleSyncPaused(sessionPreserved: Boolean) {
        safelyLog {
            Log.d(
                TAG_GOOGLE_CALENDAR_SYNC,
                buildMessage(
                    CalendarProvider.GOOGLE_CALENDAR,
                    "paused_state_saved",
                    mapOf("sessionPreserved" to sessionPreserved)
                )
            )
        }
    }

    fun googleButtonTapped(stateBefore: String, action: String) {
        safelyLog {
            Log.d(
                TAG_CALENDAR_UI,
                buildMessage(
                    CalendarProvider.GOOGLE_CALENDAR,
                    "button_tapped",
                    mapOf("stateBefore" to stateBefore, "buttonAction" to action)
                )
            )
        }
    }

    fun googleAuthFailed(reason: String) {
        safelyLog {
            Log.e(
                TAG_CALENDAR_ERROR,
                buildMessage(
                    CalendarProvider.GOOGLE_CALENDAR,
                    "auth_failed",
                    mapOf("errorCode" to reason)
                )
            )
        }
    }

    fun visibleMonthLoad(provider: CalendarProvider?, succeeded: Boolean, reason: String? = null) {
        safelyLog {
            val message = buildMessage(
                provider,
                if (succeeded) "visible_month_load_succeeded" else "visible_month_load_failed",
                mapOf("fallbackReason" to reason)
            )
            if (succeeded) Log.d(tagFor(provider), message) else Log.e(TAG_CALENDAR_ERROR, message)
        }
    }

    fun apiStatus(
        provider: CalendarProvider,
        action: String,
        statusCode: Int
    ) {
        safelyLog {
            Log.d(
                tagFor(provider),
                buildMessage(provider, action, mapOf("apiStatus" to statusCode))
            )
        }
    }

    fun apiError(
        provider: CalendarProvider,
        action: String,
        statusCode: Int,
        fallbackReason: String? = null
    ) {
        safelyLog {
            Log.e(
                TAG_CALENDAR_ERROR,
                buildMessage(
                provider = provider,
                action = action,
                fields = mapOf(
                    "apiStatus" to statusCode,
                    "fallbackReason" to fallbackReason
                    )
                )
            )
        }
    }

    fun microsoftGraphError(
        statusCode: Int,
        graphErrorCode: String?,
        sanitizedMessage: String?,
        requestId: String?,
        clientRequestId: String?
    ) {
        safelyLog {
            Log.e(
                TAG_CALENDAR_ERROR,
                buildMessage(
                    CalendarProvider.MICROSOFT_CALENDAR,
                    "calendar_delta",
                    mapOf(
                        "apiStatus" to statusCode,
                        "graphErrorCode" to graphErrorCode,
                        "sanitizedMessage" to sanitizedMessage,
                        "requestId" to requestId,
                        "clientRequestId" to clientRequestId
                    )
                )
            )
        }
    }

    fun microsoftDeltaLifecycle(action: String, statusCode: Int? = null) {
        safelyLog {
            Log.d(
                TAG_MICROSOFT_CALENDAR_SYNC,
                buildMessage(
                    CalendarProvider.MICROSOFT_CALENDAR,
                    action,
                    mapOf("apiStatus" to statusCode)
                )
            )
        }
    }

    fun fallback(
        provider: CalendarProvider?,
        action: String,
        fallbackReason: String
    ) {
        safelyLog {
            Log.w(
                tagFor(provider),
                buildMessage(provider, action, mapOf("fallbackReason" to fallbackReason))
            )
        }
    }

    fun ui(action: String, fields: Map<String, Any?> = emptyMap()) {
        safelyLog { Log.d(TAG_CALENDAR_UI, buildMessage(null, action, fields)) }
    }

    fun duplicateWarningShownInline(duplicateCount: Int, selectedDay: String) {
        safelyLog {
            Log.d(
                TAG_CALENDAR_DUPLICATE_CHECK,
                buildMessage(
                    provider = null,
                    action = "duplicate_warning_shown_inline",
                    fields = mapOf(
                        "duplicateCount" to duplicateCount,
                        "selectedDay" to selectedDay
                    )
                )
            )
        }
    }

    fun appointmentSuspended(provider: CalendarProvider, recurringOccurrence: Boolean) {
        ui(
            action = "appointment_suspended",
            fields = mapOf(
                "provider" to provider.name,
                "recurringOccurrence" to recurringOccurrence
            )
        )
    }

    fun appointmentReactivated(provider: CalendarProvider) {
        ui(
            action = "appointment_reactivated",
            fields = mapOf("provider" to provider.name)
        )
    }

    fun meetingLinkDetected(provider: CalendarProvider) {
        meetingLinkLog(provider, "meeting_link_detected", mapOf("detected" to true))
    }

    fun meetingLinkSource(
        provider: CalendarProvider,
        structured: Boolean,
        htmlFallback: Boolean,
        onlineMeeting: Boolean? = null,
        providerNamePresent: Boolean? = null
    ) {
        meetingLinkLog(
            provider,
            "meeting_link_inspected",
            mapOf(
                "detected" to (structured || htmlFallback),
                "structuredLinkUsed" to structured,
                "htmlFallbackLinkUsed" to htmlFallback,
                "onlineMeeting" to onlineMeeting,
                "providerNamePresent" to providerNamePresent
            )
        )
    }

    fun meetingLinkOpen(provider: CalendarProvider, action: String, result: String) {
        meetingLinkLog(provider, action, mapOf("result" to result))
    }

    fun joinButtonVisibility(provider: CalendarProvider?, visible: Boolean) {
        meetingLinkLog(
            provider ?: CalendarProvider.APP,
            "join_button_visibility",
            mapOf("joinButtonVisible" to visible)
        )
    }

    fun meetingOpenResult(
        provider: CalendarProvider,
        appOpenAttempted: Boolean,
        browserFallbackUsed: Boolean,
        invalidUrlRejected: Boolean,
        result: String
    ) {
        meetingLinkLog(
            provider,
            "meeting_open_result",
            mapOf(
                "appOpenAttempted" to appOpenAttempted,
                "browserFallbackUsed" to browserFallbackUsed,
                "invalidUrlRejected" to invalidUrlRejected,
                "result" to result
            )
        )
    }

    fun meetingMetadata(
        provider: CalendarProvider,
        originalProvider: CalendarProvider,
        persisted: Boolean,
        synchronizedProvidersCount: Int,
        pendingProvidersCount: Int,
        mergePreservedMeetingLink: Boolean? = null
    ) {
        meetingLinkLog(
            provider,
            "meeting_metadata",
            mapOf(
                "originalProvider" to originalProvider.name,
                "meetingMetadataPersisted" to persisted,
                "mergePreservedMeetingLink" to mergePreservedMeetingLink,
                "synchronizedProvidersCount" to synchronizedProvidersCount,
                "pendingProvidersCount" to pendingProvidersCount
            )
        )
    }

    private fun meetingLinkLog(
        provider: CalendarProvider,
        action: String,
        fields: Map<String, Any?>
    ) {
        safelyLog { Log.d(TAG_MEETING_LINK, buildMessage(provider, action, fields)) }
    }

    fun alarmSkippedForSuspendedAppointment(provider: CalendarProvider) {
        safelyLog {
            Log.d(
                TAG_CALENDAR_SYNC,
                buildMessage(provider, "alarm_skipped_suspended_appointment")
            )
        }
    }

    fun requestCount(
        provider: CalendarProvider,
        requestType: String,
        sessionCount: Int,
        dayCount: Int
    ) {
        safelyLog {
            Log.d(
                TAG_CALENDAR_QUOTA,
                buildMessage(
                    provider,
                    "request_count",
                    mapOf(
                        "requestType" to requestType,
                        "sessionCount" to sessionCount,
                        "dayCount" to dayCount
                    )
                )
            )
        }
    }

    fun safeLimitReached(
        provider: CalendarProvider,
        requestType: String,
        sessionCount: Int,
        dayCount: Int
    ) {
        safelyLog {
            Log.w(
                TAG_CALENDAR_QUOTA,
                buildMessage(
                    provider,
                    "safe_limit_reached",
                    mapOf(
                        "requestType" to requestType,
                        "sessionCount" to sessionCount,
                        "dayCount" to dayCount
                    )
                )
            )
        }
    }

    fun retryBackoff(
        provider: CalendarProvider,
        requestType: String,
        statusCode: Int,
        attempt: Int,
        delayMillis: Long,
        retryAfterUsed: Boolean
    ) {
        safelyLog {
            Log.w(
                TAG_CALENDAR_QUOTA,
                buildMessage(
                    provider,
                    "retry_backoff",
                    mapOf(
                        "requestType" to requestType,
                        "apiStatus" to statusCode,
                        "attempt" to attempt,
                        "delayMillis" to delayMillis,
                        "retryAfterUsed" to retryAfterUsed
                    )
                )
            )
        }
    }

    fun cooldown(provider: CalendarProvider?, action: String, remainingMillis: Long) {
        safelyLog {
            Log.d(
                TAG_CALENDAR_QUOTA,
                buildMessage(
                    provider,
                    "cooldown",
                    mapOf("syncAction" to action, "remainingMillis" to remainingMillis)
                )
            )
        }
    }

    fun incrementalSync(
        provider: CalendarProvider,
        used: Boolean,
        fullSyncReason: String? = null
    ) {
        safelyLog {
            Log.d(
                tagFor(provider),
                buildMessage(
                    provider,
                    "incremental_sync",
                    mapOf(
                        "incrementalUsed" to used,
                        "fullSyncReason" to fullSyncReason
                    )
                )
            )
        }
    }

    fun incrementalTokenRefreshed(provider: CalendarProvider, reason: String) {
        safelyLog {
            Log.d(
                tagFor(provider),
                buildMessage(
                    provider,
                    "incremental_token_refreshed",
                    mapOf("fallbackReason" to reason)
                )
            )
        }
    }

    fun error(
        provider: CalendarProvider?,
        action: String,
        fallbackReason: String
    ) {
        safelyLog {
            val fieldName = if (
                provider == CalendarProvider.GOOGLE_CALENDAR &&
                fallbackReason.startsWith("GOOGLE_")
            ) {
                "errorCode"
            } else {
                "fallbackReason"
            }
            Log.e(
                TAG_CALENDAR_ERROR,
                buildMessage(provider, action, mapOf(fieldName to fallbackReason))
            )
        }
    }

    private fun tagFor(provider: CalendarProvider?): String {
        return when (provider) {
            CalendarProvider.GOOGLE_CALENDAR -> TAG_GOOGLE_CALENDAR_SYNC
            CalendarProvider.MICROSOFT_CALENDAR -> TAG_MICROSOFT_CALENDAR_SYNC
            else -> TAG_CALENDAR_SYNC
        }
    }

    private fun sanitizeValue(key: String, value: Any?): String? {
        if (value == null) return null
        val normalizedKey = key.lowercase()
        if (SENSITIVE_KEY_PARTS.any { normalizedKey.contains(it) }) {
            return "<redacted>"
        }

        val rawValue = value.toString()
        return if (rawValue.length > MAX_FIELD_VALUE_LENGTH) {
            rawValue.take(MAX_FIELD_VALUE_LENGTH) + "..."
        } else {
            rawValue
        }
    }

    private inline fun safelyLog(block: () -> Unit) {
        if (!BuildConfig.DEBUG) return
        runCatching(block)
    }

    private const val MAX_FIELD_VALUE_LENGTH = 96

    private val SENSITIVE_KEY_PARTS = setOf(
        "token",
        "secret",
        "credential",
        "authorization",
        "password",
        "email",
        "eventid",
        "reminderid",
        "uri",
        "url",
        "name",
        "phone",
        "amount",
        "title",
        "detail",
        "description",
        "body",
        "content"
    )
}
