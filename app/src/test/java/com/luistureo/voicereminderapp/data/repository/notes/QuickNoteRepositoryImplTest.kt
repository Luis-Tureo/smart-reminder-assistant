package com.luistureo.voicereminderapp.data.repository.notes

import com.luistureo.voicereminderapp.domain.notes.model.QuickNoteColorTag
import com.luistureo.voicereminderapp.domain.notes.model.QuickNoteDraft
import com.luistureo.voicereminderapp.domain.notes.model.QuickNoteFilter
import com.luistureo.voicereminderapp.domain.notes.usecase.DeleteQuickNoteUseCase
import com.luistureo.voicereminderapp.domain.notes.usecase.GetQuickNoteUseCase
import com.luistureo.voicereminderapp.domain.notes.usecase.ObserveQuickNotesUseCase
import com.luistureo.voicereminderapp.domain.notes.usecase.RestoreDeletedQuickNoteUseCase
import com.luistureo.voicereminderapp.domain.notes.usecase.SaveQuickNoteUseCase
import com.luistureo.voicereminderapp.domain.notes.usecase.SetQuickNoteArchivedUseCase
import com.luistureo.voicereminderapp.domain.notes.usecase.SetQuickNotePinnedUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickNoteRepositoryImplTest {
    @Test
    fun createsTitleOnlyNote() = runBlocking {
        val fixture = Fixture()

        val saved = fixture.save(QuickNoteDraft(title = "  Pendiente  ", content = " \n "))

        assertEquals("Pendiente", saved?.title)
        assertEquals("", saved?.content)
        assertEquals(1, fixture.dao.size)
    }

    @Test
    fun createsContentOnlyNote() = runBlocking {
        val fixture = Fixture()

        val saved = fixture.save(
            QuickNoteDraft(title = "  ", content = "  Texto sin título  ")
        )

        assertNull(saved?.title)
        assertEquals("Texto sin título", saved?.content)
        assertEquals(1, fixture.dao.size)
    }

    @Test
    fun rejectsEmptyNoteWithoutWritingIt() = runBlocking {
        val fixture = Fixture()

        val saved = fixture.save(QuickNoteDraft(title = " \t ", content = " \n "))

        assertNull(saved)
        assertEquals(0, fixture.dao.size)
    }

    @Test
    fun editsExistingNotePreservingIdentityAndCreationTimestamp() = runBlocking {
        val fixture = Fixture(now = 1_000L)
        val created = requireNotNull(
            fixture.save(QuickNoteDraft(title = "Inicial", content = "Texto inicial"))
        )
        fixture.now = 2_000L

        val edited = fixture.save(
            QuickNoteDraft(
                id = created.id,
                title = "  Editada  ",
                content = "  Línea uno\nLínea dos  ",
                isPinned = true,
                colorTag = QuickNoteColorTag.BLUE
            )
        )

        assertEquals(created.id, edited?.id)
        assertEquals(created.createdAt, edited?.createdAt)
        assertEquals(2_000L, edited?.updatedAt)
        assertEquals("Editada", edited?.title)
        assertEquals("Línea uno\nLínea dos", edited?.content)
        assertEquals(QuickNoteColorTag.BLUE, edited?.colorTag)
        assertTrue(edited?.isPinned == true)
        assertEquals(1, fixture.dao.size)
    }

    @Test
    fun searchIsCaseInsensitiveAndTreatsLikeWildcardsAsLiterals() = runBlocking {
        val fixture = Fixture()
        val matching = requireNotNull(
            fixture.save(
                QuickNoteDraft(
                    title = "IDEA LOCAL",
                    content = "Ruta 50%_final\\borrador"
                )
            )
        )
        fixture.now += 100L
        fixture.save(QuickNoteDraft(title = "Idea distinta", content = "Ruta 500 final"))

        val byTitle = fixture.observe(QuickNoteFilter.ALL, "idea local")
        val byLiteralWildcards = fixture.observe(QuickNoteFilter.ALL, "50%_")
        val byLiteralSlash = fixture.observe(QuickNoteFilter.ALL, "final\\borrador")

        assertEquals(listOf(matching.id), byTitle.map { it.id })
        assertEquals(listOf(matching.id), byLiteralWildcards.map { it.id })
        assertEquals(listOf(matching.id), byLiteralSlash.map { it.id })
        assertEquals("%final\\\\borrador%", fixture.dao.lastSearchPattern)
    }

    @Test
    fun pinAndUnpinUpdateOrderingWithPinnedNotesFirst() = runBlocking {
        val fixture = Fixture(now = 100L)
        val oldest = requireNotNull(fixture.save(QuickNoteDraft(title = "Antigua")))
        fixture.now = 200L
        val middle = requireNotNull(fixture.save(QuickNoteDraft(title = "Intermedia")))
        fixture.now = 300L
        assertTrue(fixture.pin(oldest.id, true))
        fixture.now = 400L
        val newest = requireNotNull(fixture.save(QuickNoteDraft(title = "Nueva")))

        assertEquals(
            listOf(oldest.id, newest.id, middle.id),
            fixture.observe(QuickNoteFilter.ALL).map { it.id }
        )

        fixture.now = 500L
        assertTrue(fixture.pin(oldest.id, false))
        assertFalse(requireNotNull(fixture.get(oldest.id)).isPinned)
        assertEquals(
            listOf(oldest.id, newest.id, middle.id),
            fixture.observe(QuickNoteFilter.ALL).map { it.id }
        )
    }

    @Test
    fun filtersSeparateActivePinnedAndArchivedNotes() = runBlocking {
        val fixture = Fixture(now = 100L)
        val active = requireNotNull(fixture.save(QuickNoteDraft(title = "Activa")))
        fixture.now = 200L
        val pinned = requireNotNull(
            fixture.save(QuickNoteDraft(title = "Fijada", isPinned = true))
        )
        fixture.now = 300L
        val archived = requireNotNull(
            fixture.save(
                QuickNoteDraft(title = "Archivada", isPinned = true, isArchived = true)
            )
        )

        assertEquals(setOf(active.id, pinned.id), fixture.observe(QuickNoteFilter.ALL).map { it.id }.toSet())
        assertEquals(listOf(pinned.id), fixture.observe(QuickNoteFilter.PINNED).map { it.id })
        assertEquals(listOf(archived.id), fixture.observe(QuickNoteFilter.ARCHIVED).map { it.id })
    }

    @Test
    fun archivesAndRestoresNoteBetweenFilters() = runBlocking {
        val fixture = Fixture()
        val note = requireNotNull(fixture.save(QuickNoteDraft(title = "Temporal")))

        fixture.now += 100L
        assertTrue(fixture.archive(note.id, true))
        assertTrue(fixture.observe(QuickNoteFilter.ALL).isEmpty())
        assertEquals(listOf(note.id), fixture.observe(QuickNoteFilter.ARCHIVED).map { it.id })

        fixture.now += 100L
        assertTrue(fixture.archive(note.id, false))
        assertEquals(listOf(note.id), fixture.observe(QuickNoteFilter.ALL).map { it.id })
        assertTrue(fixture.observe(QuickNoteFilter.ARCHIVED).isEmpty())
    }

    @Test
    fun deletesAndRestoresExactSnapshotForUndo() = runBlocking {
        val fixture = Fixture()
        val saved = requireNotNull(
            fixture.save(
                QuickNoteDraft(
                    title = "Recuperable",
                    content = "Contenido local",
                    isPinned = true,
                    colorTag = QuickNoteColorTag.GREEN
                )
            )
        )

        val deleted = fixture.delete(saved.id)
        assertEquals(saved, deleted)
        assertNull(fixture.get(saved.id))
        assertTrue(fixture.restore(requireNotNull(deleted)))
        assertEquals(saved, fixture.get(saved.id))
        assertFalse(fixture.restore(saved))
    }

    @Test
    fun missingRowsDoNotReportSuccessfulMutations() = runBlocking {
        val fixture = Fixture()

        assertFalse(fixture.pin(999, true))
        assertFalse(fixture.archive(999, true))
        assertNull(fixture.delete(999))
        assertNull(fixture.get(999))
    }

    private class Fixture(now: Long = 1_000L) {
        val dao = FakeQuickNoteDao()
        var now: Long = now
        private val repository = QuickNoteRepositoryImpl(dao) { this.now }
        private val observe = ObserveQuickNotesUseCase(repository)
        private val get = GetQuickNoteUseCase(repository)
        private val save = SaveQuickNoteUseCase(repository)
        private val pin = SetQuickNotePinnedUseCase(repository)
        private val archive = SetQuickNoteArchivedUseCase(repository)
        private val delete = DeleteQuickNoteUseCase(repository)
        private val restore = RestoreDeletedQuickNoteUseCase(repository)

        suspend fun save(draft: QuickNoteDraft) = save.invoke(draft)
        suspend fun get(noteId: Int) = get.invoke(noteId)
        suspend fun pin(noteId: Int, pinned: Boolean) = pin.invoke(noteId, pinned)
        suspend fun archive(noteId: Int, archived: Boolean) = archive.invoke(noteId, archived)
        suspend fun delete(noteId: Int) = delete.invoke(noteId)
        suspend fun restore(note: com.luistureo.voicereminderapp.domain.notes.model.QuickNote) =
            restore.invoke(note)

        suspend fun observe(
            filter: QuickNoteFilter,
            query: String = ""
        ) = observe.invoke(filter, query).first()
    }
}
