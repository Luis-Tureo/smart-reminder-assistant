package com.luistureo.voicereminderapp.presentation.camera

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import com.luistureo.voicereminderapp.R

class CameraReminderConfirmationActivity : ComponentActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var continueButton: MaterialButton
    private lateinit var editButton: MaterialButton
    private lateinit var cancelButton: MaterialButton
    private lateinit var summaryText: TextView
    private lateinit var detectedText: TextView
    private lateinit var scheduleText: TextView
    private lateinit var previewImage: ImageView

    private lateinit var payload: CameraReminderDraftPayload

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_reminder_confirmation)

        payload = CameraReminderDraftContract.readPayload(intent)
        initViews()
        bindContent()
        setupListeners()
    }

    private fun initViews() {
        backButton = findViewById(R.id.btnBackCameraConfirmation)
        continueButton = findViewById(R.id.btnCameraConfirmationContinue)
        editButton = findViewById(R.id.btnCameraConfirmationEdit)
        cancelButton = findViewById(R.id.btnCameraConfirmationCancel)
        summaryText = findViewById(R.id.tvCameraConfirmationSummary)
        detectedText = findViewById(R.id.tvCameraConfirmationText)
        scheduleText = findViewById(R.id.tvCameraConfirmationSchedule)
        previewImage = findViewById(R.id.imageCameraConfirmationPreview)
    }

    private fun bindContent() {
        summaryText.text = buildSummaryText()
        detectedText.text = buildDetectedText()
        scheduleText.text = buildScheduleText()

        payload.imageUri?.let { uriString ->
            runCatching { Uri.parse(uriString) }.getOrNull()?.let { uri ->
                previewImage.setImageURI(uri)
                previewImage.isVisible = true
            }
        }
    }

    private fun setupListeners() {
        backButton.setOnClickListener { finishCanceled() }
        cancelButton.setOnClickListener { finishCanceled() }

        continueButton.setOnClickListener {
            finishWithAction(CameraReminderDraftContract.ACTION_CONFIRM)
        }

        editButton.setOnClickListener {
            finishWithAction(CameraReminderDraftContract.ACTION_EDIT)
        }
    }

    private fun finishCanceled() {
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun finishWithAction(action: String) {
        val resultIntent = Intent().apply {
            putExtras(
                CameraReminderDraftContract.createIntent(
                    title = payload.title,
                    text = payload.text,
                    date = payload.date,
                    time = payload.time,
                    recognizedText = payload.recognizedText,
                    imageUri = payload.imageUri?.let(Uri::parse),
                    hasDetectedDate = payload.hasDetectedDate,
                    hasDetectedTime = payload.hasDetectedTime
                ).extras ?: Bundle.EMPTY
            )
            putExtra(CameraReminderDraftContract.EXTRA_ACTION, action)
        }

        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun buildSummaryText(): String {
        val title = payload.title?.takeIf { it.isNotBlank() }
            ?: payload.text?.substringBefore(".")
            ?: getString(R.string.camera_confirmation_no_summary)
        val dateLabel = payload.date
        val timeLabel = payload.time
        val scheduleSummary = when {
            !dateLabel.isNullOrBlank() && !timeLabel.isNullOrBlank() -> {
                getString(R.string.camera_confirmation_summary_complete, dateLabel, timeLabel)
            }

            !dateLabel.isNullOrBlank() -> {
                getString(R.string.camera_confirmation_summary_date_only, dateLabel)
            }

            !timeLabel.isNullOrBlank() -> {
                getString(R.string.camera_confirmation_summary_time_only, timeLabel)
            }

            else -> getString(R.string.camera_confirmation_summary_missing_schedule)
        }

        return getString(R.string.camera_confirmation_summary_message, title, scheduleSummary)
    }

    private fun buildDetectedText(): String {
        val recognizedText = payload.recognizedText.trim()
        return if (recognizedText.isBlank()) {
            getString(R.string.camera_confirmation_no_text_detected)
        } else {
            recognizedText
        }
    }

    private fun buildScheduleText(): String {
        return when {
            !payload.hasDetectedDate && !payload.hasDetectedTime ->
                getString(R.string.camera_confirmation_missing_date_time)

            !payload.hasDetectedDate ->
                getString(R.string.camera_confirmation_missing_date)

            !payload.hasDetectedTime ->
                getString(R.string.camera_confirmation_missing_time)

            else ->
                getString(R.string.camera_confirmation_detected_date_time, payload.date, payload.time)
        }
    }
}
