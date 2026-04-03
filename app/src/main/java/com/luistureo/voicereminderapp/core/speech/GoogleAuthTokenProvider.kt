package com.luistureo.voicereminderapp.core.speech

import android.content.Context
import com.google.auth.oauth2.GoogleCredentials
import com.luistureo.voicereminderapp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleAuthTokenProvider(
    private val context: Context
) {

    suspend fun getAccessToken(): String = withContext(Dispatchers.IO) {
        val inputStream = context.resources.openRawResource(R.raw.google_service_account)

        inputStream.use { stream ->
            val credentials = GoogleCredentials
                .fromStream(stream)
                .createScoped(
                    listOf("https://www.googleapis.com/auth/cloud-platform")
                )

            credentials.refreshIfExpired()
            credentials.accessToken.tokenValue
        }
    }
}