package com.luistureo.voicereminderapp.presentation.calendar

import androidx.annotation.StringRes
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarErrorCode

object GoogleCalendarErrorUi {
    @StringRes
    fun messageRes(code: GoogleCalendarErrorCode): Int {
        return when (code) {
            GoogleCalendarErrorCode.AUTH_CANCELLED ->
                R.string.calendar_sync_google_auth_cancelled_inline
            GoogleCalendarErrorCode.AUTH_MISSING_GAIA_ID ->
                R.string.calendar_sync_google_missing_gaia_inline
            GoogleCalendarErrorCode.AUTH_BAD_AUTHENTICATION ->
                R.string.calendar_sync_google_bad_auth_inline
            GoogleCalendarErrorCode.AUTH_INTERNAL_ERROR ->
                R.string.calendar_sync_google_internal_error_inline
            GoogleCalendarErrorCode.AUTH_NETWORK_IO ->
                R.string.calendar_sync_google_auth_network_inline
            GoogleCalendarErrorCode.AUTH_PLAY_SERVICES_UNAVAILABLE ->
                R.string.calendar_sync_google_play_services_inline
            GoogleCalendarErrorCode.AUTH_SCOPE_DENIED ->
                R.string.calendar_sync_google_scope_denied_inline
            GoogleCalendarErrorCode.AUTH_UNKNOWN ->
                R.string.calendar_sync_google_auth_unknown_inline
            GoogleCalendarErrorCode.CALENDAR_API_401 ->
                R.string.calendar_sync_google_api_401_inline
            GoogleCalendarErrorCode.CALENDAR_API_403 ->
                R.string.calendar_sync_google_api_403_inline
            GoogleCalendarErrorCode.CALENDAR_API_429 ->
                R.string.calendar_sync_google_api_429_inline
            GoogleCalendarErrorCode.CALENDAR_API_5XX ->
                R.string.calendar_sync_google_api_5xx_inline
            GoogleCalendarErrorCode.SYNC_IO_EXCEPTION ->
                R.string.calendar_sync_google_sync_io_inline
            GoogleCalendarErrorCode.SYNC_UNKNOWN ->
                R.string.calendar_sync_google_sync_unknown_inline
        }
    }
}
