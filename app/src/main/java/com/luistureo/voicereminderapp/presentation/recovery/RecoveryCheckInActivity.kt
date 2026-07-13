package com.luistureo.voicereminderapp.presentation.recovery

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryCheckIn
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryCheckInStatus
import java.time.LocalDate
import kotlinx.coroutines.launch

class RecoveryCheckInActivity : ComponentActivity() {
    private lateinit var viewModel: RecoveryViewModel
    private lateinit var triggerSpinner: Spinner
    private lateinit var actionSpinner: Spinner
    private lateinit var additional: View
    private lateinit var intensityLayout: TextInputLayout
    private var initialized = false
    private var lapseReflection: String? = null
    private val goalId by lazy { intent.getIntExtra(RecoveryDashboardActivity.EXTRA_GOAL_ID, 0) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (goalId <= 0) { finish(); return }
        setContentView(R.layout.activity_recovery_check_in)
        viewModel = ViewModelProvider(this, RecoveryViewModelFactory(applicationContext))[
            RecoveryViewModel::class.java
        ]
        setupViews()
        observe()
        viewModel.load(goalId)
    }

    private fun setupViews() {
        findViewById<ImageButton>(R.id.btnBackRecoveryCheckIn).setOnClickListener { finish() }
        triggerSpinner = findViewById(R.id.spinnerRecoveryCheckInTrigger)
        actionSpinner = findViewById(R.id.spinnerRecoveryCheckInAction)
        additional = findViewById(R.id.containerRecoveryCheckInAdditional)
        intensityLayout = findViewById(R.id.layoutRecoveryIntensity)
        findViewById<MaterialButton>(R.id.btnRecoveryCheckInAdditional).setOnClickListener {
            additional.visibility = if (additional.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        findViewById<MaterialButton>(R.id.btnSaveRecoveryCheckIn).setOnClickListener { validateAndSave() }
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val goal = state.selectedGoal?.takeIf { it.id == goalId } ?: return@collect
                    triggerSpinner.adapter = ArrayAdapter(
                        this@RecoveryCheckInActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        listOf(getString(R.string.recovery_none)) + state.triggers.filter { it.enabled }.map { it.label }
                    )
                    actionSpinner.adapter = ArrayAdapter(
                        this@RecoveryCheckInActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        listOf(getString(R.string.recovery_none)) + state.helpfulActions.filter { it.enabled }.map { it.label }
                    )
                    findViewById<MaterialSwitch>(R.id.switchRecoveryReducedFrequency).visibility =
                        if (goal.reductionTrackingEnabled) View.VISIBLE else View.GONE
                    if (!initialized) {
                        initialized = true
                        state.dashboard?.todayCheckIn?.let(::populate)
                    }
                }
            }
        }
    }

    private fun populate(checkIn: RecoveryCheckIn) {
        findViewById<RadioGroup>(R.id.groupRecoveryCheckIn).check(
            when (checkIn.status) {
                RecoveryCheckInStatus.ACHIEVED -> R.id.radioRecoveryAchieved
                RecoveryCheckInStatus.DIFFICULTY_MANAGED -> R.id.radioRecoveryManaged
                RecoveryCheckInStatus.LAPSE -> R.id.radioRecoveryLapse
                RecoveryCheckInStatus.PREFER_NOT_TO_REGISTER -> R.id.radioRecoverySkip
            }
        )
        findViewById<TextInputEditText>(R.id.inputRecoveryIntensity).setText(
            checkIn.cravingIntensity?.toString().orEmpty()
        )
        findViewById<TextInputEditText>(R.id.inputRecoveryCheckInNote).setText(checkIn.note)
        findViewById<MaterialSwitch>(R.id.switchRecoveryReducedFrequency).isChecked = checkIn.reducedFrequency
        additional.visibility = if (
            checkIn.cravingIntensity != null || checkIn.trigger != null ||
            checkIn.helpfulAction != null || checkIn.note != null || checkIn.reducedFrequency
        ) View.VISIBLE else View.GONE
        triggerSpinner.post {
            val values = viewModel.uiState.value.triggers.filter { it.enabled }.map { it.label }
            triggerSpinner.setSelection((values.indexOf(checkIn.trigger) + 1).coerceAtLeast(0))
        }
        actionSpinner.post {
            val values = viewModel.uiState.value.helpfulActions.filter { it.enabled }.map { it.label }
            actionSpinner.setSelection((values.indexOf(checkIn.helpfulAction) + 1).coerceAtLeast(0))
        }
    }

    private fun validateAndSave() {
        val status = when (findViewById<RadioGroup>(R.id.groupRecoveryCheckIn).checkedRadioButtonId) {
            R.id.radioRecoveryAchieved -> RecoveryCheckInStatus.ACHIEVED
            R.id.radioRecoveryManaged -> RecoveryCheckInStatus.DIFFICULTY_MANAGED
            R.id.radioRecoveryLapse -> RecoveryCheckInStatus.LAPSE
            R.id.radioRecoverySkip -> RecoveryCheckInStatus.PREFER_NOT_TO_REGISTER
            else -> null
        }
        val validation = findViewById<TextView>(R.id.tvRecoveryCheckInValidation)
        if (status == null) {
            validation.setText(R.string.recovery_checkin_prompt)
            validation.visibility = View.VISIBLE
            return
        }
        validation.visibility = View.GONE
        val intensity = findViewById<TextInputEditText>(R.id.inputRecoveryIntensity).text
            ?.toString()?.toIntOrNull()
        intensityLayout.error = if (intensity != null && intensity !in 1..10) {
            getString(R.string.recovery_invalid_intensity)
        } else null
        if (intensityLayout.error != null) return
        if (status == RecoveryCheckInStatus.LAPSE) {
            showLapseReflection()
        } else save(status, false)
    }

    private fun showLapseReflection() {
        val input = EditText(this).apply {
            hint = getString(R.string.recovery_lapse_next_time)
            maxLines = 4
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.recovery_lapse_support)
            .setMessage(R.string.recovery_lapse_continue)
            .setView(input)
            .setPositiveButton(R.string.recovery_lapse_continue_action) { _, _ ->
                lapseReflection = input.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
                showRestartDecision()
            }
            .setNegativeButton(R.string.recovery_lapse_skip_note) { _, _ -> showRestartDecision() }
            .show()
    }

    private fun showRestartDecision() {
            AlertDialog.Builder(this)
                .setTitle(R.string.recovery_lapse_restart_title)
                .setMessage(R.string.recovery_lapse_restart_message)
                .setPositiveButton(R.string.recovery_lapse_restart) { _, _ -> save(RecoveryCheckInStatus.LAPSE, true) }
                .setNegativeButton(R.string.recovery_lapse_keep) { _, _ -> save(RecoveryCheckInStatus.LAPSE, false) }
                .show()
    }

    private fun save(status: RecoveryCheckInStatus, resetsStreak: Boolean) {
        val state = viewModel.uiState.value
        val goal = state.selectedGoal ?: return
        val existing = state.dashboard?.todayCheckIn
        val omitDetails = status == RecoveryCheckInStatus.PREFER_NOT_TO_REGISTER
        val now = System.currentTimeMillis()
        val writtenNote = findViewById<TextInputEditText>(R.id.inputRecoveryCheckInNote).text
            ?.toString()?.trim()?.takeIf { it.isNotBlank() }
        val combinedNote = listOfNotNull(writtenNote, lapseReflection).distinct().joinToString("\n")
            .takeIf { it.isNotBlank() }
        viewModel.saveCheckIn(
            RecoveryCheckIn(
                id = existing?.id ?: 0,
                goalId = goal.id,
                goalHistoryKey = goal.historyKey,
                date = LocalDate.now(),
                status = status,
                cravingIntensity = if (omitDetails) null else findViewById<TextInputEditText>(R.id.inputRecoveryIntensity).text?.toString()?.toIntOrNull(),
                trigger = if (omitDetails || triggerSpinner.selectedItemPosition <= 0) null else triggerSpinner.selectedItem?.toString(),
                helpfulAction = if (omitDetails || actionSpinner.selectedItemPosition <= 0) null else actionSpinner.selectedItem?.toString(),
                note = if (omitDetails) null else combinedNote,
                reducedFrequency = !omitDetails && findViewById<MaterialSwitch>(R.id.switchRecoveryReducedFrequency).isChecked,
                resetsStreak = resetsStreak,
                createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now
            )
        ) {
            Toast.makeText(this, R.string.recovery_checkin_saved, Toast.LENGTH_SHORT).show()
            if (status == RecoveryCheckInStatus.LAPSE) showLapseNextActions() else finish()
        }
    }

    private fun showLapseNextActions() {
        AlertDialog.Builder(this)
            .setMessage(R.string.recovery_lapse_continue)
            .setItems(
                arrayOf(
                    getString(R.string.recovery_lapse_review_tools),
                    getString(R.string.recovery_lapse_adjust_reminders),
                    getString(R.string.recovery_lapse_close)
                )
            ) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, RecoveryToolsActivity::class.java)
                        .putExtra(RecoveryDashboardActivity.EXTRA_GOAL_ID, goalId))
                    1 -> startActivity(Intent(this, RecoverySettingsActivity::class.java)
                        .putExtra(RecoveryDashboardActivity.EXTRA_GOAL_ID, goalId))
                }
                finish()
            }
            .setOnCancelListener { finish() }
            .show()
    }
}
