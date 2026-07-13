package com.luistureo.voicereminderapp.presentation.recovery

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.recovery.RecoveryPreferenceStore
import com.luistureo.voicereminderapp.core.wellness.WellnessAssistantCoordinator
import com.luistureo.voicereminderapp.core.wellness.WellnessAssistantPhrase
import com.luistureo.voicereminderapp.presentation.assistant.AssistantDialogueBubbleView
import com.luistureo.voicereminderapp.presentation.routine.RoutineDashboardActivity
import kotlinx.coroutines.launch

class RecoverySupportActivity : ComponentActivity() {
    private lateinit var viewModel: RecoveryViewModel
    private lateinit var configuredActions: LinearLayout
    private lateinit var bubble: AssistantDialogueBubbleView
    private lateinit var assistantCoordinator: WellnessAssistantCoordinator
    private val goalId by lazy { intent.getIntExtra(RecoveryDashboardActivity.EXTRA_GOAL_ID, 0) }
    private var rendered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (goalId <= 0) { finish(); return }
        setContentView(R.layout.activity_recovery_support)
        viewModel = ViewModelProvider(this, RecoveryViewModelFactory(applicationContext))[
            RecoveryViewModel::class.java
        ]
        configuredActions = findViewById(R.id.containerRecoveryConfiguredActions)
        bubble = findViewById(R.id.bubbleRecoverySupport)
        setupActions()
        observe()
        viewModel.load(goalId)
        val preferences = RecoveryPreferenceStore(applicationContext)
        assistantCoordinator = WellnessAssistantCoordinator(
            context = this,
            bubbleView = bubble,
            voiceEnabled = preferences.voiceEnabled(),
            bubbleEnabled = preferences.bubbleEnabled()
        )
        assistantCoordinator.present(WellnessAssistantPhrase.RECOVERY_REVIEW_SUPPORT_TOOLS)
    }

    override fun onDestroy() {
        if (::assistantCoordinator.isInitialized) assistantCoordinator.release()
        super.onDestroy()
    }

    private fun setupActions() {
        findViewById<ImageButton>(R.id.btnBackRecoverySupport).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnRecoveryRememberReasons).setOnClickListener {
            val reasons = viewModel.uiState.value.selectedGoal?.let { goal ->
                listOfNotNull(goal.personalReason, goal.motivations)
                    .filter { it.isNotBlank() }.joinToString("\n\n")
            }.orEmpty()
            AlertDialog.Builder(this).setTitle(R.string.recovery_support_reasons)
                .setMessage(reasons.ifBlank { getString(R.string.recovery_support_no_reasons) })
                .setPositiveButton(android.R.string.ok, null).show()
        }
        findViewById<MaterialButton>(R.id.btnRecoveryContactSupport).setOnClickListener {
            startActivity(Intent(this, RecoveryContactsActivity::class.java)
                .putExtra(RecoveryDashboardActivity.EXTRA_GOAL_ID, goalId))
        }
        findViewById<MaterialButton>(R.id.btnRecoveryAlternative).setOnClickListener {
            val actions = viewModel.uiState.value.helpfulActions.filter { it.enabled }
            if (actions.isEmpty()) Toast.makeText(this, R.string.recovery_support_no_action, Toast.LENGTH_SHORT).show()
            else AlertDialog.Builder(this).setTitle(R.string.recovery_support_alternative)
                .setItems(actions.map { it.label }.toTypedArray(), null).show()
        }
        findViewById<MaterialButton>(R.id.btnRecoveryWait).setOnClickListener {
            AlertDialog.Builder(this).setTitle(R.string.recovery_support_wait)
                .setMessage(R.string.recovery_support_wait_message)
                .setPositiveButton(android.R.string.ok, null).show()
        }
        findViewById<MaterialButton>(R.id.btnRecoveryOpenRoutine).setOnClickListener {
            startActivity(Intent(this, RoutineDashboardActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnRecoveryRecordFeeling).setOnClickListener {
            startActivity(Intent(this, RecoveryCheckInActivity::class.java)
                .putExtra(RecoveryDashboardActivity.EXTRA_GOAL_ID, goalId))
        }
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (rendered || state.selectedGoal?.id != goalId) return@collect
                    rendered = true
                    configuredActions.removeAllViews()
                    state.helpfulActions.filter { it.enabled }.forEach { action ->
                        configuredActions.addView(MaterialButton(this@RecoverySupportActivity).apply {
                            text = action.label
                            minHeight = (48 * resources.displayMetrics.density).toInt()
                            setOnClickListener {
                                AlertDialog.Builder(this@RecoverySupportActivity)
                                    .setTitle(action.label)
                                    .setMessage(R.string.recovery_tools_no_guarantee)
                                    .setPositiveButton(android.R.string.ok, null).show()
                            }
                        })
                    }
                }
            }
        }
    }
}
