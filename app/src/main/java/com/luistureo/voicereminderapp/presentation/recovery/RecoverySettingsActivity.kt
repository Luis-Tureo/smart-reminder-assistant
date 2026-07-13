package com.luistureo.voicereminderapp.presentation.recovery

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.recovery.RecoveryNotificationTextMode
import com.luistureo.voicereminderapp.core.recovery.RecoveryPreferenceStore
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryDeletionMode
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryGoalStatus
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryMilestone
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryMilestoneKind
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryReminder
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryReminderType
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

class RecoverySettingsActivity : ComponentActivity() {
    private lateinit var viewModel: RecoveryViewModel
    private lateinit var preferences: RecoveryPreferenceStore
    private lateinit var reminderTypeSpinner: Spinner
    private var reminderTime = LocalTime.of(20, 0)
    private var initialized = false
    private val goalId by lazy { intent.getIntExtra(RecoveryDashboardActivity.EXTRA_GOAL_ID, 0) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (goalId <= 0) { finish(); return }
        setContentView(R.layout.activity_recovery_settings)
        preferences = RecoveryPreferenceStore(applicationContext)
        viewModel = ViewModelProvider(this, RecoveryViewModelFactory(applicationContext))[
            RecoveryViewModel::class.java
        ]
        setupViews()
        observe()
        viewModel.load(goalId)
    }

