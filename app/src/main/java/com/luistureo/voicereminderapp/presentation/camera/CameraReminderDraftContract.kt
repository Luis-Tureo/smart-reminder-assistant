package com.luistureo.voicereminderapp.presentation.camera

import android.content.Intent
import android.net.Uri
import com.luistureo.voicereminderapp.domain.model.ReminderDraft
import com.luistureo.voicereminderapp.domain.model.ReminderSource

data class CameraReminderDraftPayload(
    val title: String?,
    val text: String?,
    val date: String?,
    val time: String?,
    val recognizedText: String,
    val imageUri: String?,
    val hasDetectedDate: Boolean,
    val hasDetectedTime: Boolean
) {
    fun toDraft(): ReminderDraft {
        return ReminderDraft(
            title = title,
            text = text,
            date = date,
            time = time,
            source = ReminderSource.CAMERA
        )
    }
}

object CameraReminderDraftContract {
    const val EXTRA_TITLE = "extra_camera_draft_title"
    const val EXTRA_TEXT = "extra_camera_draft_text"
    const val EXTRA_DATE = "extra_camera_draft_date"
    const val EXTRA_TIME = "extra_camera_draft_time"
    const val EXTRA_RECOGNIZED_TEXT = "extra_camera_recognized_text"
    const val EXTRA_IMAGE_URI = "extra_camera_image_uri"
    const val EXTRA_HAS_DATE = "extra_camera_has_date"
    const val EXTRA_HAS_TIME = "extra_camera_has_time"
    const val EXTRA_ACTION = "extra_camera_action"
    const val ACTION_CONFIRM = "camera_action_confirm"
    const val ACTION_EDIT = "camera_action_edit"

    fun createIntent(
        title: String?,
        text: String?,
        date: String?,
        time: String?,
        recognizedText: String,
        imageUri: Uri?,
        hasDetectedDate: Boolean,
        hasDetectedTime: Boolean
    ): Intent {
        return Intent().apply {
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_TEXT, text)
            putExtra(EXTRA_DATE, date)
            putExtra(EXTRA_TIME, time)
            putExtra(EXTRA_RECOGNIZED_TEXT, recognizedText)
            putExtra(EXTRA_IMAGE_URI, imageUri?.toString())
            putExtra(EXTRA_HAS_DATE, hasDetectedDate)
            putExtra(EXTRA_HAS_TIME, hasDetectedTime)
        }
    }

    fun readPayload(intent: Intent): CameraReminderDraftPayload {
        return CameraReminderDraftPayload(
            title = intent.getStringExtra(EXTRA_TITLE),
            text = intent.getStringExtra(EXTRA_TEXT),
            date = intent.getStringExtra(EXTRA_DATE),
            time = intent.getStringExtra(EXTRA_TIME),
            recognizedText = intent.getStringExtra(EXTRA_RECOGNIZED_TEXT).orEmpty(),
            imageUri = intent.getStringExtra(EXTRA_IMAGE_URI),
            hasDetectedDate = intent.getBooleanExtra(EXTRA_HAS_DATE, false),
            hasDetectedTime = intent.getBooleanExtra(EXTRA_HAS_TIME, false)
        )
    }
}
