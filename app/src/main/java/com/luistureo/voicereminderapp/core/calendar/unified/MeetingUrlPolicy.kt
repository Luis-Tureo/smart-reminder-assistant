package com.luistureo.voicereminderapp.core.calendar.unified

import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import java.net.URI

object MeetingUrlPolicy {
    fun isSupportedMeetingUrl(url: String?): Boolean {
        val uri = parseHttpsUri(url) ?: return false
        val host = uri.host?.lowercase() ?: return false
        return host == "meet.google.com" ||
                host == "teams.microsoft.com" ||
                host == "teams.live.com"
    }

    fun selectPreferredUrl(
        originProvider: CalendarProvider,
        urlsByProvider: Map<CalendarProvider, String>,
        fallbackUrl: String? = null
    ): String? {
        val supportedUrls = urlsByProvider.filterValues(::isSupportedMeetingUrl)
        val supportedFallback = fallbackUrl?.takeIf(::isSupportedMeetingUrl)
        return supportedUrls[originProvider]
            ?: supportedFallback?.takeIf { providerForUrl(it) == originProvider }
            ?: when (originProvider) {
                CalendarProvider.GOOGLE_CALENDAR ->
                    supportedUrls[CalendarProvider.MICROSOFT_CALENDAR]
                CalendarProvider.MICROSOFT_CALENDAR ->
                    supportedUrls[CalendarProvider.GOOGLE_CALENDAR]
                CalendarProvider.APP ->
                    supportedUrls[CalendarProvider.GOOGLE_CALENDAR]
                        ?: supportedUrls[CalendarProvider.MICROSOFT_CALENDAR]
            }
            ?: supportedFallback
    }

    fun providerForUrl(url: String?): CalendarProvider? {
        val host = parseHttpsUri(url)?.host?.lowercase()
        return when {
            host == "meet.google.com" ->
                CalendarProvider.GOOGLE_CALENDAR
            host == "teams.microsoft.com" || host == "teams.live.com" ->
                CalendarProvider.MICROSOFT_CALENDAR
            else -> null
        }
    }

    fun extractFirstSupportedUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return Regex("https://[^\\s<\\\"']+", RegexOption.IGNORE_CASE)
            .findAll(text)
            .map { it.value.trimEnd('.', ',', ')', ']', '>', ';') }
            .firstOrNull(::isSupportedMeetingUrl)
    }

    private fun parseHttpsUri(url: String?): URI? {
        val value = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.userInfo != null) return null
        return uri.takeIf { !it.host.isNullOrBlank() && !it.rawPath.isNullOrBlank() }
    }
}
