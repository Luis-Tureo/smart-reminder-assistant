package com.luistureo.voicereminderapp.data.repository.notes

import android.content.Context
import com.luistureo.voicereminderapp.data.local.database.ReminderDatabase
import com.luistureo.voicereminderapp.domain.notes.repository.QuickNoteRepository

object QuickNoteRepositoryProvider {
    fun create(context: Context): QuickNoteRepository = QuickNoteRepositoryImpl(
        ReminderDatabase.getDatabase(context.applicationContext).quickNoteDao()
    )
}
