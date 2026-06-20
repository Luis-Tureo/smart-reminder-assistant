package com.luistureo.voicereminderapp.presentation.manual

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.alarm.ReminderScheduler
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarReminderSynchronizer
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarSyncLogger
import com.luistureo.voicereminderapp.core.calendar.unified.UnifiedCalendarSynchronizer
import com.luistureo.voicereminderapp.core.nlp.PastedReminderDateOrigin
import com.luistureo.voicereminderapp.core.nlp.PastedReminderParseResult
import com.luistureo.voicereminderapp.core.nlp.PastedReminderTextParser
import com.luistureo.voicereminderapp.core.nlp.ReminderContentCleaner
import com.luistureo.voicereminderapp.core.nlp.ReminderTextParserLogger
import com.luistureo.voicereminderapp.core.utils.DateTimeFormatter
import com.luistureo.voicereminderapp.data.local.database.ReminderDatabase
import com.luistureo.voicereminderapp.data.repository.ReminderRepositoryImpl
import com.luistureo.voicereminderapp.domain.model.ReminderDraft
import com.luistureo.voicereminderapp.domain.model.ReminderRecurrence
import com.luistureo.voicereminderapp.domain.model.ReminderRecurrenceUnit
import com.luistureo.voicereminderapp.domain.model.ReminderSource
import com.luistureo.voicereminderapp.domain.usecase.SaveReminderDraftUseCase
import java.time.LocalDate
import java.time.LocalTime
import java.util.Calendar
import kotlinx.coroutines.launch

class PasteTextReminderActivity : ComponentActivity() {
    private lateinit var pastedTextInput: TextInputEditText
    private lateinit var titleInput: TextInputEditText
    private lateinit var detailInput: TextInputEditText
    private lateinit var editorCard: View
    private lateinit var confirmationCard: View
    private lateinit var dateButton: MaterialButton
    private lateinit var timeButton: MaterialButton
    private lateinit var dateError: TextView
    private lateinit var timeError: TextView
    private lateinit var dateNotice: TextView
    private lateinit var urgentSwitch: SwitchMaterial
    private lateinit var recurrenceSpinner: Spinner
    private lateinit var confirmationSummary: TextView
    private lateinit var saveButton: MaterialButton

    private lateinit var saveReminderDraftUseCase: SaveReminderDraftUseCase
    private lateinit var unifiedCalendarSynchronizer: UnifiedCalendarSynchronizer
    private lateinit var reminderScheduler: ReminderScheduler

