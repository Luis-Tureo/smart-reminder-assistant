package com.luistureo.voicereminderapp.data.local.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.luistureo.voicereminderapp.core.notes.QuickNoteSearchPattern
import com.luistureo.voicereminderapp.data.local.dao.notes.QuickNoteDao
import com.luistureo.voicereminderapp.data.local.entity.notes.QuickNoteEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickNoteDaoInstrumentedTest {
    private lateinit var database: ReminderDatabase
    private lateinit var dao: QuickNoteDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, ReminderDatabase::class.java).build()
        dao = database.quickNoteDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun searchIsCaseInsensitiveAndTreatsWildcardsAsLiteralText() = runBlocking {
        insert(title = "Plan 100% REAL", updatedAt = 30L)
        insert(title = "Plan 100x real", updatedAt = 20L)
        insert(title = "clave_uno", updatedAt = 10L)
        insert(title = "claveXuno", updatedAt = 5L)

        val percentMatches = dao.observeNotes(
            archived = false,
            pinnedOnly = false,
            searchPattern = QuickNoteSearchPattern.contains("100% real")
        ).first()
        val underscoreMatches = dao.observeNotes(
            archived = false,
            pinnedOnly = false,
            searchPattern = QuickNoteSearchPattern.contains("clave_")
        ).first()

        assertEquals(listOf("Plan 100% REAL"), percentMatches.map { it.title })
        assertEquals(listOf("clave_uno"), underscoreMatches.map { it.title })
    }

    @Test
    fun filtersAndOrderingAreAppliedByTheDao() = runBlocking {
        insert(title = "Reciente", pinned = false, updatedAt = 400L)
        insert(title = "Fijada antigua", pinned = true, updatedAt = 100L)
        insert(title = "Fijada reciente", pinned = true, updatedAt = 200L)
        insert(title = "Archivada", pinned = true, archived = true, updatedAt = 500L)

        val active = dao.observeNotes(false, false, QuickNoteSearchPattern.contains("")).first()
        val pinned = dao.observeNotes(false, true, QuickNoteSearchPattern.contains("")).first()
        val archived = dao.observeNotes(true, false, QuickNoteSearchPattern.contains("")).first()

        assertEquals(
            listOf("Fijada reciente", "Fijada antigua", "Reciente"),
            active.map { it.title }
        )
        assertEquals(listOf("Fijada reciente", "Fijada antigua"), pinned.map { it.title })
        assertEquals(listOf("Archivada"), archived.map { it.title })
    }

    private suspend fun insert(
        title: String,
        pinned: Boolean = false,
        archived: Boolean = false,
        updatedAt: Long
    ) {
        dao.insert(
            QuickNoteEntity(
                title = title,
                content = "Contenido",
                isPinned = pinned,
                colorTag = null,
                createdAt = updatedAt,
                updatedAt = updatedAt,
                isArchived = archived
            )
        )
    }
}
