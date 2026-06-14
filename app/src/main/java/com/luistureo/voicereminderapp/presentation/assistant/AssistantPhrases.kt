package com.luistureo.voicereminderapp.presentation.assistant

object AssistantPhrases {
    const val START_LISTENING = "Perfecto, te escucho. \u00bfQu\u00e9 quieres recordar?"
    const val LISTENING_SHORT = "Perfecto, te escucho."
    const val ASK_DATE = "\u00bfPara qu\u00e9 d\u00eda?"
    const val ASK_TIME = "\u00bfA qu\u00e9 hora?"
    const val SAVE_SUCCESS = "Perfecto, lo dej\u00e9 agendado."
    const val SAVE_ERROR = "Ocurri\u00f3 un error al guardar el recordatorio."

    fun confirmation(date: String, time: String, isUrgent: Boolean): String {
        val urgencyLabel = if (isUrgent) " como urgente" else ""
        return "\u00bfLo dejo agendado$urgencyLabel para el $date a las $time?"
    }

    fun ambiguousTimeQuestion(hour: Int): String {
        return when (hour) {
            in 1..8 -> "\u00bfLo dejamos en la ma\u00f1ana o en la tarde?"
            in 9..11 -> "\u00bfLo dejamos en la ma\u00f1ana o en la noche?"
            12 -> "\u00bfA las 12, ma\u00f1ana o tarde?"
            else -> "\u00bfLo dejamos en la ma\u00f1ana, tarde o noche?"
        }
    }
}
