package com.luistureo.voicereminderapp.presentation.routine

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.checkbox.MaterialCheckBox
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.domain.routine.model.Routine
import com.luistureo.voicereminderapp.domain.routine.model.RoutineAssistantMode
import com.luistureo.voicereminderapp.domain.routine.model.RoutinePeriod
import com.luistureo.voicereminderapp.presentation.routine.adapter.RoutineEditorTaskAdapter
import com.luistureo.voicereminderapp.presentation.routine.state.RoutineEditorState
import com.luistureo.voicereminderapp.presentation.routine.state.RoutineTaskDraft
import com.luistureo.voicereminderapp.presentation.routine.viewmodel.RoutineViewModel
import com.luistureo.voicereminderapp.presentation.routine.viewmodel.RoutineViewModelFactory
import java.time.LocalTime
import java.util.Locale
import kotlinx.coroutines.launch

class RoutineEditorActivity : ComponentActivity() {
    private lateinit var viewModel: RoutineViewModel
    private lateinit var taskAdapter: RoutineEditorTaskAdapter
    private lateinit var nameLayout: TextInputLayout
    private lateinit var nameInput: TextInputEditText
    private lateinit var descriptionInput: TextInputEditText
    private lateinit var categoryInput: TextInputEditText
    private lateinit var periodGroup: RadioGroup
    private lateinit var colorGroup: RadioGroup
    private lateinit var iconSpinner: Spinner
    private lateinit var startButton: MaterialButton
    private lateinit var deadlineButton: MaterialButton
    private lateinit var advancedSettingsButton: MaterialButton
    private lateinit var templateSourceText: TextView
    private lateinit var voiceCheck: MaterialCheckBox
    private lateinit var bubbleCheck: MaterialCheckBox
    private var editorState = RoutineEditorState()
    private var startTime: LocalTime? = null
    private var deadlineTime: LocalTime? = null
    private var sourceRoutine: Routine? = null
    private var initialized = false
    private val routineId by lazy { intent.getIntExtra(EXTRA_ROUTINE_ID, 0) }
    private val templateId by lazy { intent.getIntExtra(EXTRA_TEMPLATE_ID, 0) }
    private val duplicate by lazy { intent.getBooleanExtra(EXTRA_DUPLICATE, false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_routine_editor)
        viewModel = ViewModelProvider(
            this,
            RoutineViewModelFactory(applicationContext)
        )[RoutineViewModel::class.java]
        setupViews()
        setupTaskList()
        setupListeners()
        observeState()
        when {
            routineId != 0 -> viewModel.loadRoutine(routineId)
            templateId != 0 -> viewModel.loadTemplate(templateId)
            else -> initializeBlankEditor()
        }
    }

