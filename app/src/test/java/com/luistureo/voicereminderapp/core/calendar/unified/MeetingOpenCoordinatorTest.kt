package com.luistureo.voicereminderapp.core.calendar.unified

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeetingOpenCoordinatorTest {
    @Test
    fun installedProviderAppUsesActionViewPathWithoutBrowserFallback() {
        var appOpened = false
        var browserOpened = false
        val result = MeetingOpenCoordinator.open(
            "https://meet.google.com/abc-defg-hij",
            providerAppAvailable = true,
            openProviderApp = { appOpened = true },
            openBrowser = { browserOpened = true }
        )
        assertEquals(MeetingOpenCoordinator.Result.APP_OPENED, result)
        assertTrue(appOpened)
        assertFalse(browserOpened)
    }

    @Test
    fun failedProviderAppAutomaticallyFallsBackToBrowser() {
        var browserOpened = false
        val result = MeetingOpenCoordinator.open(
            "https://teams.microsoft.com/l/meetup-join/abc",
            providerAppAvailable = true,
            openProviderApp = { error("missing app") },
            openBrowser = { browserOpened = true }
        )
        assertEquals(MeetingOpenCoordinator.Result.BROWSER_OPENED, result)
        assertTrue(browserOpened)
    }

    @Test
    fun invalidAndMalformedUrlsNeverInvokeHandlers() {
        listOf(
            "javascript:alert(1)",
            "file:///tmp/x",
            "content://private/meeting",
            "intent://meet.google.com/#Intent;scheme=https;end",
            "https://%zz"
        ).forEach { url ->
            var invoked = false
            val result = MeetingOpenCoordinator.open(
                url,
                providerAppAvailable = true,
                openProviderApp = { invoked = true },
                openBrowser = { invoked = true }
            )
            assertEquals(MeetingOpenCoordinator.Result.INVALID_URL, result)
            assertFalse(invoked)
        }
    }
}