    private fun setupViews() {
        findViewById<ImageButton>(R.id.btnBackRecoverySettings).setOnClickListener { finish() }
        findViewById<RadioGroup>(R.id.groupRecoveryNotificationPrivacy).check(
            if (preferences.notificationTextMode() == RecoveryNotificationTextMode.DISCREET) {
                R.id.radioRecoveryDiscreet
            } else R.id.radioRecoveryFull
        )
        findViewById<MaterialSwitch>(R.id.switchRecoveryVoice).isChecked = preferences.voiceEnabled()
        findViewById<MaterialSwitch>(R.id.switchRecoveryBubble).isChecked = preferences.bubbleEnabled()
        findViewById<MaterialSwitch>(R.id.switchRecoveryMilestones).isChecked = preferences.milestonesEnabled()
        findViewById<MaterialSwitch>(R.id.switchRecoveryStatistics).isChecked = preferences.statisticsEnabled()
        reminderTypeSpinner = findViewById(R.id.spinnerRecoveryReminderType)
        reminderTypeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf(
                getString(R.string.recovery_reminder_daily),
                getString(R.string.recovery_reminder_motivation),
                getString(R.string.recovery_reminder_milestone),
                getString(R.string.recovery_reminder_high_risk),
                getString(R.string.recovery_reminder_support)
            )
        )
        reminderTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (initialized) populateReminder(RecoveryReminderType.entries[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        findViewById<MaterialButton>(R.id.btnRecoveryReminderTime).setOnClickListener {
            TimePickerDialog(this, { _, hour, minute ->
                reminderTime = LocalTime.of(hour, minute); updateTime()
            }, reminderTime.hour, reminderTime.minute, true).show()
        }
        findViewById<MaterialButton>(R.id.btnAddRecoveryMilestone).setOnClickListener {
            val input = findViewById<TextInputEditText>(R.id.inputRecoveryCustomMilestone)
            val days = input.text?.toString()?.toIntOrNull()?.takeIf { it in 1..3650 }
            if (days != null) {
                viewModel.saveMilestone(
                    RecoveryMilestone(
                        goalId = goalId,
                        label = "$days días",
                        thresholdDays = days,
                        kind = RecoveryMilestoneKind.DAYS
                    )
                )
                input.setText("")
            }
        }
        findViewById<MaterialButton>(R.id.btnRecoveryEditGoal).setOnClickListener {
            startActivity(Intent(this, RecoveryGoalEditorActivity::class.java)
                .putExtra(RecoveryDashboardActivity.EXTRA_GOAL_ID, goalId))
        }
        findViewById<MaterialButton>(R.id.btnRecoverySettingsContacts).setOnClickListener {
            startActivity(Intent(this, RecoveryContactsActivity::class.java)
                .putExtra(RecoveryDashboardActivity.EXTRA_GOAL_ID, goalId))
        }
        findViewById<MaterialButton>(R.id.btnSaveRecoverySettings).setOnClickListener { save() }
        findViewById<MaterialButton>(R.id.btnDeleteRecoveryGoal).setOnClickListener { chooseDeletion() }
        updateTime()
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (initialized || state.selectedGoal?.id != goalId) return@collect
                    initialized = true
                    findViewById<MaterialSwitch>(R.id.switchRecoveryPaused).isChecked =
                        state.selectedGoal.status == RecoveryGoalStatus.PAUSED
                    populateReminder(RecoveryReminderType.entries[reminderTypeSpinner.selectedItemPosition])
                }
            }
        }
    }

    private fun save() {
        preferences.setNotificationTextMode(
            if (findViewById<RadioGroup>(R.id.groupRecoveryNotificationPrivacy).checkedRadioButtonId == R.id.radioRecoveryFull) {
                RecoveryNotificationTextMode.FULL
            } else RecoveryNotificationTextMode.DISCREET
        )
        preferences.setVoiceEnabled(findViewById<MaterialSwitch>(R.id.switchRecoveryVoice).isChecked)
        preferences.setBubbleEnabled(findViewById<MaterialSwitch>(R.id.switchRecoveryBubble).isChecked)
        preferences.setMilestonesEnabled(findViewById<MaterialSwitch>(R.id.switchRecoveryMilestones).isChecked)
        preferences.setStatisticsEnabled(findViewById<MaterialSwitch>(R.id.switchRecoveryStatistics).isChecked)
        val selectedType = RecoveryReminderType.entries[reminderTypeSpinner.selectedItemPosition]
        val existing = viewModel.uiState.value.reminders.firstOrNull { it.type == selectedType }
        val paused = findViewById<MaterialSwitch>(R.id.switchRecoveryPaused).isChecked
        viewModel.saveRecoverySettings(
            RecoveryReminder(
                id = existing?.id ?: 0,
                goalId = goalId,
                type = selectedType,
                timeMinutes = reminderTime.hour * 60 + reminderTime.minute,
                enabled = findViewById<MaterialSwitch>(R.id.switchRecoveryCheckInReminder).isChecked,
                snoozeMinutes = existing?.snoozeMinutes ?: 10,
                updatedAtEpochMillis = System.currentTimeMillis()
            ),
            paused = paused
        ) {
            Toast.makeText(this, R.string.recovery_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun chooseDeletion() {
        AlertDialog.Builder(this).setTitle(R.string.recovery_settings_delete_title)
            .setItems(
                arrayOf(
                    getString(R.string.recovery_settings_archive),
                    getString(R.string.recovery_settings_delete_keep),
                    getString(R.string.recovery_settings_delete_all)
                )
            ) { _, which ->
                when (which) {
                    0 -> performDeletion(RecoveryDeletionMode.ARCHIVE, R.string.recovery_settings_archived)
                    1 -> confirmDeletion(
                        R.string.recovery_settings_delete_keep_confirm,
                        RecoveryDeletionMode.DELETE_KEEP_ANONYMOUS_HISTORY,
                        R.string.recovery_settings_deleted_keep
                    )
                    2 -> confirmDeletion(
                        R.string.recovery_settings_delete_all_confirm,
                        RecoveryDeletionMode.DELETE_ALL,
                        R.string.recovery_settings_deleted_all
                    )
                }
            }
            .setNegativeButton(R.string.recovery_cancel, null)
            .show()
    }

    private fun confirmDeletion(message: Int, mode: RecoveryDeletionMode, resultMessage: Int) {
        AlertDialog.Builder(this).setMessage(message)
            .setNegativeButton(R.string.recovery_cancel, null)
            .setPositiveButton(R.string.recovery_delete) { _, _ -> performDeletion(mode, resultMessage) }
            .show()
    }

    private fun performDeletion(mode: RecoveryDeletionMode, message: Int) {
        viewModel.deleteGoal(mode) {
            runOnUiThread {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun updateTime() {
        findViewById<MaterialButton>(R.id.btnRecoveryReminderTime).text =
            reminderTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    private fun populateReminder(type: RecoveryReminderType) {
        val reminder = viewModel.uiState.value.reminders.firstOrNull { it.type == type }
        findViewById<MaterialSwitch>(R.id.switchRecoveryCheckInReminder).isChecked =
            reminder?.enabled ?: false
        reminderTime = reminder?.let { LocalTime.of(it.timeMinutes / 60, it.timeMinutes % 60) }
            ?: LocalTime.of(20, 0)
        updateTime()
    }
}
