package com.luistureo.voicereminderapp.presentation.state

import com.luistureo.voicereminderapp.core.nlp.ReminderContentCleaner
import com.luistureo.voicereminderapp.domain.model.ReminderDraft
import com.luistureo.voicereminderapp.domain.model.ReminderSource

object ReminderFormDraftMergePolicy {

    fun merge(
        currentFormState: ReminderFormState,
        draft: ReminderDraft,
        source: ReminderSource = draft.source
    ): ReminderFormState {
        val cleanedDetail = ReminderContentCleaner.cleanDetail(draft.text)
        val cleanedTitle = draft.title?.let(ReminderContentCleaner::buildTitle)
            ?: ReminderContentCleaner.buildTitle(cleanedDetail)

        return currentFormState.copy(
            title = cleanedTitle ?: currentFormState.title,
            detail = cleanedDetail ?: currentFormState.detail,
            date = draft.date ?: currentFormState.date,
            time = draft.time ?: currentFormState.time,
            isUrgent = currentFormState.isUrgent || draft.isUrgent,
            source = source
        )
    }
}
