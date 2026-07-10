package com.luistureo.voicereminderapp.presentation.routine

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.presentation.routine.adapter.RoutineTaskAdapter
import com.luistureo.voicereminderapp.presentation.routine.state.RoutinePresentationPolicy
import com.luistureo.voicereminderapp.presentation.routine.viewmodel.RoutineViewModel
import com.luistureo.voicereminderapp.presentation.routine.viewmodel.RoutineViewModelFactory
import com.luistureo.voicereminderapp.presentation.assistant.AssistantActivity
import com.luistureo.voicereminderapp.domain.routine.model.RoutineAssistantMode
import java.time.LocalDate
import kotlinx.coroutines.launch

class RoutineDetailActivity : ComponentActivity() {
    private lateinit var viewModel: RoutineViewModel
    private lateinit var adapter: RoutineTaskAdapter
    private lateinit var nameText: TextView
    private lateinit var descriptionText: TextView
    private lateinit var progressText: TextView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var periodIcon: ImageView
    private lateinit var disabledText: TextView
    private lateinit var emptyText: TextView
    private lateinit var successText: TextView
    private lateinit var completeButton: MaterialButton
    private lateinit var partialButton: MaterialButton
    private lateinit var assistantButton: MaterialButton
    private var routineId: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        routineId = intent.getIntExtra(EXTRA_ROUTINE_ID, 0)
        if (routineId <= 0) {
            finish()
            return
        }
        setContentView(R.layout.activity_routine_detail)
        viewModel = ViewModelProvider(
            this,
            RoutineViewModelFactory(applicationContext)
        )[RoutineViewModel::class.java]
        setupViews()
        observeState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val newRoutineId = intent.getIntExtra(EXTRA_ROUTINE_ID, 0)
        if (newRoutineId <= 0) {
            finish()
            return
        }
        setIntent(intent)
        routineId = newRoutineId
        if (::viewModel.isInitialized) viewModel.loadRoutine(routineId)
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) viewModel.loadRoutine(routineId)
    }

    private fun setupViews() {
        findViewById<ImageButton>(R.id.btnBackRoutineDetail).setOnClickListener { finish() }
        nameText = findViewById(R.id.tvRoutineDetailName)
        descriptionText = findViewById(R.id.tvRoutineDetailDescription)
        progressText = findViewById(R.id.tvRoutineDetailProgress)
        progress = findViewById(R.id.progressRoutineDetail)
        periodIcon = findViewById(R.id.imageRoutineDetailPeriod)
        disabledText = findViewById(R.id.tvRoutineDisabled)
        emptyText = findViewById(R.id.tvRoutineTasksEmpty)
        successText = findViewById(R.id.tvRoutineSuccess)
        completeButton = findViewById(R.id.btnCompleteRoutine)
        partialButton = findViewById(R.id.btnMarkRoutinePartial)
        assistantButton = findViewById(R.id.btnStartRoutineAssistant)
        adapter = RoutineTaskAdapter(viewModel::toggleTask)
        findViewById<RecyclerView>(R.id.recyclerRoutineTasks).apply {
            layoutManager = LinearLayoutManager(this@RoutineDetailActivity)
            adapter = this@RoutineDetailActivity.adapter
        }
        completeButton.setOnClickListener { viewModel.completeSelectedRoutine() }
        partialButton.setOnClickListener { viewModel.markSelectedRoutinePartial() }
        assistantButton.setOnClickListener {
            startActivity(
                Intent(this, AssistantActivity::class.java)
                    .putExtra(AssistantActivity.EXTRA_ROUTINE_ID, routineId)
                    .putExtra(
                        AssistantActivity.EXTRA_ROUTINE_DATE_EPOCH_DAY,
                        LocalDate.now().toEpochDay()
                    )
                    .putExtra(AssistantActivity.EXTRA_ROUTINE_NOTIFICATION_ID, 0)
            )
        }
        findViewById<MaterialButton>(R.id.btnEditRoutine).setOnClickListener {
            startActivity(
                Intent(this, RoutineEditorActivity::class.java)
                    .putExtra(RoutineEditorActivity.EXTRA_ROUTINE_ID, routineId)
            )
        }
        findViewById<MaterialButton>(R.id.btnDuplicateRoutine).setOnClickListener {
            startActivity(Intent(this, RoutineEditorActivity::class.java)
                .putExtra(RoutineEditorActivity.EXTRA_ROUTINE_ID, routineId)
                .putExtra(RoutineEditorActivity.EXTRA_DUPLICATE, true))
        }
        findViewById<MaterialButton>(R.id.btnSaveRoutineAsTemplate).setOnClickListener {
            val state = viewModel.uiState.value
            val routine = state.selectedRoutine ?: return@setOnClickListener
            viewModel.savePersonalTemplate(routine, state.tasks) {
                Toast.makeText(this, R.string.routine_personal_template_saved, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val routine = state.selectedRoutine
                    if (routine != null) {
                        val item = RoutinePresentationPolicy.dashboardItem(
                            routine,
                            state.tasks,
                            LocalDate.now()
                        )
                        nameText.text = routine.name
                        descriptionText.text = routine.description
                        periodIcon.setImageResource(RoutineUiFormatter.icon(routine))
                        periodIcon.imageTintList = ColorStateList.valueOf(routine.color)
                        progressText.text = resources.getQuantityString(
                            R.plurals.routine_task_progress_short,
                            item.totalTasks,
                            item.completedTasks,
                            item.totalTasks
                        )
                        progress.setProgressCompat(item.percentage, true)
                        progress.setIndicatorColor(routine.color)
                        disabledText.isVisible = !routine.enabled
                        completeButton.isVisible = RoutinePresentationPolicy.activeActionsVisible(routine)
                        partialButton.isVisible = RoutinePresentationPolicy.activeActionsVisible(routine)
                        assistantButton.isVisible = routine.enabled &&
                            routine.assistantMode != RoutineAssistantMode.SIMPLE_DISPLAY
                        adapter.interactionsEnabled = routine.enabled
                    }
                    adapter.submitList(state.tasks)
                    emptyText.isVisible = !state.isLoading && state.tasks.isEmpty()
                    if (state.showCompletionFeedback) showSuccessFeedback()
                    state.message?.let { message ->
                        Toast.makeText(this@RoutineDetailActivity, message, Toast.LENGTH_SHORT).show()
                        viewModel.consumeMessage()
                    }
                }
            }
        }
    }

    private fun showSuccessFeedback() {
        successText.isVisible = true
        successText.alpha = 0f
        successText.scaleX = 0.96f
        successText.scaleY = 0.96f
        successText.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220L).start()
        viewModel.consumeCompletionFeedback()
    }

    companion object {
        const val EXTRA_ROUTINE_ID = "extra_routine_id"
    }
}
