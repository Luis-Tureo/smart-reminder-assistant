package com.luistureo.voicereminderapp.core.loan

import kotlin.math.abs

object ClpFormatter {
    fun format(amountClp: Long): String {
        val sign = if (amountClp < 0L) "-" else ""
        val digits = abs(amountClp).toString()
            .reversed()
            .chunked(3)
            .joinToString(".")
            .reversed()
        return "$sign\$$digits"
    }

    fun parse(rawValue: String): Long? {
        val normalized = rawValue
            .replace("$", "")
            .replace(".", "")
            .replace(",", "")
            .replace("CLP", "", ignoreCase = true)
            .trim()

        return normalized.toLongOrNull()?.takeIf { it >= 0L }
    }
}
