package com.luistureo.voicereminderapp.core.calendar.unified

import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class CalendarHttpRequestExecutor(
    private val client: OkHttpClient,
    private val usageGuard: CalendarApiUsageGuard? = null
) {
    suspend fun execute(
        request: Request,
        provider: CalendarProvider,
        requestType: String
    ): Response {
        repeat(CalendarBackoffPolicy.MAX_ATTEMPTS) { attempt ->
            if (usageGuard?.tryAcquireRequest(provider, requestType) == false) {
                throw CalendarQuotaLimitException(provider)
            }

            val response = client.newCall(request).execute()
            if (
                !CalendarBackoffPolicy.shouldRetry(response.code) ||
                attempt == CalendarBackoffPolicy.MAX_ATTEMPTS - 1
            ) {
                return response
            }

            val retryAfterSeconds = CalendarBackoffPolicy.parseRetryAfterSeconds(
                response.header("Retry-After"),
                System.currentTimeMillis()
            )
            if (
                retryAfterSeconds != null &&
                retryAfterSeconds > CalendarBackoffPolicy.MAX_INLINE_RETRY_AFTER_SECONDS
            ) {
                CalendarSyncLogger.cooldown(
                    provider,
                    "retry_after_deferred",
                    retryAfterSeconds * 1_000L
                )
                return response
            }
            val delayMillis = CalendarBackoffPolicy.delayMillis(
                attempt = attempt,
                retryAfterSeconds = retryAfterSeconds
            )
            CalendarSyncLogger.retryBackoff(
                provider = provider,
                requestType = requestType,
                statusCode = response.code,
                attempt = attempt + 1,
                delayMillis = delayMillis,
                retryAfterUsed = retryAfterSeconds != null
            )
            response.close()
            delay(delayMillis)
        }
        error("No fue posible ejecutar la solicitud de calendario.")
    }
}
