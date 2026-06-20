package com.luistureo.voicereminderapp.presentation.calendar

import androidx.annotation.StringRes
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.calendar.microsoft.MicrosoftCalendarErrorCode

object MicrosoftCalendarErrorUi {
    @StringRes
    fun messageRes(code: MicrosoftCalendarErrorCode): Int = when (code) {
        MicrosoftCalendarErrorCode.AUTH_CANCELLED ->
            R.string.calendar_sync_microsoft_auth_cancelled_inline
        MicrosoftCalendarErrorCode.AUTH_NETWORK_IO ->
            R.string.calendar_sync_microsoft_auth_network_inline
        MicrosoftCalendarErrorCode.AUTH_MSAL_CLIENT_ERROR ->
            R.string.calendar_sync_microsoft_msal_client_inline
        MicrosoftCalendarErrorCode.AUTH_MSAL_SERVICE_ERROR ->
            R.string.calendar_sync_microsoft_msal_service_inline
        MicrosoftCalendarErrorCode.AUTH_SCOPE_DENIED ->
            R.string.calendar_sync_microsoft_scope_denied_inline
        MicrosoftCalendarErrorCode.DELTA_LINK_INVALID ->
            R.string.calendar_sync_microsoft_delta_link_invalid_inline
        MicrosoftCalendarErrorCode.GRAPH_400_BAD_REQUEST ->
            R.string.calendar_sync_microsoft_graph_400_inline
        MicrosoftCalendarErrorCode.GRAPH_401 ->
            R.string.calendar_sync_microsoft_graph_401_inline
        MicrosoftCalendarErrorCode.GRAPH_403 ->
            R.string.calendar_sync_microsoft_graph_403_inline
        MicrosoftCalendarErrorCode.GRAPH_404 ->
            R.string.calendar_sync_microsoft_graph_404_inline
        MicrosoftCalendarErrorCode.GRAPH_429 ->
            R.string.calendar_sync_microsoft_graph_429_inline
        MicrosoftCalendarErrorCode.GRAPH_5XX ->
            R.string.calendar_sync_microsoft_graph_5xx_inline
        MicrosoftCalendarErrorCode.SYNC_IO_EXCEPTION ->
            R.string.calendar_sync_microsoft_sync_io_inline
    }
}
