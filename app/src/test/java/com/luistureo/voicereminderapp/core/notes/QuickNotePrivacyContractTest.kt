package com.luistureo.voicereminderapp.core.notes

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickNotePrivacyContractTest {
    @Test
    fun persistenceUsesOnlyTheLocalRoomDatabase() {
        val provider = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/data/repository/notes/" +
                "QuickNoteRepositoryProvider.kt"
        ).readText()
        val database = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/data/local/database/ReminderDatabase.kt"
        ).readText()

        assertTrue(provider.contains("ReminderDatabase.getDatabase(context.applicationContext)"))
        assertTrue(provider.contains(".quickNoteDao()"))
        assertTrue(database.contains("QuickNoteEntity::class"))
        assertTrue(database.contains("abstract fun quickNoteDao(): QuickNoteDao"))
    }

    @Test
    fun noteModuleHasNoCloudCalendarAiOrSensitiveLoggingIntegration() {
        val sources = noteSourceText()
        val forbidden = listOf(
            "core.calendar",
            "GoogleCalendar",
            "MicrosoftCalendar",
            "UnifiedCalendar",
            "OkHttpClient",
            "Retrofit",
            "HttpURLConnection",
            "http://",
            "https://",
            "Firebase",
            "RemoteAssistantTtsClient",
            "ChatAssistantService",
            "SpeechRecognizer",
            "Log.d(",
            "Log.i(",
            "Log.e(",
            "Timber."
        )

        forbidden.forEach { token ->
            assertFalse("Integración prohibida en Notas rápidas: $token", sources.contains(token))
        }
    }

    @Test
    fun sharingIsExplicitAndUsesTheSystemChooser() {
        val editor = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/notes/" +
                "QuickNoteEditorActivity.kt"
        ).readText()

        assertTrue(editor.contains("Intent(Intent.ACTION_SEND)"))
        assertTrue(editor.contains("Intent.createChooser"))
        assertTrue(editor.contains("putExtra(Intent.EXTRA_TEXT, sharedText)"))
        assertFalse(editor.contains("sendBroadcast(shareIntent)"))
        assertFalse(editor.contains("startService(shareIntent)"))
    }

    @Test
    fun daoSearchEscapesPatternsAndKeepsStablePrivateOrdering() {
        val dao = projectFile(
            "app/src/main/java/com/luistureo/voicereminderapp/data/local/dao/notes/QuickNoteDao.kt"
        ).readText()

        assertTrue(dao.contains("title LIKE :searchPattern ESCAPE '\\' COLLATE NOCASE"))
        assertTrue(dao.contains("content LIKE :searchPattern ESCAPE '\\' COLLATE NOCASE"))
        assertTrue(dao.contains("ORDER BY isPinned DESC, updatedAt DESC, id DESC"))
    }

    private fun noteSourceText(): String {
        val roots = listOf(
            "app/src/main/java/com/luistureo/voicereminderapp/core/notes",
            "app/src/main/java/com/luistureo/voicereminderapp/domain/notes",
            "app/src/main/java/com/luistureo/voicereminderapp/presentation/notes",
            "app/src/main/java/com/luistureo/voicereminderapp/data/local/dao/notes",
            "app/src/main/java/com/luistureo/voicereminderapp/data/local/entity/notes",
            "app/src/main/java/com/luistureo/voicereminderapp/data/mapper/notes",
            "app/src/main/java/com/luistureo/voicereminderapp/data/repository/notes"
        )
        return roots
            .flatMap { path ->
                projectFile(path).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
            }
            .joinToString("\n") { it.readText() }
    }

    private fun projectFile(path: String): File = File(path).takeIf(File::exists)
        ?: File(path.removePrefix("app/"))
}