    private var selectedCalendarDay: LocalDate? = null
    private var selectedDate: LocalDate? = null
    private var selectedTime: LocalTime? = null
    private var parsedDateOrigin: PastedReminderDateOrigin = PastedReminderDateOrigin.MISSING
    private var isSaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_paste_text_reminder)
        initDependencies()
        initViews()
        setupRecurrenceSpinner()
        setupListeners()
        selectedCalendarDay = intent.getStringExtra(EXTRA_SELECTED_DATE)
            ?.let(DateTimeFormatter::parseDate)
        onBackPressedDispatcher.addCallback(this) { cancelAndFinish() }
    }

    private fun initDependencies() {
        val repository = ReminderRepositoryImpl(
            ReminderDatabase.getDatabase(this).reminderDao()
        )
        val googleSynchronizer = GoogleCalendarReminderSynchronizer(
            applicationContext,
            repository
        )
        saveReminderDraftUseCase = SaveReminderDraftUseCase(repository)
        unifiedCalendarSynchronizer = UnifiedCalendarSynchronizer(
            applicationContext,
            repository,
            googleSynchronizer
        )
        reminderScheduler = ReminderScheduler(applicationContext)
    }

    private fun initViews() {
        pastedTextInput = findViewById(R.id.inputPastedReminderText)
        titleInput = findViewById(R.id.inputPasteReminderTitle)
        detailInput = findViewById(R.id.inputPasteReminderDetail)
        editorCard = findViewById(R.id.cardPasteReminderEditor)
        confirmationCard = findViewById(R.id.cardPasteReminderConfirmation)
        dateButton = findViewById(R.id.btnPasteReminderDate)
        timeButton = findViewById(R.id.btnPasteReminderTime)
        dateError = findViewById(R.id.tvPasteReminderDateError)
        timeError = findViewById(R.id.tvPasteReminderTimeError)
        dateNotice = findViewById(R.id.tvPasteReminderDateNotice)
        urgentSwitch = findViewById(R.id.switchPasteReminderUrgent)
        recurrenceSpinner = findViewById(R.id.spinnerPasteReminderRecurrence)
        confirmationSummary = findViewById(R.id.tvPasteReminderConfirmationSummary)
        saveButton = findViewById(R.id.btnSavePastedReminder)
    }

    private fun setupRecurrenceSpinner() {
        recurrenceSpinner.adapter = ArrayAdapter.createFromResource(
            this,
            R.array.paste_reminder_recurrence_options,
            android.R.layout.simple_spinner_dropdown_item
        )
    }

    private fun setupListeners() {
        findViewById<ImageButton>(R.id.btnBackPasteText).setOnClickListener {
            cancelAndFinish()
        }
        findViewById<MaterialButton>(R.id.btnAnalyzePastedReminder).setOnClickListener {
            analyzeText()
        }
        dateButton.setOnClickListener { showDatePicker() }
        timeButton.setOnClickListener { showTimePicker() }
        findViewById<MaterialButton>(R.id.btnReviewPastedReminder).setOnClickListener {
            showConfirmation()
        }
        saveButton.setOnClickListener { saveConfirmedReminder() }
        findViewById<MaterialButton>(R.id.btnEditPastedReminder).setOnClickListener {
            confirmationCard.isVisible = false
            editorCard.isVisible = true
        }
        findViewById<MaterialButton>(R.id.btnCancelPastedReminder).setOnClickListener {
            cancelAndFinish()
        }
    }

    private fun analyzeText() {
        val input = pastedTextInput.text?.toString()?.trim().orEmpty()
        if (input.isBlank()) {
            Toast.makeText(this, R.string.paste_reminder_text_required, Toast.LENGTH_SHORT).show()
            return
        }
        bindParsedResult(
            PastedReminderTextParser.parse(
                input = input,
                selectedCalendarDay = selectedCalendarDay
            )
        )
    }

    private fun bindParsedResult(result: PastedReminderParseResult) {
        titleInput.setText(result.title)
        detailInput.setText(result.detail)
        selectedDate = result.date
        selectedTime = result.time?.let { LocalTime.of(it.hour, it.minute) }
        parsedDateOrigin = result.dateOrigin
        urgentSwitch.isChecked = result.isUrgent
        recurrenceSpinner.setSelection(result.recurrence.toSpinnerIndex())
        dateError.isVisible = result.requiresDate
        timeError.isVisible = result.requiresTime
        updateScheduleButtons()
        updateDateNotice()
        confirmationCard.isVisible = false
        editorCard.isVisible = true
    }

    private fun updateScheduleButtons() {
        dateButton.text = selectedDate?.let {
            DateTimeFormatter.formatDate(it.dayOfMonth, it.monthValue, it.year)
        } ?: getString(R.string.reminder_select_date)
        timeButton.text = selectedTime?.let {
            DateTimeFormatter.formatTime(it.hour, it.minute)
        } ?: getString(R.string.reminder_select_time)
    }

    private fun updateDateNotice() {
        val text = when {
            parsedDateOrigin == PastedReminderDateOrigin.TEXT &&
                    selectedCalendarDay != null && selectedDate != selectedCalendarDay ->
                selectedDate?.let {
                    getString(
                        R.string.paste_reminder_text_date_override,
                        DateTimeFormatter.formatDate(it.dayOfMonth, it.monthValue, it.year)
                    )
                }
            parsedDateOrigin == PastedReminderDateOrigin.SELECTED_CALENDAR_DAY ->
                getString(R.string.paste_reminder_selected_day_default)
            else -> null
        }
        dateNotice.text = text.orEmpty()
        dateNotice.isVisible = !text.isNullOrBlank()
    }

    private fun showDatePicker() {
        val base = selectedDate ?: selectedCalendarDay ?: LocalDate.now()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                selectedDate = LocalDate.of(year, month + 1, day)
                parsedDateOrigin = PastedReminderDateOrigin.USER_SELECTED
                dateError.isVisible = false
                updateScheduleButtons()
                updateDateNotice()
            },
            base.year,
            base.monthValue - 1,
            base.dayOfMonth
        ).show()
    }

    private fun showTimePicker() {
        val base = selectedTime ?: LocalTime.now()
        TimePickerDialog(
            this,
            { _, hour, minute ->
                selectedTime = LocalTime.of(hour, minute)
                timeError.isVisible = false
                updateScheduleButtons()
            },
            base.hour,
            base.minute,
            true
        ).show()
    }

    private fun showConfirmation() {
        val draft = buildDraftOrShowErrors() ?: return
        confirmationSummary.text = buildConfirmationSummary(draft)
        editorCard.isVisible = false
        confirmationCard.isVisible = true
        ReminderTextParserLogger.confirmationShown()
        CalendarSyncLogger.ui("paste_text_confirmation_shown")
    }

    private fun saveConfirmedReminder() {
        if (!confirmationCard.isVisible || isSaving) return
        val draft = buildDraftOrShowErrors() ?: run {
            confirmationCard.isVisible = false
            editorCard.isVisible = true
            return
        }
        isSaving = true
        saveButton.isEnabled = false
        lifecycleScope.launch {
            runCatching {
                val savedReminder = saveReminderDraftUseCase(draft)
                val syncedReminder = unifiedCalendarSynchronizer.syncSavedReminder(savedReminder)
                reminderScheduler.syncReminderSchedule(syncedReminder)
            }.onSuccess {
                ReminderTextParserLogger.saved()
                CalendarSyncLogger.ui("paste_text_reminder_saved")
                Toast.makeText(
                    this@PasteTextReminderActivity,
                    R.string.paste_reminder_saved,
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }.onFailure {
                isSaving = false
                saveButton.isEnabled = true
                Toast.makeText(
                    this@PasteTextReminderActivity,
                    R.string.paste_reminder_save_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun buildDraftOrShowErrors(): ReminderDraft? {
        val detail = detailInput.text?.toString()?.trim().orEmpty()
        if (detail.isBlank()) {
            Toast.makeText(this, R.string.paste_reminder_detail_required, Toast.LENGTH_SHORT).show()
            return null
        }
        dateError.isVisible = selectedDate == null
        timeError.isVisible = selectedTime == null
        if (selectedDate == null || selectedTime == null) return null
        val title = titleInput.text?.toString()?.trim().orEmpty()
            .ifBlank { ReminderContentCleaner.buildTitle(detail).orEmpty() }
        val date = requireNotNull(selectedDate)
        val time = requireNotNull(selectedTime)
        return ReminderDraft(
            title = title,
            text = detail,
            date = DateTimeFormatter.formatDate(date.dayOfMonth, date.monthValue, date.year),
            time = DateTimeFormatter.formatTime(time.hour, time.minute),
            isUrgent = urgentSwitch.isChecked,
            source = ReminderSource.MANUAL,
            recurrence = recurrenceSpinner.selectedItemPosition.toRecurrence()
        )
    }

    private fun buildConfirmationSummary(draft: ReminderDraft): String {
        val recurrence = recurrenceSpinner.selectedItem?.toString().orEmpty()
        val urgency = if (draft.isUrgent) "Sí" else "No"
        return "Título: ${draft.title}\nDetalle: ${draft.text}\nFecha: ${draft.date}\n" +
                "Hora: ${draft.time}\nUrgente: $urgency\nRecurrencia: $recurrence"
    }

    private fun ReminderRecurrence?.toSpinnerIndex(): Int = when (this?.unit) {
        ReminderRecurrenceUnit.DAY -> 1
        ReminderRecurrenceUnit.WEEK -> 2
        ReminderRecurrenceUnit.MONTH -> 3
        ReminderRecurrenceUnit.YEAR -> 4
        null -> 0
    }

    private fun Int.toRecurrence(): ReminderRecurrence? = when (this) {
        1 -> ReminderRecurrence(ReminderRecurrenceUnit.DAY)
        2 -> ReminderRecurrence(ReminderRecurrenceUnit.WEEK)
        3 -> ReminderRecurrence(ReminderRecurrenceUnit.MONTH)
        4 -> ReminderRecurrence(ReminderRecurrenceUnit.YEAR)
        else -> null
    }

    private fun cancelAndFinish() {
        ReminderTextParserLogger.cancelled()
        CalendarSyncLogger.ui("paste_text_reminder_cancelled")
        finish()
    }

    companion object {
        const val EXTRA_SELECTED_DATE = "extra_selected_date"
    }
}
