package com.luistureo.voicereminderapp.data.local.dao.notes

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.luistureo.voicereminderapp.data.local.entity.notes.QuickNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickNoteDao {
    @Query(
        """
        SELECT * FROM quick_notes
        WHERE isArchived = :archived
          AND (:pinnedOnly = 0 OR isPinned = 1)
          AND (
              title LIKE :searchPattern ESCAPE '\' COLLATE NOCASE
              OR content LIKE :searchPattern ESCAPE '\' COLLATE NOCASE
          )
        ORDER BY isPinned DESC, updatedAt DESC, id DESC
        """
    )
    fun observeNotes(
        archived: Boolean,
        pinnedOnly: Boolean,
        searchPattern: String
    ): Flow<List<QuickNoteEntity>>

    @Query("SELECT * FROM quick_notes WHERE id = :noteId LIMIT 1")
    suspend fun getById(noteId: Int): QuickNoteEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(note: QuickNoteEntity): Long

    @Update(onConflict = OnConflictStrategy.ABORT)
    suspend fun update(note: QuickNoteEntity): Int

    @Query(
        """
        UPDATE quick_notes
        SET isPinned = :pinned, updatedAt = :updatedAt
        WHERE id = :noteId
        """
    )
    suspend fun setPinned(noteId: Int, pinned: Boolean, updatedAt: Long): Int

    @Query(
        """
        UPDATE quick_notes
        SET isArchived = :archived, updatedAt = :updatedAt
        WHERE id = :noteId
        """
    )
    suspend fun setArchived(noteId: Int, archived: Boolean, updatedAt: Long): Int

    @Query("DELETE FROM quick_notes WHERE id = :noteId")
    suspend fun deleteById(noteId: Int): Int
}
