package com.luistureo.voicereminderapp.core.calendar.microsoft

object MicrosoftCalendarConfig {
    const val CLIENT_ID = "2a450f3c-966d-4c19-8fdf-dabb4566a911"
    const val REDIRECT_URI =
        "msauth://com.luistureo.voicereminderapp/BW1s4fDEKCPSW1ifniiayKcRFFY%3D"
    const val AUTHORITY = "https://login.microsoftonline.com/common"
    const val GRAPH_BASE_URL = "https://graph.microsoft.com/v1.0"

    val scopes = arrayOf("User.Read", "Calendars.ReadWrite")

    fun isConfigured(
        clientId: String = CLIENT_ID,
        redirectUri: String = REDIRECT_URI
    ): Boolean {
        return clientId.isNotBlank() &&
                redirectUri.startsWith("msauth://com.luistureo.voicereminderapp/")
    }
}
