package com.luistureo.voicereminderapp.core.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

class LocalImageTextRecognizer(
    private val context: Context
) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognizeFromBitmap(bitmap: Bitmap): String {
        return recognize(InputImage.fromBitmap(bitmap, 0))
    }

    suspend fun recognizeFromUri(uri: Uri): String {
        return recognize(InputImage.fromFilePath(context, uri))
    }

    fun close() {
        recognizer.close()
    }

    private suspend fun recognize(image: InputImage): String {
        return recognizer.process(image).await().text
    }
}
