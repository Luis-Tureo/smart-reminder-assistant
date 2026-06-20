package com.luistureo.voicereminderapp.core.calendar.microsoft

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MicrosoftCalendarConfigTest {

    @Test
    fun entraConfigurationMatchesRegisteredApplication() {
        assertTrue(MicrosoftCalendarConfig.isConfigured())
        assertEquals("2a450f3c-966d-4c19-8fdf-dabb4566a911", MicrosoftCalendarConfig.CLIENT_ID)
        assertEquals(
            "msauth://com.luistureo.voicereminderapp/BW1s4fDEKCPSW1ifniiayKcRFFY%3D",
            MicrosoftCalendarConfig.REDIRECT_URI
        )
        assertTrue(MicrosoftCalendarConfig.scopes.contains("User.Read"))
        assertTrue(MicrosoftCalendarConfig.scopes.contains("Calendars.ReadWrite"))
    }

    @Test
    fun missingClientIdIsDetectedWithoutCrashing() {
        assertFalse(MicrosoftCalendarConfig.isConfigured(clientId = "", redirectUri = "msauth://x"))
    }

    @Test
    fun rawMsalConfigContainsNoClientSecret() {
        val config = sourceFile("app/src/main/res/raw/auth_config_single_account.json").readText()

        assertTrue(config.contains(MicrosoftCalendarConfig.CLIENT_ID))
        assertFalse(config.contains("client_secret", ignoreCase = true))
        assertFalse(config.contains("access_token", ignoreCase = true))
        assertFalse(config.contains("refresh_token", ignoreCase = true))
    }

    private fun sourceFile(projectPath: String): File {
        val fromRoot = File(projectPath)
        if (fromRoot.exists()) return fromRoot
        return File(projectPath.removePrefix("app/"))
    }
}
