package com.luistureo.voicereminderapp.core.calendar.unified

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

object MeetingContentSanitizer {
    private const val MAX_DESCRIPTION_LENGTH = 420

    fun cleanDescription(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val document = Jsoup.parse(raw)
        removeHiddenAndDecorativeContent(document)
        document.select("a[href]")
            .filter { MeetingUrlPolicy.isSupportedMeetingUrl(it.attr("abs:href").ifBlank { it.attr("href") }) }
            .forEach { it.remove() }
        document.select("br").append("\n")
        document.select("p, div, li, tr, h1, h2, h3, h4, h5, h6").forEach { element ->
            element.before("\n")
            element.after("\n")
        }
        val text = document.body().wholeText()
            .replace('\u00A0', ' ')
            .lines()
            .map(String::trim)
            .filter(String::isNotBlank)
            .takeWhile { !isInvitationBoilerplate(it) }
            .filterNot(::isTrackingOrMeetingLinkLine)
            .distinct()
            .joinToString("\n")
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        return text.take(MAX_DESCRIPTION_LENGTH).trimEnd().let {
            if (text.length > MAX_DESCRIPTION_LENGTH) "$it…" else it
        }
    }

    fun extractSupportedMeetingUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val document = Jsoup.parse(raw)
        val hrefUrl = document.select("a[href]")
            .asSequence()
            .map { it.attr("abs:href").ifBlank { it.attr("href") } }
            .firstOrNull(MeetingUrlPolicy::isSupportedMeetingUrl)
        if (hrefUrl != null) return hrefUrl.trim()
        return URL_PATTERN.findAll(document.text())
            .map { it.value.trimEnd('.', ',', ')', ']', '>', ';') }
            .firstOrNull(MeetingUrlPolicy::isSupportedMeetingUrl)
    }

    private fun removeHiddenAndDecorativeContent(document: Document) {
        document.select("head, style, script, noscript, svg, img, meta, link").remove()
        document.select("[hidden], [aria-hidden=true], [style*=display:none], [style*=display: none], [style*=visibility:hidden], [style*=visibility: hidden]").remove()
    }

    private fun isInvitationBoilerplate(line: String): Boolean {
        val normalized = line.lowercase()
        return BOILERPLATE_MARKERS.any(normalized::contains)
    }

    private fun isTrackingOrMeetingLinkLine(line: String): Boolean {
        val normalized = line.lowercase()
        return MeetingUrlPolicy.extractFirstSupportedUrl(line) != null ||
            normalized.contains("privacy") || normalized.contains("privacidad") ||
            normalized.contains("help") && normalized.contains("meeting") ||
            normalized.contains("tracking") || normalized.startsWith("join conversation")
    }

    private val BOILERPLATE_MARKERS = listOf(
        "________________________________________________________________________________",
        "microsoft teams meeting",
        "reunión de microsoft teams",
        "join the meeting now",
        "unirse a la reunión ahora",
        "need help?",
        "meeting id:",
        "id. de reunión:",
        "passcode:",
        "código de acceso:",
        "creado desde smart reminder assistant",
        "participants:",
        "participantes:",
        "attendees:",
        "asistentes:"
    )

    private val URL_PATTERN = Regex("https://[^\\s<\\\"']+", RegexOption.IGNORE_CASE)
}
