package com.luistureo.voicereminderapp.presentation.calendar

object CalendarSyncUiPolicy {
    fun lastSyncLabel(lastSuccessAtEpochMillis: Long, nowEpochMillis: Long): String? {
        if (lastSuccessAtEpochMillis <= 0L) return null
        val elapsedMinutes = ((nowEpochMillis - lastSuccessAtEpochMillis)
            .coerceAtLeast(0L) / 60_000L)
        return when {
            elapsedMinutes < 1L -> "Última sincronización: hace menos de un minuto"
            elapsedMinutes == 1L -> "Última sincronización: hace un minuto"
            elapsedMinutes < 60L -> "Última sincronización: hace $elapsedMinutes minutos"
            else -> "Última sincronización: hace unas horas"
        }
    }

    fun resolve(
        googleConnected: Boolean,
        microsoftConnected: Boolean,
        microsoftAuthConfigured: Boolean,
        googlePaused: Boolean = false,
        microsoftPaused: Boolean = false
    ): CalendarSyncPanelUiState {
        return CalendarSyncPanelUiState(
            status = when {
                googleConnected && microsoftConnected -> CalendarSyncStatus.GOOGLE_AND_MICROSOFT
                googleConnected -> CalendarSyncStatus.GOOGLE
                microsoftConnected -> CalendarSyncStatus.MICROSOFT
                googlePaused -> CalendarSyncStatus.GOOGLE_PAUSED
                microsoftPaused -> CalendarSyncStatus.MICROSOFT_PAUSED
                else -> CalendarSyncStatus.NOT_CONNECTED
            },
            googleButtonAction = when {
                googleConnected -> CalendarSyncButtonAction.PAUSE_GOOGLE
                googlePaused -> CalendarSyncButtonAction.ACTIVATE_GOOGLE
                else -> CalendarSyncButtonAction.CONNECT_GOOGLE
            },
            microsoftButtonAction = when {
                microsoftConnected -> CalendarSyncButtonAction.PAUSE_MICROSOFT
                microsoftPaused -> CalendarSyncButtonAction.ACTIVATE_MICROSOFT
                microsoftAuthConfigured -> CalendarSyncButtonAction.CONNECT_MICROSOFT
                else -> CalendarSyncButtonAction.SHOW_MICROSOFT_NOT_CONFIGURED
            }
        )
    }

    fun resolveProviderState(
        currentState: CalendarProviderUiState,
        syncEnabled: Boolean,
        hasSession: Boolean,
        hasError: Boolean
    ): CalendarProviderUiState = resolveGoogleProviderState(
        currentState,
        syncEnabled,
        hasSession,
        hasError
    )

    fun resolveGoogleProviderState(
        currentState: CalendarProviderUiState,
        syncEnabled: Boolean,
        hasSession: Boolean,
        hasError: Boolean
    ): CalendarProviderUiState {
        if (
            currentState == CalendarProviderUiState.CONNECTING ||
            currentState == CalendarProviderUiState.SYNCING
        ) {
            return currentState
        }
        return when {
            hasError -> CalendarProviderUiState.ERROR
            !syncEnabled && hasSession -> CalendarProviderUiState.PAUSED
            syncEnabled && hasSession -> CalendarProviderUiState.CONNECTED
            else -> CalendarProviderUiState.DISCONNECTED
        }
    }

    fun googleMainAction(
        state: CalendarProviderUiState,
        hasSession: Boolean
    ): CalendarSyncButtonAction {
        return when {
            state == CalendarProviderUiState.PAUSED ->
                CalendarSyncButtonAction.ACTIVATE_GOOGLE
            state == CalendarProviderUiState.CONNECTED ->
                CalendarSyncButtonAction.PAUSE_GOOGLE
            state == CalendarProviderUiState.ERROR && hasSession ->
                CalendarSyncButtonAction.PAUSE_GOOGLE
            else -> CalendarSyncButtonAction.CONNECT_GOOGLE
        }
    }
}

data class CalendarSyncPanelUiState(
    val status: CalendarSyncStatus,
    val googleButtonAction: CalendarSyncButtonAction,
    val microsoftButtonAction: CalendarSyncButtonAction
)

enum class CalendarSyncStatus {
    NOT_CONNECTED,
    GOOGLE,
    GOOGLE_PAUSED,
    MICROSOFT_PAUSED,
    MICROSOFT,
    GOOGLE_AND_MICROSOFT
}

enum class CalendarSyncButtonAction {
    CONNECT_GOOGLE,
    ACTIVATE_GOOGLE,
    PAUSE_GOOGLE,
    CONNECT_MICROSOFT,
    ACTIVATE_MICROSOFT,
    PAUSE_MICROSOFT,
    SHOW_MICROSOFT_NOT_CONFIGURED
}

enum class CalendarProviderUiState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    SYNCING,
    ERROR,
    PAUSED,
    DISABLED
}
