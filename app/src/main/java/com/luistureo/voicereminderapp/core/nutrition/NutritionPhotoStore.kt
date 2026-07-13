package com.luistureo.voicereminderapp.core.nutrition

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

object NutritionPhotoStore {
    private const val DIRECTORY_NAME = "nutrition_photos"
    private const val FILE_PREFIX = "nutrition_meal_"
    private const val MAX_BYTES = 15L * 1024L * 1024L

    fun importToLocalStorage(context: Context, source: Uri): Uri {
        require(source.scheme == "content" || source.scheme == "file")
        val resolver = context.contentResolver
        val extension = resolver.getType(source)
            ?.let(MimeTypeMap.getSingleton()::getExtensionFromMimeType)
            ?.takeIf { it.matches("[A-Za-z0-9]{1,5}".toRegex()) }
            ?: "jpg"
        val directory = File(context.filesDir, DIRECTORY_NAME).apply { mkdirs() }
        val target = File(directory, "$FILE_PREFIX${UUID.randomUUID()}.$extension")
        try {
            resolver.openInputStream(source).use { input ->
                requireNotNull(input) { "No fue posible abrir la foto seleccionada." }
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_BYTES) { "La foto seleccionada es demasiado grande." }
                        output.write(buffer, 0, read)
                    }
                }
            }
            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                target
            )
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }

    fun deleteIfManaged(context: Context, uri: Uri?) {
        val fileName = uri?.lastPathSegment?.substringAfterLast('/') ?: return
        if (!fileName.startsWith(FILE_PREFIX)) return
        val directory = File(context.filesDir, DIRECTORY_NAME).canonicalFile
        val target = File(directory, fileName).canonicalFile
        if (target.parentFile == directory) target.delete()
    }
}
