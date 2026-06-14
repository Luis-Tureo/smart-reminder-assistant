package com.luistureo.ttsbackend

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import java.util.Base64

class TtsHttpHandlerTest {

    private var server: HttpServer? = null

    @After
    fun tearDown() {
        server?.stop(0)
        server = null
    }

    @Test
    fun validTextReturnsMp3Audio() {
        val expectedAudio = byteArrayOf(1, 2, 3)
        val baseUrl = startServer(
            config = TtsBackendConfig(maxTextLength = 50),
            synthesizer = FakeSynthesizer(audio = expectedAudio)
        )

        val response = post(baseUrl, """{"text":"Hola asistente","languageCode":"es-US"}""")

        assertEquals(200, response.code)
        assertEquals("audio/mpeg", response.contentType)
        assertArrayEquals(expectedAudio, response.body)
    }

    @Test
    fun validTextCanReturnBase64JsonAudio() {
        val expectedAudio = byteArrayOf(4, 5, 6)
        val baseUrl = startServer(
            config = TtsBackendConfig(maxTextLength = 50, responseMode = "json"),
            synthesizer = FakeSynthesizer(audio = expectedAudio)
        )

        val response = post(baseUrl, """{"text":"Hola"}""")
        val body = response.body.toString(Charsets.UTF_8)

        assertEquals(200, response.code)
        assertTrue(response.contentType.startsWith("application/json"))
        assertTrue(body.contains(Base64.getEncoder().encodeToString(expectedAudio)))
        assertTrue(body.contains("audio/mpeg"))
    }

    @Test
    fun emptyTextIsRejected() {
        val baseUrl = startServer(
            config = TtsBackendConfig(maxTextLength = 50),
            synthesizer = FakeSynthesizer()
        )

        val response = post(baseUrl, """{"text":"   "}""")
        val body = response.body.toString(Charsets.UTF_8)

        assertEquals(400, response.code)
        assertTrue(body.contains("empty_text"))
    }

    @Test
    fun tooLongTextIsRejected() {
        val baseUrl = startServer(
            config = TtsBackendConfig(maxTextLength = 4),
            synthesizer = FakeSynthesizer()
        )

        val response = post(baseUrl, """{"text":"demasiado largo"}""")
        val body = response.body.toString(Charsets.UTF_8)

        assertEquals(413, response.code)
        assertTrue(body.contains("text_too_long"))
    }

    @Test
    fun googleTtsFailureReturnsClearError() {
        val baseUrl = startServer(
            config = TtsBackendConfig(maxTextLength = 50),
            synthesizer = FakeSynthesizer(failure = IllegalStateException("Google failed"))
        )

        val response = post(baseUrl, """{"text":"Hola"}""")
        val body = response.body.toString(Charsets.UTF_8)

        assertEquals(502, response.code)
        assertTrue(body.contains("tts_failure"))
        assertTrue(body.contains("No se pudo sintetizar"))
    }

    private fun startServer(
        config: TtsBackendConfig,
        synthesizer: SpeechSynthesizer
    ): String {
        val localServer = HttpServer.create(InetSocketAddress(0), 0)
        localServer.createContext("/tts", TtsHttpHandler(config, synthesizer))
        localServer.start()
        server = localServer
        return "http://127.0.0.1:${localServer.address.port}/tts"
    }

    private fun post(url: String, body: String): TestResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        val responseStream = if (connection.responseCode >= 400) {
            connection.errorStream
        } else {
            connection.inputStream
        }

        return TestResponse(
            code = connection.responseCode,
            contentType = connection.contentType.orEmpty(),
            body = responseStream.use { it?.readBytes() ?: ByteArray(0) }
        )
    }

    private data class TestResponse(
        val code: Int,
        val contentType: String,
        val body: ByteArray
    )

    private class FakeSynthesizer(
        private val audio: ByteArray = byteArrayOf(1),
        private val failure: Exception? = null
    ) : SpeechSynthesizer {
        override fun synthesize(request: TtsRequest): ByteArray {
            failure?.let { throw it }
            return audio
        }
    }
}
