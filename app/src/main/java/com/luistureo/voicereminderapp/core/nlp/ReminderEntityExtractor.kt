package com.luistureo.voicereminderapp.core.nlp

import android.content.Context
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityAnnotation
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractor
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import kotlinx.coroutines.tasks.await
import java.util.TimeZone

class ReminderEntityExtractor(
    @Suppress("UNUSED_PARAMETER") context: Context
) {

    private val extractor: EntityExtractor by lazy {
        val options = EntityExtractorOptions.Builder(
            EntityExtractorOptions.SPANISH
        ).build()

        EntityExtraction.getClient(options)
    }

    suspend fun prepare() {
        extractor.downloadModelIfNeeded().await()
    }

    suspend fun extractDateTimeEntities(
        text: String,
        referenceTimeMillis: Long
    ): List<EntityAnnotation> {
        val params = EntityExtractionParams.Builder(text)
            .setReferenceTime(referenceTimeMillis)
            .setReferenceTimeZone(TimeZone.getDefault())
            .build()

        val annotations: List<EntityAnnotation> = extractor.annotate(params).await()

        return annotations.filter { annotation ->
            annotation.getEntities().any { entity ->
                entity.getType() == Entity.TYPE_DATE_TIME
            }
        }
    }

    fun close() {
        extractor.close()
    }
}