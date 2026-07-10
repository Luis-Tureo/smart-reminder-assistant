package com.luistureo.voicereminderapp.presentation.routine

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.Toast
import android.widget.Spinner
import android.widget.ArrayAdapter
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.routine.RoutinePreferenceStore
import com.luistureo.voicereminderapp.domain.routine.model.Routine
import com.luistureo.voicereminderapp.domain.routine.model.RoutineAssistantMode
import com.luistureo.voicereminderapp.domain.routine.model.RoutineChartType
import com.luistureo.voicereminderapp.domain.routine.model.RoutineStreakSettings
import com.luistureo.voicereminderapp.domain.routine.model.RoutineSuggestionSettings
import com.luistureo.voicereminderapp.presentation.routine.viewmodel.RoutineViewModel
import com.luistureo.voicereminderapp.presentation.routine.viewmodel.RoutineViewModelFactory
import java.time.LocalTime
import java.util.Locale
import kotlinx.coroutines.launch

class RoutineSettingsActivity : ComponentActivity() {
    private lateinit var viewModel: RoutineViewModel
    private lateinit var enabledSwitch: MaterialSwitch
    private lateinit var assistantGroup: RadioGroup
    private lateinit var bubblesCheck: MaterialCheckBox
    private lateinit var voiceCheck: MaterialCheckBox
    private lateinit var scheduleButton: MaterialButton
    private lateinit var preferredNameInput: TextInputEditText
    private lateinit var preferenceStore: RoutinePreferenceStore
    private lateinit var chartSpinner: Spinner
    private lateinit var partialStreakCheck: MaterialCheckBox
    private lateinit var partialThresholdInput: TextInputEditText
    private lateinit var suggestionsCheck: MaterialCheckBox
    private lateinit var suggestionBubbleCheck: MaterialCheckBox
    private lateinit var suggestionVoiceCheck: MaterialCheckBox
    private lateinit var suggestionTimeButton: MaterialButton
    private lateinit var showBuiltInsCheck: MaterialCheckBox
    private lateinit var showPersonalCheck: MaterialCheckBox
    private lateinit var completedMessagesCheck: MaterialCheckBox
    private lateinit var partialMessagesCheck: MaterialCheckBox
    private lateinit var missedMessagesCheck: MaterialCheckBox
    private var suggestionTime = LocalTime.of(18, 0)
    private var motivationSchedule: LocalTime? = null
    private var routine: Routine? = null
    private var initialized = false
    private val routineId by lazy { intent.getIntExtra(EXTRA_ROUTINE_ID, 0) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (routineId == 0) {
            finish()
            return
        }
        setContentView(R.layout.activity_routine_settings)
        viewModel = ViewModelProvider(
            this,
            RoutineViewModelFactory(applicationContext)
        )[RoutineViewModel::class.java]
        preferenceStore = RoutinePreferenceStore(applicationContext)
        setupViews()
        observeState()
        viewModel.loadRoutine(routineId)
    }

