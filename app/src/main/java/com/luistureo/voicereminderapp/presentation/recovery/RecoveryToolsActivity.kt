package com.luistureo.voicereminderapp.presentation.recovery

import android.app.AlertDialog
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryHelpfulAction
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryTrigger
import com.luistureo.voicereminderapp.presentation.recovery.adapter.RecoveryToolAdapter
import com.luistureo.voicereminderapp.presentation.recovery.adapter.RecoveryToolRow
import kotlinx.coroutines.launch

class RecoveryToolsActivity : ComponentActivity() {
    private lateinit var viewModel: RecoveryViewModel
    private lateinit var triggerAdapter: RecoveryToolAdapter
    private lateinit var actionAdapter: RecoveryToolAdapter
    private val goalId by lazy { intent.getIntExtra(RecoveryDashboardActivity.EXTRA_GOAL_ID, 0) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (goalId <= 0) { finish(); return }
        setContentView(R.layout.activity_recovery_tools)
        viewModel = ViewModelProvider(this, RecoveryViewModelFactory(applicationContext))[
            RecoveryViewModel::class.java
        ]
        setupViews()
        observe()
        viewModel.load(goalId)
    }

    private fun setupViews() {
        findViewById<ImageButton>(R.id.btnBackRecoveryTools).setOnClickListener { finish() }
        triggerAdapter = RecoveryToolAdapter { row ->
            viewModel.uiState.value.triggers.firstOrNull { it.id == row.id }?.let { item ->
                confirmDelete { viewModel.deleteTrigger(item) }
            }
        }
        actionAdapter = RecoveryToolAdapter { row ->
            viewModel.uiState.value.helpfulActions.firstOrNull { it.id == row.id }?.let { item ->
                confirmDelete { viewModel.deleteHelpfulAction(item) }
            }
        }
        findViewById<RecyclerView>(R.id.recyclerRecoveryTriggers).apply {
            layoutManager = LinearLayoutManager(this@RecoveryToolsActivity); adapter = triggerAdapter
        }
        findViewById<RecyclerView>(R.id.recyclerRecoveryActions).apply {
            layoutManager = LinearLayoutManager(this@RecoveryToolsActivity); adapter = actionAdapter
        }
        findViewById<MaterialButton>(R.id.btnAddRecoveryTrigger).setOnClickListener {
            val input = findViewById<TextInputEditText>(R.id.inputRecoveryTrigger)
            val value = input.text?.toString().orEmpty().trim()
            if (value.isNotBlank()) {
                viewModel.saveTrigger(RecoveryTrigger(goalId = goalId, label = value, sortOrder = viewModel.uiState.value.triggers.size))
                input.setText("")
            }
        }
        findViewById<MaterialButton>(R.id.btnAddRecoveryHelpfulAction).setOnClickListener {
            val input = findViewById<TextInputEditText>(R.id.inputRecoveryHelpfulAction)
            val value = input.text?.toString().orEmpty().trim()
            if (value.isNotBlank()) {
                viewModel.saveHelpfulAction(RecoveryHelpfulAction(goalId = goalId, label = value, sortOrder = viewModel.uiState.value.helpfulActions.size))
                input.setText("")
            }
        }
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    triggerAdapter.submitList(state.triggers.map { RecoveryToolRow(it.id, it.label) })
                    actionAdapter.submitList(state.helpfulActions.map { RecoveryToolRow(it.id, it.label) })
                    findViewById<TextView>(R.id.tvRecoveryTriggersEmpty).isVisible = state.triggers.isEmpty()
                    findViewById<TextView>(R.id.tvRecoveryActionsEmpty).isVisible = state.helpfulActions.isEmpty()
                }
            }
        }
    }

    private fun confirmDelete(action: () -> Unit) {
        AlertDialog.Builder(this).setMessage(R.string.recovery_delete)
            .setNegativeButton(R.string.recovery_cancel, null)
            .setPositiveButton(R.string.recovery_delete) { _, _ -> action() }.show()
    }
}