    private fun setupViews() {
        findViewById<ImageButton>(R.id.btnBackRoutineEditor).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvRoutineEditorTitle).setText(
            if (routineId == 0) R.string.routine_editor_create_title
            else R.string.routine_editor_edit_title
        )
        nameLayout = findViewById(R.id.layoutRoutineName)
        nameInput = findViewById(R.id.inputRoutineName)
        descriptionInput = findViewById(R.id.inputRoutineDescription)
        categoryInput = findViewById(R.id.inputRoutineCategory)
        periodGroup = findViewById(R.id.groupRoutinePeriod)
        colorGroup = findViewById(R.id.groupRoutineColor)
        iconSpinner = findViewById(R.id.spinnerRoutineIcon)
        startButton = findViewById(R.id.btnRoutineStartTime)
        deadlineButton = findViewById(R.id.btnRoutineDeadlineTime)
        advancedSettingsButton = findViewById(R.id.btnRoutineAdvancedSettings)
        templateSourceText = findViewById(R.id.tvRoutineTemplateSource)
        voiceCheck = findViewById(R.id.checkEditorRoutineVoice)
        bubbleCheck = findViewById(R.id.checkEditorRoutineBubble)
        ArrayAdapter.createFromResource(
            this,
            R.array.routine_icon_options,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            iconSpinner.adapter = adapter
        }
    }

    private fun setupTaskList() {
        taskAdapter = RoutineEditorTaskAdapter(
            onEdit = ::showTaskEditor,
            onDelete = { task ->
                editorState = editorState.deleteTask(task.localId)
                taskAdapter.submitList(editorState.tasks)
            }
        )
        val recycler = findViewById<RecyclerView>(R.id.recyclerRoutineEditorTasks)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = taskAdapter
        ItemTouchHelper(
            object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                0
            ) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    editorState = editorState.moveTask(
                        viewHolder.bindingAdapterPosition,
                        target.bindingAdapterPosition
                    )
                    taskAdapter.submitList(editorState.tasks)
                    return true
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
            }
        ).attachToRecyclerView(recycler)
    }

    private fun setupListeners() {
        findViewById<MaterialButton>(R.id.btnAddRoutineTask).setOnClickListener {
            showTaskEditor(null)
        }
        startButton.setOnClickListener {
            selectOptionalTime(startTime, R.string.routine_start_time) { selected ->
                startTime = selected
                updateTimeButtons()
            }
        }
        deadlineButton.setOnClickListener {
            selectOptionalTime(deadlineTime, R.string.routine_deadline_time) { selected ->
                deadlineTime = selected
                updateTimeButtons()
            }
        }
        advancedSettingsButton.setOnClickListener {
            startActivity(
                Intent(this, RoutineSettingsActivity::class.java)
                    .putExtra(RoutineSettingsActivity.EXTRA_ROUTINE_ID, routineId)
            )
        }
        findViewById<MaterialButton>(R.id.btnSaveRoutine).setOnClickListener { saveRoutine() }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (!initialized && routineId != 0 && state.selectedRoutine != null) {
                        initializeFromRoutine(state.selectedRoutine, state.tasks)
                    }
                    if (!initialized && templateId != 0 && state.selectedTemplate != null) {
                        initializeFromTemplate(state.selectedTemplate)
                    }
                    state.message?.let { message ->
                        Toast.makeText(this@RoutineEditorActivity, message, Toast.LENGTH_SHORT).show()
                        viewModel.consumeMessage()
                    }
                }
            }
        }
    }

    private fun initializeBlankEditor() {
        initialized = true
        categoryInput.setText(getString(R.string.routine_module_title))
        periodGroup.check(R.id.radioRoutineMorning)
        colorGroup.check(R.id.radioRoutineColorBlue)
        updateTimeButtons()
    }

    private fun initializeFromRoutine(
        routine: Routine,
        tasks: List<com.luistureo.voicereminderapp.domain.routine.model.RoutineTask>
    ) {
        sourceRoutine = routine.takeUnless { duplicate }
        initialized = true
        nameInput.setText(if (duplicate) "${routine.name} (copia)" else routine.name)
        descriptionInput.setText(routine.description)
        categoryInput.setText(routine.category)
        periodGroup.check(
            when (routine.period) {
                RoutinePeriod.MORNING -> R.id.radioRoutineMorning
                RoutinePeriod.AFTERNOON -> R.id.radioRoutineAfternoon
                RoutinePeriod.NIGHT -> R.id.radioRoutineNight
            }
        )
        colorGroup.check(colorIdFor(routine.color))
        iconSpinner.setSelection(iconValues.indexOf(routine.icon).coerceAtLeast(0))
        startTime = routine.startTime
        deadlineTime = routine.deadlineTime
        voiceCheck.isChecked = routine.voiceEnabled
        bubbleCheck.isChecked = routine.motivationBubbleEnabled
        editorState = RoutineEditorState.fromTasks(tasks)
        taskAdapter.submitList(editorState.tasks)
        advancedSettingsButton.isVisible = true
        updateTimeButtons()
    }

    private fun initializeFromTemplate(
        template: com.luistureo.voicereminderapp.domain.routine.model.RoutineTemplate
    ) {
        initialized = true
        nameInput.setText(template.name)
        descriptionInput.setText(template.description)
        categoryInput.setText(template.category)
        periodGroup.check(when (template.period) {
            RoutinePeriod.MORNING -> R.id.radioRoutineMorning
            RoutinePeriod.AFTERNOON -> R.id.radioRoutineAfternoon
            RoutinePeriod.NIGHT -> R.id.radioRoutineNight
        })
        colorGroup.check(colorIdFor(template.color ?: COLOR_BLUE))
        iconSpinner.setSelection(iconValues.indexOf(template.icon).coerceAtLeast(0))
        editorState = RoutineEditorState.fromTemplate(template.suggestedTasks)
        taskAdapter.submitList(editorState.tasks)
        templateSourceText.text = getString(R.string.routine_template_source, template.name)
        templateSourceText.isVisible = true
        updateTimeButtons()
    }

    private fun saveRoutine() {
        nameLayout.error = null
        val name = nameInput.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) {
            nameLayout.error = getString(R.string.routine_error_name_required)
            return
        }
        if (editorState.tasks.isEmpty()) {
            Toast.makeText(this, R.string.routine_error_task_required, Toast.LENGTH_SHORT).show()
            return
        }
        val now = System.currentTimeMillis()
        val base = sourceRoutine
        val routine = Routine(
            id = base?.id ?: 0,
            name = name,
            description = descriptionInput.text?.toString()?.trim().orEmpty(),
            category = categoryInput.text?.toString()?.trim().orEmpty()
                .ifBlank { getString(R.string.routine_module_title) },
            icon = iconValues[iconSpinner.selectedItemPosition.coerceIn(iconValues.indices)],
            color = selectedColor(),
            enabled = base?.enabled ?: false,
            period = selectedPeriod(),
            startTime = startTime,
            deadlineTime = deadlineTime,
            assistantMode = base?.assistantMode ?: RoutineAssistantMode.SIMPLE_DISPLAY,
            voiceEnabled = voiceCheck.isChecked,
            motivationBubbleEnabled = bubbleCheck.isChecked,
            motivationSchedule = base?.motivationSchedule,
            createdAtEpochMillis = base?.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = now
        )
        val tasks = editorState.tasks.mapIndexed { index, task -> task.toDomain(routine.id, index) }
        viewModel.saveRoutine(routine, tasks) { savedId ->
            startActivity(
                Intent(this, RoutineDetailActivity::class.java)
                    .putExtra(RoutineDetailActivity.EXTRA_ROUTINE_ID, savedId)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            finish()
        }
    }

    private fun showTaskEditor(existing: RoutineTaskDraft?) {
        val content = LayoutInflater.from(this).inflate(R.layout.dialog_routine_task_editor, null)
        val name = content.findViewById<TextInputEditText>(R.id.inputRoutineTaskName)
        val nameContainer = content.findViewById<TextInputLayout>(R.id.layoutRoutineTaskName)
        val duration = content.findViewById<TextInputEditText>(R.id.inputRoutineTaskDuration)
        val notes = content.findViewById<TextInputEditText>(R.id.inputRoutineTaskNotes)
        val timeButton = content.findViewById<MaterialButton>(R.id.btnRoutineTaskTime)
        var selectedTime = existing?.optionalTime
        name.setText(existing?.title.orEmpty())
        duration.setText(
            existing?.estimatedDurationMinutes?.let {
                String.format(Locale.getDefault(), "%d", it)
            }.orEmpty()
        )
        notes.setText(existing?.notes.orEmpty())
        fun updateTaskTime() {
            timeButton.text = selectedTime?.let(RoutineUiFormatter::time)
                ?: getString(R.string.routine_task_time_optional)
        }
        updateTaskTime()
        timeButton.setOnClickListener {
            selectOptionalTime(selectedTime, R.string.routine_task_time_optional) {
                selectedTime = it
                updateTaskTime()
            }
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(
                if (existing == null) R.string.routine_task_editor_add_title
                else R.string.routine_task_editor_edit_title
            )
            .setView(content)
            .setNegativeButton(R.string.routine_task_cancel, null)
            .setPositiveButton(R.string.routine_task_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                nameContainer.error = null
                val title = name.text?.toString()?.trim().orEmpty()
                if (title.isBlank()) {
                    nameContainer.error = getString(R.string.routine_error_name_required)
                    return@setOnClickListener
                }
                val durationText = duration.text?.toString()?.trim().orEmpty()
                val durationMinutes = durationText.toIntOrNull()
                if (durationText.isNotEmpty() && (durationMinutes == null || durationMinutes <= 0)) {
                    duration.error = getString(R.string.routine_task_duration_optional)
                    return@setOnClickListener
                }
                editorState = if (existing == null) {
                    editorState.addTask(
                        title,
                        selectedTime,
                        durationMinutes,
                        notes.text?.toString()?.trim()?.takeIf(String::isNotBlank)
                    )
                } else {
                    editorState.editTask(
                        existing.copy(
                            title = title,
                            optionalTime = selectedTime,
                            estimatedDurationMinutes = durationMinutes,
                            notes = notes.text?.toString()?.trim()?.takeIf(String::isNotBlank)
                        )
                    )
                }
                taskAdapter.submitList(editorState.tasks)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun selectOptionalTime(
        current: LocalTime?,
        titleRes: Int,
        onSelected: (LocalTime?) -> Unit
    ) {
        if (current == null) {
            showTimePicker(LocalTime.now(), onSelected)
            return
        }
        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setItems(
                arrayOf(
                    getString(R.string.routine_change_time),
                    getString(R.string.routine_clear_time)
                )
            ) { _, which ->
                if (which == 0) showTimePicker(current, onSelected) else onSelected(null)
            }
            .show()
    }

    private fun showTimePicker(initial: LocalTime, onSelected: (LocalTime?) -> Unit) {
        TimePickerDialog(
            this,
            { _, hour, minute -> onSelected(LocalTime.of(hour, minute)) },
            initial.hour,
            initial.minute,
            true
        ).show()
    }

    private fun updateTimeButtons() {
        startButton.text = startTime?.let(RoutineUiFormatter::time)
            ?: getString(R.string.routine_start_time)
        deadlineButton.text = deadlineTime?.let(RoutineUiFormatter::time)
            ?: getString(R.string.routine_deadline_time)
    }

    private fun selectedPeriod(): RoutinePeriod = when (periodGroup.checkedRadioButtonId) {
        R.id.radioRoutineAfternoon -> RoutinePeriod.AFTERNOON
        R.id.radioRoutineNight -> RoutinePeriod.NIGHT
        else -> RoutinePeriod.MORNING
    }

    private fun selectedColor(): Int = when (colorGroup.checkedRadioButtonId) {
        R.id.radioRoutineColorAmber -> COLOR_AMBER
        R.id.radioRoutineColorPurple -> COLOR_PURPLE
        else -> COLOR_BLUE
    }

    private fun colorIdFor(color: Int): Int = when (color) {
        COLOR_AMBER -> R.id.radioRoutineColorAmber
        COLOR_PURPLE -> R.id.radioRoutineColorPurple
        else -> R.id.radioRoutineColorBlue
    }

    companion object {
        const val EXTRA_ROUTINE_ID = "extra_routine_id"
        const val EXTRA_TEMPLATE_ID = "extra_template_id"
        const val EXTRA_DUPLICATE = "extra_duplicate"
        private val COLOR_BLUE = 0xFF496A84.toInt()
        private val COLOR_AMBER = 0xFFC98524.toInt()
        private val COLOR_PURPLE = 0xFF5968A8.toInt()
        private val iconValues = listOf("wb_sunny", "light_mode", "bedtime")
    }
}
