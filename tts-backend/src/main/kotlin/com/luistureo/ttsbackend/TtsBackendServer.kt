package com.luistureo.ttsbackend

import com.google.cloud.texttospeech.v1.AudioConfig
import com.google.cloud.texttospeech.v1.AudioEncoding
import com.google.cloud.texttospeech.v1.SynthesisInput
import com.google.cloud.texttospeech.v1.SynthesizeSpeechRequest
import com.google.cloud.texttospeech.v1.TextToSpeechClient
import com.google.cloud.texttospeech.v1.VoiceSelectionParams
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetSocketAddress
import java.util.Base64
import java.util.logging.Level
import java.util.logging.Logger

fun main() {
    val config = TtsBackendConfig.fromEnvironment()
    val server = HttpServer.create(InetSocketAddress(config.port), 0)
    server.createContext("/tts", TtsHttpHandler(config, GoogleCloudTtsSynthesizer(config)))
    server.executor = null
    logger.info("TTS backend listening on port ${config.port}")
    server.start()
}

data class TtsBackendConfig(
    val port: Int = 8080,
    val maxTextLength: Int = 500,
    val defaultLanguageCode: String = "es-ES",
    val defaultVoiceName: String = "",
    val speakingRate: Double = 1.0,
    val pitch: Double = 0.0,
    val audioEncoding: String = "MP3",
    val responseMode: String = "audio",
    val allowedOrigins: Set<String> = emptySet()
) {
    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): TtsBackendConfig {
            return TtsBackendConfig(
                port = env["PORT"]?.toIntOrNull() ?: 8080,
                maxTextLength = env["TTS_MAX_TEXT_LENGTH"]?.toIntOrNull() ?: 500,
                defaultLanguageCode = env["TTS_DEFAULT_LANGUAGE_CODE"].orEmpty().ifBlank { "es-ES" },
                defaultVoiceName = env["TTS_DEFAULT_VOICE_NAME"].orEmpty(),
                speakingRate = env["TTS_SPEAKING_RATE"]?.toDoubleOrNull() ?: 1.0,
                pitch = env["TTS_PITCH"]?.toDoubleOrNull() ?: 0.0,
                audioEncoding = env["TTS_AUDIO_ENCODING"].orEmpty().ifBlank { "MP3" },
                responseMode = env["TTS_RESPONSE_MODE"].orEmpty().ifBlank { "audio" },
                allowedOrigins = env["TTS_ALLOWED_ORIGINS"].orEmpty()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toSet()
            )
        }
    }
}

data class TtsRequest(
    val text: String,
    val languageCode: String,
    val voice: String?
)

interface SpeechSynthesizer {
    fun synthesize(request: TtsRequest): ByteArray
}

class GoogleCloudTtsSynthesizer(
    private val config: TtsBackendConfig
) : SpeechSynthesizer {

    override fun synthesize(request: TtsRequest): ByteArray {
        TextToSpeechClient.create().use { client ->
            val voiceBuilder = VoiceSelectionParams.newBuilder()
                .setLanguageCode(request.languageCode)

            request.voice
                ?.takeIf { it.isNotBlank() }
                ?.let { voiceBuilder.name = it }

            val cloudRequest = SynthesizeSpeechRequest.newBuilder()
                .setInput(SynthesisInput.newBuilder().setText(request.text).build())
                .setVoice(voiceBuilder.build())
                .setAudioConfig(
                    AudioConfig.newBuilder()
                        .setAudioEncoding(AudioEncoding.valueOf(config.audioEncoding))
                        .setSpeakingRate(config.speakingRate)
                        .setPitch(config.pitch)
                        .build()
                )
                .build()

            return client.synthesizeSpeech(cloudRequest).audioContent.toByteArray()
        }
    }
}

