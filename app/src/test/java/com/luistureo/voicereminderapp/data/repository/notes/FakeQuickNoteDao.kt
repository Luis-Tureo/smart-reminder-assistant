package com.luistureo.voicereminderapp.data.repository.notes

import com.luistureo.voicereminderapp.data.local.dao.notes.QuickNoteDao
import com.luistureo.voicereminderapp.data.local.entity.notes.QuickNoteEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal class FakeQuickNoteDao : QuickNoteDao {
    private val rows = linkedMapOf<Int, QuickNoteEntity>()
    private val revision = MutableStateFlow(0)
    private var nextId = 1

    var lastSearchPattern: String? = null
        private set

    val size: Int
        get() = rows.size

    override fun observeNotes(
        archived: Boolean,
        pinnedOnly: Boolean,
        searchPattern: String
    ): Flow<List<QuickNoteEntity>> {
        lastSearchPattern = searchPattern
        val literalQuery = decodeContainsPattern(searchPattern)
        return revision.map {
            rows.values
                .asSequence()
                .filter { it.isArchived == archived }
                .filter { !pinnedOnly || it.isPinned }
                .filter { note ->
                    note.title.orEmpty().contains(literalQuery, ignoreCase = true) ||
                        note.content.contains(literalQuery, ignoreCase = true)
                }
                .sortedWith(
                    compareByDescending<QuickNoteEntity> { it.isPinned }
                        .thenByDescending { it.updatedAt }
                        .thenByDescending { it.id }
                )
                .toList()
        }
    }

    override suspend fun getById(noteId: Int): QuickNoteEntity? = rows[noteId]

    override suspend fun insert(note: QuickNoteEntity): Long {
        val id = note.id.takeIf { it > 0 } ?: nextId
        check(id !in rows) { "Identificador duplicado: $id" }
        rows[id] = note.copy(id = id)
        nextId = maxOf(nextId, id + 1)
        notifyChanged()
        return id.toLong()
    }

    override suspend fun update(note: QuickNoteEntity): Int {
        if (note.id !in rows) return 0
        rows[note.id] = note
        notifyChanged()
        return 1
    }

    override suspend fun setPinned(noteId: Int, pinned: Boolean, updatedAt: Long): Int {
        val existing = rows[noteId] ?: return 0
        rows[noteId] = existing.copy(isPinned = pinned, updatedAt = updatedAt)
        notifyChanged()
        return 1
    }

    override suspend fun setArchived(noteId: Int, archived: Boolean, updatedAt: Long): Int {
        val existing = rows[noteId] ?: return 0
        rows[noteId] = existing.copy(isArchived = archived, updatedAt = updatedAt)
        notifyChanged()
        return 1
    }

    override suspend fun deleteById(noteId: Int): Int {
        if (rows.remove(noteId) == null) return 0
        notifyChanged()
        return 1
    }

    private fun notifyChanged() {
        revision.value += 1
    }

    private fun decodeContainsPattern(pattern: String): String {
        val body = pattern.removePrefix("%").removeSuffix("%")
        return buildString {
            var escaped = false
            body.forEach { character ->
                when {
                    escaped -> {
                        append(character)
                        escaped = false
                    }
                    character == '\\' -> escaped = true
                    else -> append(character)
                }
            }
            if (escaped) append('\\')
        }
    }
}
