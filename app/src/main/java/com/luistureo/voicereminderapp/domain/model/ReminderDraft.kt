package com.luistureo.voicereminderapp.domain.model

data class ReminderDraft(
    val text: String? = null,
    val date: String? = null,
    val time: String? = null
) {
    fun isReadyToSave(): Boolean {
        return !text.isNullOrBlank() &&
                !date.isNullOrBlank() &&
                !time.isNullOrBlank()
    }
}