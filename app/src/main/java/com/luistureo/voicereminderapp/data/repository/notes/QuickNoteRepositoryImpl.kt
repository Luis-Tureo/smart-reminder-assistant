package com.luistureo.voicereminderapp.data.repository.notes

import com.luistureo.voicereminderapp.core.notes.QuickNoteSearchPattern
import com.luistureo.voicereminderapp.data.local.dao.notes.QuickNoteDao
import com.luistureo.voicereminderapp.data.local.entity.notes.QuickNoteEntity
import com.luistureo.voicereminderapp.data.mapper.notes.toDomain
import com.luistureo.voicereminderapp.data.mapper.notes.toEntity
import com.luistureo.voicereminderapp.domain.notes.model.QuickNote
import com.luistureo.voicereminderapp.domain.notes.model.QuickNoteDraft
import com.luistureo.voicereminderapp.domain.notes.model.QuickNoteFilter
import com.luistureo.voicereminderapp.domain.notes.repository.QuickNoteRepository
import com.luistureo.voicereminderapp.domain.notes.validation.QuickNoteValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class QuickNoteRepositoryImpl(
    private val dao: QuickNoteDao,
    private val nowProvider: () -> Long = System::currentTimeMillis
) : QuickNoteRepository {

    override fun observeNotes(
        filter: QuickNoteFilter,
        query: String
    ): Flow<List<QuickNote>> {
        val archived = filter == QuickNoteFilter.ARCHIVED
        val pinnedOnly = filter == QuickNoteFilter.PINNED
        return dao.observeNotes(
            archived = archived,
            pinnedOnly = pinnedOnly,
            searchPattern = QuickNoteSearchPattern.contains(query)
        ).map { entities -> entities.map(QuickNoteEntity::toDomain) }
    }

    override suspend fun getNote(noteId: Int): QuickNote? = dao.getById(noteId)?.toDomain()

    override suspend fun saveNote(draft: QuickNoteDraft): QuickNote? {
        val normalized = QuickNoteValidator.normalizeOrNull(draft) ?: return null
        val existing = normalized.id.takeIf { it > 0 }?.let { dao.getById(it) }
        val now = nextTimestamp(existing?.updatedAt)

        val entity = if (existing == null) {
            QuickNoteEntity(
                id = 0,
                title = normalized.title,
                content = normalized.content,
                isPinned = normalized.isPinned,
                colorTag = normalized.colorTag?.name,
                createdAt = now,
                updatedAt = now,
                isArchived = normalized.isArchived
            )
        } else {
            existing.copy(
                title = normalized.title,
                content = normalized.content,
                isPinned = normalized.isPinned,
                colorTag = normalized.colorTag?.name,
                updatedAt = now,
                isArchived = normalized.isArchived
            )
        }

        val noteId = if (existing == null) {
            dao.insert(entity).toInt()
        } else {
            if (dao.update(entity) != 1) return null
            entity.id
        }
        return dao.getById(noteId)?.toDomain()
    }

    override suspend fun setPinned(noteId: Int, pinned: Boolean): Boolean {
        val existing = dao.getById(noteId) ?: return false
        return dao.setPinned(noteId, pinned, nextTimestamp(existing.updatedAt)) == 1
    }

    override suspend fun setArchived(noteId: Int, archived: Boolean): Boolean {
        val existing = dao.getById(noteId) ?: return false
        return dao.setArchived(noteId, archived, nextTimestamp(existing.updatedAt)) == 1
    }

    override suspend fun deleteNote(noteId: Int): QuickNote? {
        val existing = dao.getById(noteId) ?: return null
        return if (dao.deleteById(noteId) == 1) existing.toDomain() else null
    }

    override suspend fun restoreDeletedNote(note: QuickNote): Boolean = runCatching {
        dao.insert(note.toEntity())
        true
    }.getOrDefault(false)

    private fun nextTimestamp(previous: Long?): Long {
        val now = nowProvider()
        return previous?.let { maxOf(now, it + 1L) } ?: now
    }
}
