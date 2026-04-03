package com.luistureo.voicereminderapp.core.speech

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

class GoogleCloudTtsService(
    private val context: Context,
    private val tokenProvider: GoogleAuthTokenProvider
) {

    private val client = OkHttpClient()

    companion object {
        private const val LANGUAGE_CODE = "es-ES"
        private const val VOICE_NAME = ""
        private const val AUDIO_ENCODING = "MP3"
        private const val SPEAKING_RATE = 1.0
        private const val PITCH = 0.0
    }

    suspend fun synthesizeToTempFile(text: String): File = withContext(Dispatchers.IO) {
        val accessToken = tokenProvider.getAccessToken()

        val requestJson = JSONObject().apply {
            put(
                "input",
                JSONObject().apply {
                    put(
                        "ssml",
                        """
            <speak>
                <prosody rate="92%" pitch="0%">
                    $text
                </prosody>
            </speak>
            """.trimIndent()
                    )
                }
            )

            put(
                "voice",
                JSONObject().apply {
                    put("languageCode", LANGUAGE_CODE)

                    if (VOICE_NAME.isNotBlank()) {
                        put("name", VOICE_NAME)
                    }
                }
            )

            put(
                "audioConfig",
                JSONObject().apply {
                    put("audioEncoding", AUDIO_ENCODING)
                    put("speakingRate", SPEAKING_RATE)
                    put("pitch", PITCH)
                }
            )
        }

        val requestBody = requestJson.toString()
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("https://texttospeech.googleapis.com/v1/text:synthesize")
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string().orEmpty()
            throw IllegalStateException(
                "Google Cloud TTS error: ${response.code} - $errorBody"
            )
        }

        val responseBody = response.body?.string()
            ?: throw IllegalStateException("Empty response from Google Cloud TTS")

        val audioContent = JSONObject(responseBody).getString("audioContent")
        val audioBytes = Base64.decode(audioContent, Base64.DEFAULT)

        val outputFile = File.createTempFile(
            "voice_assistant_",
            ".mp3",
            context.cacheDir
        )
        outputFile.writeBytes(audioBytes)

        outputFile
    }
}