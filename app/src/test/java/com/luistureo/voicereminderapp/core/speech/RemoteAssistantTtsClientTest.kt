package com.luistureo.voicereminderapp.core.speech

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.Base64

class RemoteAssistantTtsClientTest {

    @Test
    fun returnsAudioFromBase64JsonResponse() = runBlocking {
        val expectedBytes = byteArrayOf(1, 2, 3)
        val encodedAudio = Base64.getEncoder().encodeToString(expectedBytes)
        val client = RemoteAssistantTtsClient(
            backendUrl = TEST_URL,
            httpClient = okHttpClient(
                body = """{"audioBase64":"$encodedAudio","mimeType":"audio/mpeg"}""",
                contentType = "application/json"
            )
        )

        val audio = client.synthesize("Perfecto")

        assertEquals("audio/mpeg", audio?.mimeType)
        assertArrayEquals(expectedBytes, audio?.bytes)
    }

    @Test
    fun returnsAudioFromRawAudioResponse() = runBlocking {
        val expectedBytes = byteArrayOf(4, 5, 6)
        val client = RemoteAssistantTtsClient(
            backendUrl = TEST_URL,
            httpClient = okHttpClient(
                bodyBytes = expectedBytes,
                contentType = "audio/mpeg"
            )
        )

        val audio = client.synthesize("Hola")

        assertEquals("audio/mpeg", audio?.mimeType)
        assertArrayEquals(expectedBytes, audio?.bytes)
    }

    @Test
    fun returnsNullForEmptyTextOrMissingBackendUrl() = runBlocking {
        assertNull(RemoteAssistantTtsClient(backendUrl = TEST_URL).synthesize(""))
        assertNull(RemoteAssistantTtsClient(backendUrl = "").synthesize("Hola"))
    }

    @Test
    fun returnsNullForInvalidAudioResponse() = runBlocking {
        val client = RemoteAssistantTtsClient(
            backendUrl = TEST_URL,
            httpClient = okHttpClient(
                body = """{"audioBase64":"not-base64","mimeType":"audio/mpeg"}""",
                contentType = "application/json"
            )
        )

        assertNull(client.synthesize("Hola"))
    }

    @Test
    fun returnsNullForRemoteFailureWithoutThrowing() = runBlocking {
        val client = RemoteAssistantTtsClient(
            backendUrl = TEST_URL,
            httpClient = okHttpClient(
                body = "error",
                contentType = "text/plain",
                code = 500
            )
        )

        assertNull(client.synthesize("Hola"))
    }

    @Test
    fun returnsNullWhenNetworkIsUnavailableWithoutThrowing() = runBlocking {
        val client = RemoteAssistantTtsClient(
            backendUrl = TEST_URL,
            httpClient = OkHttpClient.Builder()
                .addInterceptor {
                    throw IOException("No internet")
                }
                .build()
        )

        assertNull(client.synthesize("Hola"))
    }

    @Test
    fun sendsAssistantTextAndFixedVoiceToBackend() = runBlocking {
        val sentBodies = mutableListOf<String>()
        val expectedBytes = byteArrayOf(7, 8, 9)
        val encodedAudio = Base64.getEncoder().encodeToString(expectedBytes)
        val client = RemoteAssistantTtsClient(
            backendUrl = TEST_URL,
            httpClient = okHttpClient(
                body = """{"audioBase64":"$encodedAudio","mimeType":"audio/mpeg"}""",
                contentType = "application/json",
                onRequestBody = sentBodies::add
            )
        )

        client.synthesize("Hola")

        assertEquals(listOf("""{"text":"Hola","voice":"es-US-Standard-A"}"""), sentBodies)
    }

    @Test
    fun ignoresProvidedVoiceAndSendsFixedCloudVoiceToBackend() = runBlocking {
        val sentBodies = mutableListOf<String>()
        val expectedBytes = byteArrayOf(10, 11, 12)
        val encodedAudio = Base64.getEncoder().encodeToString(expectedBytes)
        val client = RemoteAssistantTtsClient(
            backendUrl = TEST_URL,
            httpClient = okHttpClient(
                body = """{"audioBase64":"$encodedAudio","mimeType":"audio/mpeg"}""",
                contentType = "application/json",
                onRequestBody = sentBodies::add
            )
        )

        client.synthesize("Hola", AssistantVoiceOption.default)

        assertEquals(listOf("""{"text":"Hola","voice":"es-US-Standard-A"}"""), sentBodies)
    }

    @Test
    fun sendsFixedCloudVoiceForEveryAssistantPhrase() = runBlocking {
        val sentBodies = mutableListOf<String>()
        val expectedBytes = byteArrayOf(16, 17, 18)
        val encodedAudio = Base64.getEncoder().encodeToString(expectedBytes)
        val client = RemoteAssistantTtsClient(
            backendUrl = TEST_URL,
            httpClient = okHttpClient(
                body = """{"audioBase64":"$encodedAudio","mimeType":"audio/mpeg"}""",
                contentType = "application/json",
                onRequestBody = sentBodies::add
            )
        )

        client.synthesize("Primera frase", AssistantVoiceOption.default)
        client.synthesize("Segunda frase", AssistantVoiceOption.default)
        client.synthesize("Tercera frase", AssistantVoiceOption.default)

        assertEquals(
            listOf(
                """{"text":"Primera frase","voice":"es-US-Standard-A"}""",
                """{"text":"Segunda frase","voice":"es-US-Standard-A"}""",
                """{"text":"Tercera frase","voice":"es-US-Standard-A"}"""
            ),
            sentBodies
        )
    }

    @Test
    fun logsRemoteRequestAndBackendResponseForDebugging() = runBlocking {
        val logs = mutableListOf<String>()
        val expectedBytes = byteArrayOf(13, 14, 15)
        val client = RemoteAssistantTtsClient(
            backendUrl = TEST_URL,
            httpClient = okHttpClient(
                bodyBytes = expectedBytes,
                contentType = "audio/mpeg"
            ),
            debugLogger = logs::add
        )

        client.synthesize("Hola", AssistantVoiceOption.default)

        assertTrue(logs.any { it.contains("Remote TTS request started") })
        assertTrue(logs.any { it.contains("selectedVoice=local_phone") })
        assertTrue(logs.any { it.contains("backendVoice=es-US-Standard-A") })
        assertTrue(logs.any { it.contains("status=200") && it.contains("contentType=audio/mpeg") })
    }

    private fun okHttpClient(
        body: String = "",
        bodyBytes: ByteArray? = null,
        contentType: String,
        code: Int = 200,
        onRequestBody: ((String) -> Unit)? = null
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                onRequestBody?.invoke(chain.request().bodyAsString())
                val responseBody = if (bodyBytes != null) {
                    bodyBytes.toResponseBody(contentType.toMediaType())
                } else {
                    body.toResponseBody(contentType.toMediaType())
                }

                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("OK")
                    .body(responseBody)
                    .header("Content-Type", contentType)
                    .build()
            }
            .build()
    }

    private fun okhttp3.Request.bodyAsString(): String {
        val body = body ?: return ""
        val buffer = Buffer()
        body.writeTo(buffer)
        return buffer.readUtf8()
    }

    private companion object {
        const val TEST_URL = "https://tts.example.test/speak"
    }
}
