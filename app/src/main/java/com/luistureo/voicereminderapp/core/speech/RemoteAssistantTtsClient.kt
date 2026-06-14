package com.luistureo.voicereminderapp.core.speech

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64

class RemoteAssistantTtsClient(
    private val backendUrl: String = AssistantTtsConfig.REMOTE_TTS_BACKEND_URL,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val debugLogger: (String) -> Unit = AssistantTtsDebugLogger::log
) : AssistantTtsService {

    override suspend fun synthesize(
        text: String,
        voice: AssistantVoiceOption?
    ): AssistantTtsAudio? = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) {
            debugLogger("Remote TTS skipped: empty text")
            return@withContext null
        }
        if (backendUrl.isBlank()) {
            debugLogger("Remote TTS skipped: backend URL is empty")
            return@withContext null
        }

        runCatching {
            val backendVoice = AssistantTtsConfig.REMOTE_TTS_VOICE
            debugLogger(
                "Remote TTS request started: selectedVoice=${voice?.id ?: "none"}, " +
                    "backendVoice=$backendVoice"
            )
            val requestBody = buildRequestJson(cleanText, backendVoice)
                .toRequestBody(JSON_MEDIA_TYPE)

            val request = Request.Builder()
                .url(backendUrl)
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val contentType = response.header("Content-Type").orEmpty()
                debugLogger(
                    "Remote TTS backend response: status=${response.code}, " +
                        "contentType=${contentType.ifBlank { "none" }}"
                )
                if (!response.isSuccessful) return@use null

                val body = response.body ?: return@use null

                if (contentType.startsWith("audio/", ignoreCase = true)) {
                    return@use AssistantTtsAudio(
                        bytes = body.bytes(),
                        mimeType = contentType.substringBefore(";").ifBlank { DEFAULT_MIME_TYPE }
                    ).takeIf { it.isPlayable }
                }

                parseJsonAudio(body.string())
            }
        }.getOrElse { error ->
            debugLogger("Remote TTS request failed: ${error.javaClass.simpleName}")
            null
        }
    }

    private fun parseJsonAudio(rawBody: String): AssistantTtsAudio? {
        val encodedAudio = findJsonString(rawBody, "audioBase64", "audio_base64", "audioContent")
            ?: return null
        val mimeType = findJsonString(rawBody, "mimeType", "mime_type") ?: DEFAULT_MIME_TYPE
        val bytes = runCatching {
            Base64.getDecoder().decode(encodedAudio)
        }.getOrNull() ?: return null

        return AssistantTtsAudio(bytes = bytes, mimeType = mimeType).takeIf { it.isPlayable }
    }

    private fun buildRequestJson(text: String, voice: String?): String {
        val voiceProperty = voice
            ?.takeIf { it.isNotBlank() }
            ?.let { ""","voice":"${it.escapeJsonString()}"""" }
            .orEmpty()
        return """{"text":"${text.escapeJsonString()}"$voiceProperty}"""
    }

    private fun findJsonString(rawBody: String, vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key ->
            val pattern = """"${Regex.escape(key)}"\s*:\s*"((?:\\.|[^"\\])*)"""".toRegex()
            pattern.find(rawBody)
                ?.groupValues
                ?.getOrNull(1)
                ?.unescapeJsonString()
                ?.takeIf { it.isNotBlank() }
        }
    }

    private fun String.escapeJsonString(): String = buildString {
        this@escapeJsonString.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }

    private fun String.unescapeJsonString(): String = buildString {
        var index = 0
        while (index < this@unescapeJsonString.length) {
            val char = this@unescapeJsonString[index]
            if (char == '\\' && index + 1 < this@unescapeJsonString.length) {
                when (val escaped = this@unescapeJsonString[index + 1]) {
                    '\\' -> append('\\')
                    '"' -> append('"')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    else -> append(escaped)
                }
                index += 2
            } else {
                append(char)
                index++
            }
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val DEFAULT_MIME_TYPE = "audio/mpeg"
    }
}
