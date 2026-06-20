package com.luistureo.voicereminderapp.core.calendar.microsoft

import java.io.IOException

enum class MicrosoftCalendarErrorCode(
    val value: String,
    val invalidatesSession: Boolean
) {
    AUTH_CANCELLED("MICROSOFT_AUTH_CANCELLED", false),
    AUTH_NETWORK_IO("MICROSOFT_AUTH_NETWORK_IO", false),
    AUTH_MSAL_CLIENT_ERROR("MICROSOFT_AUTH_MSAL_CLIENT_ERROR", true),
    AUTH_MSAL_SERVICE_ERROR("MICROSOFT_AUTH_MSAL_SERVICE_ERROR", false),
    AUTH_SCOPE_DENIED("MICROSOFT_AUTH_SCOPE_DENIED", true),
    DELTA_LINK_INVALID("MICROSOFT_DELTA_LINK_INVALID", false),
    GRAPH_400_BAD_REQUEST("MICROSOFT_GRAPH_400_BAD_REQUEST", false),
    GRAPH_401("MICROSOFT_GRAPH_401", true),
    GRAPH_403("MICROSOFT_GRAPH_403", true),
    GRAPH_404("MICROSOFT_GRAPH_404", false),
    GRAPH_429("MICROSOFT_GRAPH_429", false),
    GRAPH_5XX("MICROSOFT_GRAPH_5XX", false),
    SYNC_IO_EXCEPTION("MICROSOFT_SYNC_IO_EXCEPTION", false);

    companion object {
        fun fromStoredValue(value: String?): MicrosoftCalendarErrorCode? =
            entries.firstOrNull { it.value == value }

        fun fromFailure(error: Throwable): MicrosoftCalendarErrorCode {
            val chain = generateSequence(error) { it.cause }.toList()
            val graphStatus = chain.filterIsInstance<MicrosoftGraphApiException>()
                .firstOrNull()?.statusCode
            if (graphStatus != null) {
                val graphError = chain.filterIsInstance<MicrosoftGraphApiException>().first()
                return when (graphStatus) {
                    400, 410 -> if (graphError.isDeltaLinkInvalid) {
                        DELTA_LINK_INVALID
                    } else {
                        GRAPH_400_BAD_REQUEST
                    }
                    401 -> GRAPH_401
                    403 -> GRAPH_403
                    404 -> GRAPH_404
                    429 -> GRAPH_429
                    in 500..599 -> GRAPH_5XX
                    else -> SYNC_IO_EXCEPTION
                }
            }
            if (chain.any { it is IOException }) return SYNC_IO_EXCEPTION
            val normalized = chain.joinToString(" ") {
                "${it.javaClass.simpleName} ${it.message.orEmpty()}"
            }.uppercase()
            return when {
                "CANCEL" in normalized -> AUTH_CANCELLED
                "NETWORK" in normalized || "IO_ERROR" in normalized -> AUTH_NETWORK_IO
                "SCOPE" in normalized || "CONSENT" in normalized ||
                        "AUTHORIZATION_DECLINED" in normalized -> AUTH_SCOPE_DENIED
                "MSALSERVICE" in normalized || "SERVICE_NOT_AVAILABLE" in normalized ->
                    AUTH_MSAL_SERVICE_ERROR
                else -> AUTH_MSAL_CLIENT_ERROR
            }
        }

        fun fromAuthFailure(error: Throwable): MicrosoftCalendarErrorCode {
            val chain = generateSequence(error) { it.cause }.toList()
            if (chain.any { it is IOException }) return AUTH_NETWORK_IO
            return when (val code = fromFailure(error)) {
                SYNC_IO_EXCEPTION -> AUTH_NETWORK_IO
                else -> code
            }
        }
    }
}