    private fun setupViews() {
        findViewById<ImageButton>(R.id.btnBackRoutineSettings).setOnClickListener { finish() }
        enabledSwitch = findViewById(R.id.switchRoutineEnabled)
        assistantGroup = findViewById(R.id.groupRoutineAssistantMode)
        bubblesCheck = findViewById(R.id.checkRoutineMotivationBubbles)
        voiceCheck = findViewById(R.id.checkRoutineAssistantVoice)
        scheduleButton = findViewById(R.id.btnRoutineMotivationSchedule)
        preferredNameInput = findViewById(R.id.inputRoutinePreferredName)
        preferredNameInput.setText(preferenceStore.getPreferredName().orEmpty())
        chartSpinner = findViewById(R.id.spinnerRoutinePreferredChart)
        chartSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf("Barras", "Circular", "Calendario", "Lista porcentual"))
        chartSpinner.setSelection(preferenceStore.getChartType().ordinal)
        partialStreakCheck = findViewById(R.id.checkRoutinePartialStreak)
        partialThresholdInput = findViewById(R.id.inputRoutinePartialThreshold)
        preferenceStore.getStreakSettings().let {
            partialStreakCheck.isChecked = it.countPartialDays
            partialThresholdInput.setText(
                String.format(Locale.getDefault(), "%d", it.partialThresholdPercentage)
            )
        }
        suggestionsCheck = findViewById(R.id.checkRoutineSuggestionsEnabled)
        suggestionBubbleCheck = findViewById(R.id.checkRoutineSuggestionBubble)
        suggestionVoiceCheck = findViewById(R.id.checkRoutineSuggestionVoice)
        suggestionTimeButton = findViewById(R.id.btnRoutineSuggestionTime)
        preferenceStore.getSuggestionSettings().let {
            suggestionsCheck.isChecked = it.enabled
            suggestionBubbleCheck.isChecked = it.showBubble
            suggestionVoiceCheck.isChecked = it.speak
            suggestionTime = LocalTime.of(it.preferredHour, it.preferredMinute)
        }
        suggestionTimeButton.text = RoutineUiFormatter.time(suggestionTime)
        suggestionTimeButton.setOnClickListener {
            TimePickerDialog(this, { _, hour, minute ->
                suggestionTime = LocalTime.of(hour, minute)
                suggestionTimeButton.text = RoutineUiFormatter.time(suggestionTime)
            }, suggestionTime.hour, suggestionTime.minute, true).show()
        }
        showBuiltInsCheck = findViewById(R.id.checkRoutineShowBuiltIns)
        showPersonalCheck = findViewById(R.id.checkRoutineShowPersonal)
        showBuiltInsCheck.isChecked = preferenceStore.showBuiltInTemplates()
        showPersonalCheck.isChecked = preferenceStore.showPersonalTemplates()
        completedMessagesCheck = findViewById(R.id.checkRoutineCompletedMessages)
        partialMessagesCheck = findViewById(R.id.checkRoutinePartialMessages)
        missedMessagesCheck = findViewById(R.id.checkRoutineMissedMessages)
        completedMessagesCheck.isChecked = preferenceStore.completionMessagesEnabled()
        partialMessagesCheck.isChecked = preferenceStore.partialMessagesEnabled()
        missedMessagesCheck.isChecked = preferenceStore.missedMessagesEnabled()
        scheduleButton.setOnClickListener { selectSchedule() }
        findViewById<MaterialButton>(R.id.btnSaveRoutineSettings).setOnClickListener {
            saveSettings()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val selected = state.selectedRoutine
                    if (!initialized && selected != null) {
                        initialized = true
                        routine = selected
                        enabledSwitch.isChecked = selected.enabled
                        bubblesCheck.isChecked = selected.motivationBubbleEnabled
                        voiceCheck.isChecked = selected.voiceEnabled
                        assistantGroup.check(
                            when (selected.assistantMode) {
                                RoutineAssistantMode.SIMPLE_DISPLAY -> R.id.radioRoutineAssistantDisplay
                                RoutineAssistantMode.STEP_BY_STEP_GUIDE -> R.id.radioRoutineAssistantStep
                                RoutineAssistantMode.SMART_GUIDE -> R.id.radioRoutineAssistantSmart
                            }
                        )
                        motivationSchedule = selected.motivationSchedule
                        updateScheduleButton()
                    }
                    state.message?.let { message ->
                        Toast.makeText(this@RoutineSettingsActivity, message, Toast.LENGTH_SHORT).show()
                        viewModel.consumeMessage()
                    }
                }
            }
        }
    }

    private fun saveSettings() {
        val current = routine ?: return
        val updated = current.copy(
            enabled = enabledSwitch.isChecked,
            assistantMode = when (assistantGroup.checkedRadioButtonId) {
                R.id.radioRoutineAssistantStep -> RoutineAssistantMode.STEP_BY_STEP_GUIDE
                R.id.radioRoutineAssistantSmart -> RoutineAssistantMode.SMART_GUIDE
                else -> RoutineAssistantMode.SIMPLE_DISPLAY
            },
            motivationBubbleEnabled = bubblesCheck.isChecked,
            voiceEnabled = voiceCheck.isChecked,
            motivationSchedule = motivationSchedule,
            updatedAtEpochMillis = System.currentTimeMillis()
        )
        viewModel.saveRoutine(updated, viewModel.uiState.value.tasks) {
            preferenceStore.setPreferredName(preferredNameInput.text?.toString())
            preferenceStore.setChartType(RoutineChartType.entries[chartSpinner.selectedItemPosition])
            preferenceStore.setStreakSettings(RoutineStreakSettings(
                partialStreakCheck.isChecked,
                partialThresholdInput.text?.toString()?.toIntOrNull()?.coerceIn(1, 100) ?: 80
            ))
            preferenceStore.setSuggestionSettings(RoutineSuggestionSettings(
                suggestionsCheck.isChecked, suggestionTime.hour, suggestionTime.minute,
                suggestionBubbleCheck.isChecked, suggestionVoiceCheck.isChecked
            ))
            preferenceStore.setTemplateVisibility(showBuiltInsCheck.isChecked, showPersonalCheck.isChecked)
            preferenceStore.setMotivationMessages(completedMessagesCheck.isChecked,
                partialMessagesCheck.isChecked, missedMessagesCheck.isChecked)
            Toast.makeText(this, R.string.routine_settings_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun selectSchedule() {
        val current = motivationSchedule
        if (current == null) {
            showTimePicker(LocalTime.now())
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.routine_message_schedule)
            .setItems(
                arrayOf(
                    getString(R.string.routine_change_time),
                    getString(R.string.routine_clear_time)
                )
            ) { _, which ->
                if (which == 0) showTimePicker(current) else {
                    motivationSchedule = null
                    updateScheduleButton()
                }
            }
            .show()
    }

    private fun showTimePicker(initial: LocalTime) {
        TimePickerDialog(
            this,
            { _, hour, minute ->
                motivationSchedule = LocalTime.of(hour, minute)
                updateScheduleButton()
            },
            initial.hour,
            initial.minute,
            true
        ).show()
    }

    private fun updateScheduleButton() {
        scheduleButton.text = motivationSchedule?.let(RoutineUiFormatter::time)
            ?: getString(R.string.routine_no_message_schedule)
    }

    companion object {
        const val EXTRA_ROUTINE_ID = "extra_routine_id"
    }
}