class TtsHttpHandler(
    private val config: TtsBackendConfig,
    private val synthesizer: SpeechSynthesizer
) : HttpHandler {

    override fun handle(exchange: HttpExchange) {
        try {
            addCorsHeadersIfConfigured(exchange)

            if (exchange.requestMethod.equals("OPTIONS", ignoreCase = true)) {
                exchange.sendResponseHeaders(204, -1)
                return
            }

            if (!exchange.requestMethod.equals("POST", ignoreCase = true)) {
                exchange.respondJson(405, errorJson("method_not_allowed", "Usa POST /tts."))
                return
            }

            val request = parseRequest(exchange.requestBody.readBytes().toString(Charsets.UTF_8))
                ?: return exchange.respondJson(400, errorJson("invalid_json", "El cuerpo debe ser JSON valido."))

            val validationError = validate(request)
            if (validationError != null) {
                exchange.respondJson(validationError.first, validationError.second)
                return
            }

            logger.info("TTS request accepted: chars=${request.text.length}, language=${request.languageCode}")
            val audioBytes = synthesizer.synthesize(request)
            if (audioBytes.isEmpty()) {
                exchange.respondJson(502, errorJson("tts_empty_audio", "Google Text-to-Speech no devolvio audio."))
                return
            }

            if (config.responseMode.equals("json", ignoreCase = true)) {
                exchange.respondJson(
                    200,
                    buildJsonObject {
                        put("audioBase64", JsonPrimitive(Base64.getEncoder().encodeToString(audioBytes)))
                        put("mimeType", JsonPrimitive(mimeType()))
                    }.toString()
                )
            } else {
                exchange.respondBytes(200, audioBytes, mimeType())
            }
        } catch (exception: Exception) {
            logger.log(Level.WARNING, "TTS request failed without exposing text", exception)
            exchange.respondJson(502, errorJson("tts_failure", "No se pudo sintetizar el audio."))
        } finally {
            exchange.close()
        }
    }

    private fun parseRequest(rawBody: String): TtsRequest? {
        val json = runCatching { Json.parseToJsonElement(rawBody).jsonObject }.getOrNull() ?: return null
        val text = json.stringValue("text") ?: ""
        val languageCode = json.stringValue("languageCode")
            ?.takeIf { it.isNotBlank() }
            ?: config.defaultLanguageCode
        val voice = json.stringValue("voice")
            ?.takeIf { it.isNotBlank() }
            ?: config.defaultVoiceName.takeIf { it.isNotBlank() }

        return TtsRequest(
            text = text.trim(),
            languageCode = languageCode.trim(),
            voice = voice?.trim()
        )
    }

    private fun validate(request: TtsRequest): Pair<Int, String>? {
        if (request.text.isBlank()) {
            return 400 to errorJson("empty_text", "El texto no puede estar vacio.")
        }

        if (request.text.length > config.maxTextLength) {
            return 413 to errorJson("text_too_long", "El texto supera el maximo permitido.")
        }

        if (request.languageCode.isBlank()) {
            return 400 to errorJson("invalid_language", "languageCode no puede estar vacio.")
        }

        return null
    }

    private fun addCorsHeadersIfConfigured(exchange: HttpExchange) {
        if (config.allowedOrigins.isEmpty()) return

        val origin = exchange.requestHeaders.getFirst("Origin").orEmpty()
        val allowedOrigin = when {
            "*" in config.allowedOrigins -> "*"
            origin in config.allowedOrigins -> origin
            else -> null
        } ?: return

        exchange.responseHeaders.add("Access-Control-Allow-Origin", allowedOrigin)
        exchange.responseHeaders.add("Access-Control-Allow-Methods", "POST, OPTIONS")
        exchange.responseHeaders.add("Access-Control-Allow-Headers", "Content-Type")
    }

    private fun mimeType(): String {
        return when (config.audioEncoding.uppercase()) {
            "MP3" -> "audio/mpeg"
            "OGG_OPUS" -> "audio/ogg"
            "LINEAR16" -> "audio/wav"
            else -> "application/octet-stream"
        }
    }

    private fun JsonObject.stringValue(key: String): String? {
        return this[key]?.jsonPrimitive?.contentOrNull
    }
}

private fun HttpExchange.respondJson(statusCode: Int, body: String) {
    respondBytes(statusCode, body.toByteArray(Charsets.UTF_8), "application/json; charset=utf-8")
}

private fun HttpExchange.respondBytes(statusCode: Int, body: ByteArray, contentType: String) {
    responseHeaders.set("Content-Type", contentType)
    responseHeaders.set("Cache-Control", "no-store")
    sendResponseHeaders(statusCode, body.size.toLong())
    responseBody.use { it.write(body) }
}

private fun errorJson(code: String, message: String): String {
    return buildJsonObject {
        put("error", JsonPrimitive(code))
        put("message", JsonPrimitive(message))
    }.toString()
}

private val logger = Logger.getLogger("tts-backend")
