package com.luistureo.voicereminderapp.core.speech

data class AssistantTtsAudio(
    val bytes: ByteArray,
    val mimeType: String
) {
    val isPlayable: Boolean
        get() = bytes.isNotEmpty() && mimeType.startsWith("audio/", ignoreCase = true)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AssistantTtsAudio) return false
        return bytes.contentEquals(other.bytes) && mimeType == other.mimeType
    }

    override fun hashCode(): Int {
        return 31 * bytes.contentHashCode() + mimeType.hashCode()
    }
}
