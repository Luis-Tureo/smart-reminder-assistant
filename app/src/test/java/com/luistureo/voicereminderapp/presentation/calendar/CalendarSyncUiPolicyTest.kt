package com.luistureo.voicereminderapp.presentation.calendar

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarSyncUiPolicyTest {

    @Test
    fun lastSyncLabelIsSmallAndNonIntrusive() {
        assertEquals(
            "Última sincronización: hace 5 minutos",
            CalendarSyncUiPolicy.lastSyncLabel(1_000_000L, 1_300_000L)
        )
    }

    @Test
    fun googleAndMicrosoftConnectedShowsUnifiedStateWithPauseActions() {
        val state = CalendarSyncUiPolicy.resolve(
            googleConnected = true,
            microsoftConnected = true,
            microsoftAuthConfigured = true
        )

        assertEquals(CalendarSyncStatus.GOOGLE_AND_MICROSOFT, state.status)
        assertEquals(CalendarSyncButtonAction.PAUSE_GOOGLE, state.googleButtonAction)
        assertEquals(CalendarSyncButtonAction.PAUSE_MICROSOFT, state.microsoftButtonAction)
    }

    @Test
    fun googleButtonConnectsWhenGoogleIsNotConnected() {
        val state = CalendarSyncUiPolicy.resolve(
            googleConnected = false,
            microsoftConnected = false,
            microsoftAuthConfigured = false
        )

        assertEquals(CalendarSyncStatus.NOT_CONNECTED, state.status)
        assertEquals(CalendarSyncButtonAction.CONNECT_GOOGLE, state.googleButtonAction)
    }

    @Test
    fun microsoftButtonUsesPlaceholderWhenAuthIsNotConfigured() {
        val state = CalendarSyncUiPolicy.resolve(
            googleConnected = false,
            microsoftConnected = false,
            microsoftAuthConfigured = false
        )

        assertEquals(
            CalendarSyncButtonAction.SHOW_MICROSOFT_NOT_CONFIGURED,
            state.microsoftButtonAction
        )
    }

    @Test
    fun microsoftButtonStartsLoginWhenAuthIsConfigured() {
        val state = CalendarSyncUiPolicy.resolve(
            googleConnected = false,
            microsoftConnected = false,
            microsoftAuthConfigured = true
        )

        assertEquals(CalendarSyncButtonAction.CONNECT_MICROSOFT, state.microsoftButtonAction)
    }

    @Test
    fun connectedProviderNeverOffersConnectOrSyncAction() {
        val google = CalendarSyncUiPolicy.resolve(true, false, true)
        val microsoft = CalendarSyncUiPolicy.resolve(false, true, true)

        assertEquals(CalendarSyncButtonAction.PAUSE_GOOGLE, google.googleButtonAction)
        assertEquals(
            CalendarSyncButtonAction.PAUSE_MICROSOFT,
            microsoft.microsoftButtonAction
        )
    }

    @Test
    fun eachProviderCombinationHasOneCompactStatus() {
        assertEquals(
            CalendarSyncStatus.NOT_CONNECTED,
            CalendarSyncUiPolicy.resolve(false, false, true).status
        )
        assertEquals(
            CalendarSyncStatus.GOOGLE,
            CalendarSyncUiPolicy.resolve(true, false, true).status
        )
        assertEquals(
            CalendarSyncStatus.MICROSOFT,
            CalendarSyncUiPolicy.resolve(false, true, true).status
        )
        assertEquals(
            CalendarSyncStatus.GOOGLE_AND_MICROSOFT,
            CalendarSyncUiPolicy.resolve(true, true, true).status
        )
    }

    @Test
    fun googleStateMachineSeparatesDisconnectedActivePausedAndError() {
        assertEquals(
            CalendarProviderUiState.DISCONNECTED,
            CalendarSyncUiPolicy.resolveGoogleProviderState(
                CalendarProviderUiState.DISCONNECTED, true, false, false
            )
        )
        assertEquals(
            CalendarProviderUiState.CONNECTED,
            CalendarSyncUiPolicy.resolveGoogleProviderState(
                CalendarProviderUiState.DISCONNECTED, true, true, false
            )
        )
        assertEquals(
            CalendarProviderUiState.PAUSED,
            CalendarSyncUiPolicy.resolveGoogleProviderState(
                CalendarProviderUiState.CONNECTED, false, true, false
            )
        )
        assertEquals(
            CalendarProviderUiState.ERROR,
            CalendarSyncUiPolicy.resolveGoogleProviderState(
                CalendarProviderUiState.CONNECTED, true, true, true
            )
        )
    }

    @Test
    fun googleErrorKeepsCorrectMainActionOutsideErrorBox() {
        assertEquals(
            CalendarSyncButtonAction.ACTIVATE_GOOGLE,
            CalendarSyncUiPolicy.googleMainAction(
                CalendarProviderUiState.PAUSED, true
            )
        )
        assertEquals(
            CalendarSyncButtonAction.PAUSE_GOOGLE,
            CalendarSyncUiPolicy.googleMainAction(
                CalendarProviderUiState.ERROR, true
            )
        )
        assertEquals(
            CalendarSyncButtonAction.CONNECT_GOOGLE,
            CalendarSyncUiPolicy.googleMainAction(
                CalendarProviderUiState.ERROR, false
            )
        )
    }

    @Test
    fun pausedMicrosoftUsesActivateActionAndCompactTitle() {
        val state = CalendarSyncUiPolicy.resolve(
            googleConnected = false,
            microsoftConnected = false,
            microsoftAuthConfigured = true,
            microsoftPaused = true
        )

        assertEquals(CalendarSyncStatus.MICROSOFT_PAUSED, state.status)
        assertEquals(CalendarSyncButtonAction.ACTIVATE_MICROSOFT, state.microsoftButtonAction)
    }

    @Test
    fun pausedGoogleUsesSinglePausedTitleUnlessMicrosoftIsActive() {
        val paused = CalendarSyncUiPolicy.resolve(
            googleConnected = false,
            microsoftConnected = false,
            microsoftAuthConfigured = true,
            googlePaused = true
        )
        assertEquals(
            CalendarSyncStatus.GOOGLE_PAUSED,
            paused.status
        )
        assertEquals(CalendarSyncButtonAction.ACTIVATE_GOOGLE, paused.googleButtonAction)
        assertEquals(
            CalendarSyncStatus.MICROSOFT,
            CalendarSyncUiPolicy.resolve(
                googleConnected = false,
                microsoftConnected = true,
                microsoftAuthConfigured = true,
                googlePaused = true
            ).status
        )
    }
}
