package com.luistureo.voicereminderapp.core.calendar.microsoft

import java.io.File
import java.net.URI
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MicrosoftCalendarConfigTest {

    private val debugRedirect =
        "msauth://com.luistureo.voicereminderapp/BW1s4fDEKCPSW1ifniiayKcRFFY%3D"
    private val releaseRedirect =
        "msauth://com.luistureo.voicereminderapp/%2FH2TaEmQ8s92VDO8Ny7L8I0cu2o%3D"

    @Test
    fun entraConfigurationMatchesRegisteredApplication() {
        assertTrue(MicrosoftCalendarConfig.isConfigured())
        assertEquals("2a450f3c-966d-4c19-8fdf-dabb4566a911", MicrosoftCalendarConfig.CLIENT_ID)
        assertEquals(
            debugRedirect,
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
        val configs = listOf(
            debugConfigFile().readText(),
            releaseConfigFile().readText()
        )

        configs.forEach { config ->
            assertTrue(config.contains(MicrosoftCalendarConfig.CLIENT_ID))
            assertFalse(config.contains("client_secret", ignoreCase = true))
            assertFalse(config.contains("access_token", ignoreCase = true))
            assertFalse(config.contains("refresh_token", ignoreCase = true))
        }
    }

    @Test
    fun debugAndReleaseConfigurationsUseSeparateExactRedirects() {
        val debugConfig = JSONObject(debugConfigFile().readText())
        val releaseConfig = JSONObject(releaseConfigFile().readText())

        assertEquals(debugRedirect, debugConfig.getString("redirect_uri"))
        assertEquals(releaseRedirect, releaseConfig.getString("redirect_uri"))
        assertFalse(debugConfig.getString("redirect_uri") == releaseConfig.getString("redirect_uri"))
        assertEquals(MicrosoftCalendarConfig.CLIENT_ID, releaseConfig.getString("client_id"))
        assertEquals("common", releaseConfig.tenantId())
        assertEquals("common", debugConfig.tenantId())
    }

    @Test
    fun releaseRedirectIsEncodedExactlyOnceAndMatchesAndroidCallbackPath() {
        val uri = URI(releaseRedirect)
        val releaseManifest = sourceFile("app/src/release/AndroidManifest.xml").readText()

        assertEquals("msauth", uri.scheme)
        assertEquals("com.luistureo.voicereminderapp", uri.host)
        assertEquals("/%2FH2TaEmQ8s92VDO8Ny7L8I0cu2o%3D", uri.rawPath)
        assertEquals("//H2TaEmQ8s92VDO8Ny7L8I0cu2o=", uri.path)
        assertFalse(releaseRedirect.contains("%252F", ignoreCase = true))
        assertFalse(releaseRedirect.contains("%253D", ignoreCase = true))
        assertTrue(releaseManifest.contains("android:scheme=\"msauth\""))
        assertTrue(releaseManifest.contains("android:host=\"com.luistureo.voicereminderapp\""))
        assertTrue(releaseManifest.contains("android:path=\"//H2TaEmQ8s92VDO8Ny7L8I0cu2o=\""))
    }

    @Test
    fun debugCallbackAndPackageRemainUnchanged() {
        val debugManifest = sourceFile("app/src/main/AndroidManifest.xml").readText()
        val appBuild = sourceFile("app/build.gradle.kts").readText()

        assertTrue(debugManifest.contains("android:path=\"/BW1s4fDEKCPSW1ifniiayKcRFFY=\""))
        assertFalse(debugManifest.contains("H2TaEmQ8s92VDO8Ny7L8I0cu2o"))
        assertTrue(appBuild.contains("applicationId = \"com.luistureo.voicereminderapp\""))
    }

    private fun JSONObject.tenantId(): String =
        getJSONArray("authorities")
            .getJSONObject(0)
            .getJSONObject("audience")
            .getString("tenant_id")

    private fun debugConfigFile() =
        sourceFile("app/src/main/res/raw/auth_config_single_account.json")

    private fun releaseConfigFile() =
        sourceFile("app/src/release/res/raw/auth_config_single_account.json")

    private fun sourceFile(projectPath: String): File {
        val fromRoot = File(projectPath)
        if (fromRoot.exists()) return fromRoot
        return File(projectPath.removePrefix("app/"))
    }
}
