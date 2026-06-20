package com.luistureo.voicereminderapp.core.calendar.microsoft

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MicrosoftGraphUsagePolicyTest {

    @Test
    fun usesOnlyStandardDelegatedCalendarScopes() {
        assertEquals(
            setOf("User.Read", "Calendars.ReadWrite"),
            MicrosoftCalendarConfig.scopes.toSet()
        )
    }

    @Test
    fun graphGatewayUsesStandardCalendarEndpointsAndSafeguards() {
        val gateway = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/core/calendar/microsoft/" +
                    "MicrosoftGraphCalendarGateway.kt"
        ).readText()
        val executor = sourceFile(
            "app/src/main/java/com/luistureo/voicereminderapp/core/calendar/unified/" +
                    "CalendarHttpRequestExecutor.kt"
        ).readText()

        assertTrue(gateway.contains("/me/calendarView/delta"))
        assertTrue(gateway.contains("/me/events"))
        assertTrue(executor.contains("Retry-After"))
        assertTrue(executor.contains("CalendarBackoffPolicy"))
        assertTrue(executor.contains("tryAcquireRequest"))
    }

    @Test
    fun projectDoesNotDeclareMeteredGraphOrPaidAzureResources() {
        val inspectedText = listOf(
            sourceFile("app/build.gradle.kts"),
            sourceFile(
                "app/src/main/java/com/luistureo/voicereminderapp/core/calendar/microsoft/" +
                        "MicrosoftGraphCalendarGateway.kt"
            )
        ).joinToString(separator = "\n") { it.readText() }

        assertFalse(inspectedText.contains("Microsoft.GraphServices", ignoreCase = true))
        assertFalse(inspectedText.contains("onlineMeetings", ignoreCase = true))
        assertFalse(inspectedText.contains("communications/", ignoreCase = true))
        assertFalse(inspectedText.contains("client_secret", ignoreCase = true))
    }

    private fun sourceFile(projectPath: String): File {
        val fromRoot = File(projectPath)
        if (fromRoot.exists()) return fromRoot
        return File(projectPath.removePrefix("app/"))
    }
}
