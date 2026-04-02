package com.luistureo.voicereminderapp.core.ai

import android.util.Log
import com.luistureo.voicereminderapp.domain.model.AssistantIntent
import com.luistureo.voicereminderapp.domain.model.AssistantResponse
import com.luistureo.voicereminderapp.domain.model.ReminderDraft
import com.luistureo.voicereminderapp.domain.service.ChatAssistantService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class GeminiAssistantService(
    private val apiKey: String,
    private val client: OkHttpClient = OkHttpClient()
) : ChatAssistantService {

    override suspend fun processMessage(
        userMessage: String,
        currentDraft: ReminderDraft?
    ): AssistantResponse = withContext(Dispatchers.IO) {
        try {
            Log.d("AI_DEBUG", "=== LLAMANDO A GEMINI ===")
            Log.d("AI_DEBUG", "Mensaje usuario: $userMessage")
            Log.d("AI_DEBUG", "Draft actual: $currentDraft")

            val systemPrompt = buildSystemPrompt(currentDraft)

            val requestBody = JSONObject().apply {
                put(
                    "contents",
                    JSONArray().apply {
                        put(
                            JSONObject().apply {
                                put("role", "user")
                                put(
                                    "parts",
                                    JSONArray().apply {
                                        put(
                                            JSONObject().apply {
                                                put(
                                                    "text",
                                                    """
                                                    $systemPrompt
                                                    
                                                    Mensaje del usuario:
                                                    $userMessage
                                                    """.trimIndent()
                                                )
                                            }
                                        )
                                    }
                                )
                            }
                        )
                    }
                )

                put(
                    "generationConfig",
                    JSONObject().apply {
                        put("temperature", 0.1)
                        put("maxOutputTokens", 400)
                    }
                )
            }

            val request = Request.Builder()
                .url(
                    "https://generativelanguage.googleapis.com/v1beta/models/" +
                            "gemini-2.5-flash:generateContent?key=$apiKey"
                )
                .addHeader("Content-Type", "application/json")
                .post(
                    requestBody
                        .toString()
                        .toRequestBody("application/json".toMediaType())
                )
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            Log.d("AI_DEBUG", "HTTP code: ${response.code}")
            Log.d("AI_DEBUG", "HTTP body completo: $body")

            if (!response.isSuccessful) {
                Log.e("AI_DEBUG", "Error HTTP en Gemini")

                return@withContext AssistantResponse(
                    reply = "No pude procesar tu solicitud en este momento.",
                    intent = AssistantIntent.UNKNOWN
                )
            }

            val root = JSONObject(body)
            val candidates = root.optJSONArray("candidates")

            if (candidates == null || candidates.length() == 0) {
                Log.e("AI_DEBUG", "Gemini sin candidates")

                return@withContext AssistantResponse(
                    reply = "No logré entenderte bien. Intenta nuevamente.",
                    intent = AssistantIntent.UNKNOWN
                )
            }

            val content = candidates
                .getJSONObject(0)
                .optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                ?: ""

            Log.d("AI_DEBUG", "Contenido crudo Gemini: $content")

            if (content.isBlank()) {
                Log.e("AI_DEBUG", "Gemini devolvió contenido vacío")

                return@withContext AssistantResponse(
                    reply = "No logré interpretar tu solicitud.",
                    intent = AssistantIntent.UNKNOWN
                )
            }

            val parsedResponse = parseAssistantResponse(content)

            Log.d("AI_DEBUG", "Respuesta parseada: $parsedResponse")

            parsedResponse
        } catch (exception: Exception) {
            Log.e("AI_DEBUG", "Excepción en Gemini", exception)

            AssistantResponse(
                reply = "Hubo un problema al procesar tu solicitud.",
                intent = AssistantIntent.UNKNOWN
            )
        }
    }

    private fun buildSystemPrompt(currentDraft: ReminderDraft?): String {
        val draftText = currentDraft?.text ?: ""
        val draftDate = currentDraft?.date ?: ""
        val draftTime = currentDraft?.time ?: ""

        return """
            Eres una secretaria ejecutiva profesional especializada únicamente en crear recordatorios.

            Tu única tarea es interpretar el mensaje del usuario para extraer datos de un recordatorio.

            REGLAS CRÍTICAS:
            - Siempre intenta extraer información útil del mensaje.
            - El usuario puede hablar en cualquier orden: primero fecha, luego texto, luego hora; o cualquier otra combinación.
            - Si el usuario solo dice una hora, una fecha o una parte del recordatorio, úsalo para completar el borrador actual.
            - No rechaces mensajes por parecer incompletos.
            - No inventes información.
            - No agregues datos que el usuario no dijo.
            - No calcules por tu cuenta fechas relativas como hoy, mañana o pasado mañana.
            - Si el usuario dice hoy, mañana o pasado mañana, devuelve reminderDate=null.
            - Si no estás 100% seguro de la fecha, devuelve reminderDate=null.
            - Si no estás 100% seguro de la hora, devuelve reminderTime=null.
            - Si no estás 100% seguro del texto del recordatorio, devuelve reminderText=null.
            - shouldSaveReminder=true SOLO si el mensaje actual, junto al borrador actual, deja claramente completo el recordatorio con texto, fecha y hora.
            - Si ya está completo con el borrador actual más el mensaje del usuario, no vuelvas a pedir el dato faltante.
            - reply debe ser breve y natural.

            FORMATO DE FECHA Y HORA:
            - reminderDate debe venir en formato dd/MM/yyyy solo si el usuario la dijo de forma explícita y suficientemente clara.
            - reminderTime debe venir en formato HH:mm solo si el usuario dijo una hora clara.

            RESPONDE SIEMPRE SOLO EN JSON VÁLIDO.
            No agregues explicaciones fuera del JSON.
            No uses bloques markdown.
            No uses ```json.

            Formato obligatorio:
            {
              "reply": "mensaje natural para el usuario",
              "intent": "CREATE_REMINDER | COMPLETE_REMINDER_DATA | UNKNOWN",
              "reminderText": "string o null",
              "reminderDate": "string o null",
              "reminderTime": "string o null",
              "shouldSaveReminder": true o false
            }

            Borrador actual:
            {
              "text": "${escapeJson(draftText)}",
              "date": "${escapeJson(draftDate)}",
              "time": "${escapeJson(draftTime)}"
            }
        """.trimIndent()
    }

    private fun parseAssistantResponse(content: String): AssistantResponse {
        return try {
            val cleanJson = content
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val json = JSONObject(cleanJson)

            AssistantResponse(
                reply = json.optString("reply", "Entendido."),
                intent = parseIntent(json.optString("intent", "UNKNOWN")),
                reminderText = json.optString("reminderText")
                    .takeIf { it.isNotBlank() && it != "null" },
                reminderDate = json.optString("reminderDate")
                    .takeIf { it.isNotBlank() && it != "null" },
                reminderTime = json.optString("reminderTime")
                    .takeIf { it.isNotBlank() && it != "null" },
                shouldSaveReminder = json.optBoolean("shouldSaveReminder", false)
            )
        } catch (exception: Exception) {
            AssistantResponse(
                reply = "No logré interpretar correctamente la respuesta. Intenta nuevamente.",
                intent = AssistantIntent.UNKNOWN
            )
        }
    }

    private fun parseIntent(value: String): AssistantIntent {
        return when (value) {
            "CREATE_REMINDER" -> AssistantIntent.CREATE_REMINDER
            "COMPLETE_REMINDER_DATA" -> AssistantIntent.COMPLETE_REMINDER_DATA
            else -> AssistantIntent.UNKNOWN
        }
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
    }
}