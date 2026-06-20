package com.luistureo.voicereminderapp.core.calendar.google

import com.google.android.gms.common.api.ApiException
import java.io.IOException

enum class GoogleCalendarErrorCode(
    val value: String,
    val invalidatesSession: Boolean
) {
    AUTH_CANCELLED("GOOGLE_AUTH_CANCELLED", false),
    AUTH_MISSING_GAIA_ID("GOOGLE_AUTH_MISSING_GAIA_ID", true),
    AUTH_BAD_AUTHENTICATION("GOOGLE_AUTH_BAD_AUTHENTICATION", true),
    AUTH_INTERNAL_ERROR("GOOGLE_AUTH_INTERNAL_ERROR", false),
    AUTH_NETWORK_IO("GOOGLE_AUTH_NETWORK_IO", false),
    AUTH_PLAY_SERVICES_UNAVAILABLE("GOOGLE_AUTH_PLAY_SERVICES_UNAVAILABLE", false),
    AUTH_SCOPE_DENIED("GOOGLE_AUTH_SCOPE_DENIED", true),
    AUTH_UNKNOWN("GOOGLE_AUTH_UNKNOWN", true),
    CALENDAR_API_401("GOOGLE_CALENDAR_API_401", true),
    CALENDAR_API_403("GOOGLE_CALENDAR_API_403", true),
    CALENDAR_API_429("GOOGLE_CALENDAR_API_429", false),
    CALENDAR_API_5XX("GOOGLE_CALENDAR_API_5XX", false),
    SYNC_IO_EXCEPTION("GOOGLE_SYNC_IO_EXCEPTION", false),
    SYNC_UNKNOWN("GOOGLE_SYNC_UNKNOWN", false);

    companion object {
        fun fromStoredValue(value: String?): GoogleCalendarErrorCode? {
            return entries.firstOrNull { it.value == value }
        }

        fun fromAuthFailure(reason: GoogleCalendarAuthFailureReason): GoogleCalendarErrorCode {
            return when (reason) {
                GoogleCalendarAuthFailureReason.BAD_AUTHENTICATION -> AUTH_BAD_AUTHENTICATION
                GoogleCalendarAuthFailureReason.MISSING_GAIA_ID -> AUTH_MISSING_GAIA_ID
                GoogleCalendarAuthFailureReason.INTERNAL_ERROR -> AUTH_INTERNAL_ERROR
                GoogleCalendarAuthFailureReason.NETWORK -> AUTH_NETWORK_IO
                GoogleCalendarAuthFailureReason.TOKEN_FAILURE -> AUTH_BAD_AUTHENTICATION
                GoogleCalendarAuthFailureReason.AUTO_MANAGE_ERROR ->
                    AUTH_PLAY_SERVICES_UNAVAILABLE
                GoogleCalendarAuthFailureReason.UNKNOWN -> AUTH_UNKNOWN
            }
        }

        fun fromSignInFailure(throwable: Throwable): GoogleCalendarErrorCode {
            val apiStatus = generateSequence(throwable) { it.cause }
                .filterIsInstance<ApiException>()
                .firstOrNull()
                ?.statusCode
            return when (apiStatus) {
                7 -> AUTH_NETWORK_IO
                2, 3, 9, 16, 17 -> AUTH_PLAY_SERVICES_UNAVAILABLE
                10 -> AUTH_INTERNAL_ERROR
                12501 -> AUTH_CANCELLED
                12500 -> AUTH_BAD_AUTHENTICATION
                else -> fromAuthFailure(GoogleCalendarAuthFailureReason.from(throwable))
            }
        }

        fun fromSyncFailure(throwable: Throwable): GoogleCalendarErrorCode {
            val cause = generateSequence(throwable) { it.cause }.firstOrNull { error ->
                error is GoogleCalendarApiException ||
                        error is GoogleCalendarAuthException ||
                        error is IOException
            } ?: throwable
            return when (cause) {
                is GoogleCalendarApiException -> when (cause.code) {
                    401 -> CALENDAR_API_401
                    403 -> CALENDAR_API_403
                    429 -> CALENDAR_API_429
                    in 500..599 -> CALENDAR_API_5XX
                    else -> SYNC_UNKNOWN
                }
                is GoogleCalendarAuthException.AuthenticationFailed ->
                    fromAuthFailure(cause.reason)
                is GoogleCalendarAuthException.MissingCalendarScope,
                is GoogleCalendarAuthException.UserActionRequired -> AUTH_SCOPE_DENIED
                is GoogleCalendarAuthException.NetworkFailure -> AUTH_NETWORK_IO
                is IOException -> SYNC_IO_EXCEPTION
                else -> SYNC_UNKNOWN
            }
        }

        fun fromLegacyReason(reason: String): GoogleCalendarErrorCode {
            val normalized = reason.uppercase()
            return entries.firstOrNull { it.value == normalized } ?: when {
                "BAD_AUTHENTICATION" in normalized -> AUTH_BAD_AUTHENTICATION
                "GAIA" in normalized || "MISSING_ACCOUNT" in normalized ->
                    AUTH_MISSING_GAIA_ID
                "INTERNAL_ERROR" in normalized -> AUTH_INTERNAL_ERROR
                "SCOPE" in normalized -> AUTH_SCOPE_DENIED
                "401" in normalized -> CALENDAR_API_401
                "403" in normalized -> CALENDAR_API_403
                "429" in normalized -> CALENDAR_API_429
                "5XX" in normalized -> CALENDAR_API_5XX
                "IOEXCEPTION" in normalized || "IO_EXCEPTION" in normalized ->
                    SYNC_IO_EXCEPTION
                else -> SYNC_UNKNOWN
            }
        }
    }
}
