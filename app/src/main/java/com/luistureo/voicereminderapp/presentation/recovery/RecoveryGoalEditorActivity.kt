package com.luistureo.voicereminderapp.presentation.recovery

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
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
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryCategory
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryGoal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

class RecoveryGoalEditorActivity : ComponentActivity() {
    private lateinit var viewModel: RecoveryViewModel
    private lateinit var categorySpinner: Spinner
    private lateinit var additional: View
    private lateinit var titleLayout: TextInputLayout
    private lateinit var customLayout: TextInputLayout
    private var startDate: LocalDate? = null
    private var targetDate: LocalDate? = null
    private var existing: RecoveryGoal? = null
    private var initialized = false
    private val goalId by lazy { intent.getIntExtra(RecoveryDashboardActivity.EXTRA_GOAL_ID, 0) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recovery_goal_editor)
        viewModel = ViewModelProvider(this, RecoveryViewModelFactory(applicationContext))[
            RecoveryViewModel::class.java
        ]
        setupViews()
        observe()
        if (goalId > 0) viewModel.load(goalId)
    }

    private fun setupViews() {
        findViewById<ImageButton>(R.id.btnBackRecoveryGoal).setOnClickListener { finish() }
        if (goalId > 0) findViewById<TextView>(R.id.tvRecoveryGoalEditorTitle)
            .setText(R.string.recovery_goal_editor_edit)
        titleLayout = findViewById(R.id.layoutRecoveryGoalTitle)
        customLayout = findViewById(R.id.layoutRecoveryCustomCategory)
        additional = findViewById(R.id.containerRecoveryGoalAdditional)
        categorySpinner = findViewById(R.id.spinnerRecoveryGoalCategory)
        categorySpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf(
                R.string.recovery_goal_category_alcohol,
                R.string.recovery_goal_category_nicotine,
                R.string.recovery_goal_category_gambling,
                R.string.recovery_goal_category_gaming,
                R.string.recovery_goal_category_screen,
                R.string.recovery_goal_category_shopping,
                R.string.recovery_goal_category_other
            ).map(::getString)
        )
        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val category = RecoveryCategory.entries[position]
                customLayout.visibility = if (category == RecoveryCategory.OTHER) View.VISIBLE else View.GONE
                findViewById<View>(R.id.tvRecoveryProfessionalGuidance).visibility =
                    if (category == RecoveryCategory.ALCOHOL || category == RecoveryCategory.NICOTINE) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        findViewById<MaterialButton>(R.id.btnRecoveryAdditionalOptions).setOnClickListener {
            additional.visibility = if (additional.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        findViewById<MaterialButton>(R.id.btnRecoveryStartDate).setOnClickListener {
            selectDate(startDate) { startDate = it; updateDateButtons() }
        }
        findViewById<MaterialButton>(R.id.btnRecoveryTargetDate).setOnClickListener {
            selectDate(targetDate) { targetDate = it; updateDateButtons() }
        }
        findViewById<MaterialButton>(R.id.btnSaveRecoveryGoal).setOnClickListener { save() }
        updateDateButtons()
    }

    private fun observe() {
        if (goalId <= 0) return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (initialized) return@collect
                    val goal = state.selectedGoal?.takeIf { it.id == goalId } ?: return@collect
                    initialized = true
                    existing = goal
                    findViewById<TextInputEditText>(R.id.inputRecoveryGoalTitle).setText(goal.title)
                    categorySpinner.setSelection(goal.category.ordinal)
                    findViewById<TextInputEditText>(R.id.inputRecoveryCustomCategory).setText(goal.customCategory)
                    findViewById<TextInputEditText>(R.id.inputRecoveryGoalReason).setText(goal.personalReason)
                    findViewById<TextInputEditText>(R.id.inputRecoveryGoalMotivations).setText(goal.motivations)
                    findViewById<MaterialSwitch>(R.id.switchRecoveryReductionTracking).isChecked =
                        goal.reductionTrackingEnabled
                    startDate = goal.startDate
                    targetDate = goal.targetDate
                    if (listOf(goal.startDate, goal.targetDate, goal.personalReason, goal.motivations)
                            .any { it != null }) additional.visibility = View.VISIBLE
                    updateDateButtons()
                }
            }
        }
    }

    private fun save() {
        val title = findViewById<TextInputEditText>(R.id.inputRecoveryGoalTitle).text?.toString().orEmpty().trim()
        titleLayout.error = if (title.isBlank()) getString(R.string.recovery_required_field) else null
        val category = RecoveryCategory.entries[categorySpinner.selectedItemPosition]
        val custom = findViewById<TextInputEditText>(R.id.inputRecoveryCustomCategory).text?.toString().orEmpty().trim()
        customLayout.error = if (category == RecoveryCategory.OTHER && custom.isBlank()) {
            getString(R.string.recovery_required_field)
        } else null
        if (title.isBlank() || customLayout.error != null) return
        if (startDate != null && targetDate?.isBefore(startDate) == true) {
            findViewById<MaterialButton>(R.id.btnRecoveryTargetDate).error =
                getString(R.string.recovery_goal_date_order_error)
            return
        }
        val base = existing
        val now = System.currentTimeMillis()
        viewModel.saveGoal(
            RecoveryGoal(
                id = base?.id ?: 0,
                historyKey = base?.historyKey.orEmpty(),
                title = title,
                category = category,
                customCategory = custom.takeIf { category == RecoveryCategory.OTHER },
                startDate = startDate,
                targetDate = targetDate,
                personalReason = findViewById<TextInputEditText>(R.id.inputRecoveryGoalReason).text?.toString(),
                motivations = findViewById<TextInputEditText>(R.id.inputRecoveryGoalMotivations).text?.toString(),
                reductionTrackingEnabled = findViewById<MaterialSwitch>(R.id.switchRecoveryReductionTracking).isChecked,
                status = base?.status ?: com.luistureo.voicereminderapp.domain.recovery.model.RecoveryGoalStatus.ACTIVE,
                createdAtEpochMillis = base?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now
            )
        ) { finish() }
    }

    private fun selectDate(current: LocalDate?, onSelected: (LocalDate) -> Unit) {
        val initial = current ?: LocalDate.now()
        DatePickerDialog(this, { _, year, month, day ->
            onSelected(LocalDate.of(year, month + 1, day))
        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
    }

    private fun updateDateButtons() {
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.forLanguageTag("es-CL"))
        findViewById<MaterialButton>(R.id.btnRecoveryStartDate).text = startDate?.format(formatter)
            ?: getString(R.string.recovery_goal_start_date)
        findViewById<MaterialButton>(R.id.btnRecoveryTargetDate).text = targetDate?.format(formatter)
            ?: getString(R.string.recovery_goal_target_date)
    }
}
