package com.luistureo.voicereminderapp.presentation.recovery

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
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
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.recovery.RecoveryPreferenceStore
import com.luistureo.voicereminderapp.core.wellness.WellnessAssistantCoordinator
import com.luistureo.voicereminderapp.core.wellness.WellnessAssistantPhrase
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryCheckInStatus
import com.luistureo.voicereminderapp.presentation.recovery.adapter.RecoveryCheckInAdapter
import com.luistureo.voicereminderapp.presentation.assistant.AssistantDialogueBubbleView
import java.time.LocalDate
import kotlinx.coroutines.launch

class RecoveryDashboardActivity : ComponentActivity() {
    private lateinit var viewModel: RecoveryViewModel
    private lateinit var goalsSpinner: Spinner
    private lateinit var recentAdapter: RecoveryCheckInAdapter
    private lateinit var noGoals: TextView
    private lateinit var content: View
    private lateinit var assistantCoordinator: WellnessAssistantCoordinator
    private lateinit var preferenceStore: RecoveryPreferenceStore
    private var renderingGoals = false
    private var assistantPresented = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recovery_dashboard)
        viewModel = ViewModelProvider(this, RecoveryViewModelFactory(applicationContext))[
            RecoveryViewModel::class.java
        ]
        preferenceStore = RecoveryPreferenceStore(applicationContext)
        setupViews()
        assistantCoordinator = WellnessAssistantCoordinator(
            context = this,
            bubbleView = findViewById<AssistantDialogueBubbleView>(R.id.recoveryAssistantBubble)
        )
        observe()
    }

    override fun onResume() {
        super.onResume()
        findViewById<View>(R.id.btnRecoveryStatistics).isVisible =
            RecoveryPreferenceStore(applicationContext).statisticsEnabled()
        viewModel.load(intent.getIntExtra(EXTRA_GOAL_ID, 0))
    }

    private fun setupViews() {
        findViewById<ImageButton>(R.id.btnBackRecoveryDashboard).setOnClickListener { finish() }
        noGoals = findViewById(R.id.tvRecoveryNoGoals)
        content = findViewById(R.id.containerRecoveryDashboardContent)
        goalsSpinner = findViewById(R.id.spinnerRecoveryGoals)
        recentAdapter = RecoveryCheckInAdapter()
        findViewById<RecyclerView>(R.id.recyclerRecoveryRecent).apply {
            layoutManager = LinearLayoutManager(this@RecoveryDashboardActivity)
            adapter = recentAdapter
        }
        goalsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!renderingGoals) viewModel.uiState.value.goals.getOrNull(position)?.let {
                    if (it.id != viewModel.uiState.value.selectedGoal?.id) viewModel.selectGoal(it.id)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        findViewById<MaterialButton>(R.id.btnRecoveryNewGoal).setOnClickListener {
            startActivity(Intent(this, RecoveryGoalEditorActivity::class.java))
        }
        bindGoalButton(R.id.btnRecoveryCheckIn, RecoveryCheckInActivity::class.java)
        bindGoalButton(R.id.btnRecoverySupportNow, RecoverySupportActivity::class.java)
        bindGoalButton(R.id.btnRecoveryTools, RecoveryToolsActivity::class.java)
        bindGoalButton(R.id.btnRecoveryContacts, RecoveryContactsActivity::class.java)
        bindGoalButton(R.id.btnRecoveryStatistics, RecoveryStatisticsActivity::class.java)
        bindGoalButton(R.id.btnRecoverySettings, RecoverySettingsActivity::class.java)
    }

    private fun bindGoalButton(id: Int, target: Class<*>) {
        findViewById<View>(id).setOnClickListener {
            val goalId = viewModel.uiState.value.selectedGoal?.id ?: return@setOnClickListener
            startActivity(Intent(this, target).putExtra(EXTRA_GOAL_ID, goalId))
        }
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    assistantCoordinator.updateOutputSettings(
                        voiceEnabled = preferenceStore.voiceEnabled(),
                        bubbleEnabled = preferenceStore.bubbleEnabled()
                    )
                    if (
                        !assistantPresented &&
                        state.selectedGoal != null &&
                        (preferenceStore.voiceEnabled() || preferenceStore.bubbleEnabled())
                    ) {
                        assistantPresented = true
                        assistantCoordinator.present(
                            WellnessAssistantPhrase.RECOVERY_REVIEW_SUPPORT_TOOLS
                        )
                    }
                    val goal = state.selectedGoal
                    noGoals.isVisible = !state.loading && state.goals.isEmpty()
                    content.isVisible = goal != null
                    renderingGoals = true
                    goalsSpinner.adapter = ArrayAdapter(
                        this@RecoveryDashboardActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        state.goals.map { it.title }
                    )
                    goalsSpinner.setSelection(state.goals.indexOfFirst { it.id == goal?.id }.coerceAtLeast(0))
                    renderingGoals = false
                    state.dashboard?.let { dashboard ->
                        val days = dashboard.goal.startDate?.let {
                            java.time.temporal.ChronoUnit.DAYS.between(it, LocalDate.now()).coerceAtLeast(0)
                        }
                        findViewById<TextView>(R.id.tvRecoveryDaysSince).text = days?.let {
                            getString(R.string.recovery_dashboard_days_since, it)
                        } ?: getString(R.string.recovery_dashboard_no_start_date)
                        findViewById<TextView>(R.id.tvRecoveryCurrentStreak).text =
                            "${getString(R.string.recovery_dashboard_current_streak)}\n${dashboard.statistics.currentStreak}"
                        findViewById<TextView>(R.id.tvRecoveryBestStreak).text =
                            "${getString(R.string.recovery_dashboard_best_streak)}\n${dashboard.statistics.bestStreak}"
                        findViewById<TextView>(R.id.tvRecoveryDashboardSummary).text = listOf(
                            "${getString(R.string.recovery_dashboard_success_days)}: ${dashboard.statistics.successfulDays}",
                            "${getString(R.string.recovery_dashboard_reduced_days)}: ${dashboard.statistics.reducedFrequencyDays}",
                            "${getString(R.string.recovery_dashboard_checkins)}: ${dashboard.statistics.checkIns}",
                            "${getString(R.string.recovery_dashboard_helpful_actions)}: ${dashboard.statistics.helpfulActionsUsed}"
                        ).joinToString("\n")
                        findViewById<TextView>(R.id.tvRecoveryToday).text = dashboard.todayCheckIn?.let {
                            when (it.status) {
                                RecoveryCheckInStatus.ACHIEVED -> getString(R.string.recovery_checkin_success)
                                RecoveryCheckInStatus.DIFFICULTY_MANAGED -> getString(R.string.recovery_checkin_managed)
                                RecoveryCheckInStatus.LAPSE -> getString(R.string.recovery_lapse_support)
                                RecoveryCheckInStatus.PREFER_NOT_TO_REGISTER -> getString(R.string.recovery_checkin_skip)
                            }
                        } ?: getString(R.string.recovery_dashboard_no_checkin)
                        recentAdapter.submitList(dashboard.recentCheckIns)
                        findViewById<TextView>(R.id.tvRecoveryNoRecent).isVisible =
                            dashboard.recentCheckIns.isEmpty()
                    }
                    state.messageRes?.let {
                        Toast.makeText(this@RecoveryDashboardActivity, it, Toast.LENGTH_SHORT).show()
                        viewModel.consumeMessage()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (::assistantCoordinator.isInitialized) assistantCoordinator.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_GOAL_ID = "extra_recovery_goal_id"
    }
}
