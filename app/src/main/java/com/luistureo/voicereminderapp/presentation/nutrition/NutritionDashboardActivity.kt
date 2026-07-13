package com.luistureo.voicereminderapp.presentation.nutrition

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ProgressBar
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
import com.luistureo.voicereminderapp.core.wellness.WellnessAssistantCoordinator
import com.luistureo.voicereminderapp.core.wellness.WellnessAssistantPhrase
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMealStatus
import com.luistureo.voicereminderapp.presentation.nutrition.adapter.NutritionMealAdapter
import com.luistureo.voicereminderapp.presentation.nutrition.viewmodel.NutritionViewModel
import com.luistureo.voicereminderapp.presentation.nutrition.viewmodel.NutritionViewModelFactory
import com.luistureo.voicereminderapp.presentation.assistant.AssistantDialogueBubbleView
import kotlinx.coroutines.launch

class NutritionDashboardActivity : ComponentActivity() {
    private lateinit var viewModel: NutritionViewModel
    private lateinit var adapter: NutritionMealAdapter
    private lateinit var emptyText: TextView
    private lateinit var summaryText: TextView
    private lateinit var reminderText: TextView
    private lateinit var hydrationText: TextView
    private lateinit var hydrationProgress: ProgressBar
    private lateinit var assistantCoordinator: WellnessAssistantCoordinator
    private var assistantPresented = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nutrition_dashboard)
        viewModel = ViewModelProvider(
            this,
            NutritionViewModelFactory(applicationContext)
        )[NutritionViewModel::class.java]
        setupViews()
        assistantCoordinator = WellnessAssistantCoordinator(
            context = this,
            bubbleView = findViewById<AssistantDialogueBubbleView>(R.id.nutritionAssistantBubble)
        )
        observeState()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadDashboard()
    }

    private fun setupViews() {
        findViewById<ImageButton>(R.id.btnBackNutritionDashboard).setOnClickListener { finish() }
        emptyText = findViewById(R.id.tvNutritionDashboardEmpty)
        summaryText = findViewById(R.id.tvNutritionDashboardSummary)
        reminderText = findViewById(R.id.tvNutritionPendingReminders)
        hydrationText = findViewById(R.id.tvNutritionHydrationSummary)
        hydrationProgress = findViewById(R.id.progressNutritionHydration)
        adapter = NutritionMealAdapter(
            compact = true,
            onEdit = { item -> openMealEditor(item.meal.id, item.date.toEpochDay()) },
            onComplete = { item -> viewModel.setMealStatus(item, NutritionMealStatus.COMPLETED) },
            onSkip = { item -> viewModel.setMealStatus(item, NutritionMealStatus.SKIPPED) }
        )
        findViewById<RecyclerView>(R.id.recyclerNutritionTodayMeals).apply {
            layoutManager = LinearLayoutManager(this@NutritionDashboardActivity)
            adapter = this@NutritionDashboardActivity.adapter
        }
        findViewById<MaterialButton>(R.id.btnNutritionQuickAdd).setOnClickListener {
            openMealEditor(0, java.time.LocalDate.now().toEpochDay())
        }
        bindNavigation(R.id.btnNutritionPlanning, NutritionPlanningActivity::class.java)
        bindNavigation(R.id.btnNutritionHydration, NutritionHydrationActivity::class.java)
        bindNavigation(R.id.btnNutritionShopping, NutritionShoppingActivity::class.java)
        bindNavigation(R.id.btnNutritionTemplates, NutritionTemplatesActivity::class.java)
        bindNavigation(R.id.btnNutritionPreferences, NutritionPreferencesActivity::class.java)
        bindNavigation(R.id.btnNutritionStatistics, NutritionStatisticsActivity::class.java)
    }

    private fun bindNavigation(id: Int, target: Class<out ComponentActivity>) {
        findViewById<MaterialButton>(id).setOnClickListener {
            startActivity(Intent(this, target))
        }
    }

    private fun openMealEditor(mealId: Int, dateEpochDay: Long) {
        startActivity(Intent(this, NutritionMealEditorActivity::class.java).apply {
            putExtra(NutritionMealEditorActivity.EXTRA_MEAL_ID, mealId)
            putExtra(NutritionMealEditorActivity.EXTRA_DATE_EPOCH_DAY, dateEpochDay)
        })
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    assistantCoordinator.updateOutputSettings(
                        voiceEnabled = state.preferences.assistantVoiceEnabled,
                        bubbleEnabled = state.preferences.temporaryBubbleEnabled
                    )
                    if (
                        !assistantPresented &&
                        (state.preferences.assistantVoiceEnabled || state.preferences.temporaryBubbleEnabled)
                    ) {
                        assistantPresented = true
                        assistantCoordinator.present(WellnessAssistantPhrase.NUTRITION_ADJUST_PLAN)
                    }
                    adapter.submitList(state.mealItems)
                    emptyText.isVisible = state.mealItems.isEmpty()
                    summaryText.text = getString(
                        R.string.nutrition_dashboard_meal_summary,
                        state.dashboardSummary.plannedMeals,
                        state.dashboardSummary.completedMeals
                    )
                    reminderText.text = getString(
                        R.string.nutrition_dashboard_pending_reminders,
                        state.dashboardSummary.pendingReminders
                    )
                    hydrationText.text = if (!state.preferences.hydrationEnabled) {
                        getString(R.string.nutrition_hydration_disabled)
                    } else {
                        getString(
                            R.string.nutrition_dashboard_hydration_summary,
                            state.dashboardSummary.hydrationMl,
                            state.dashboardSummary.hydrationTargetMl
                        )
                    }
                    hydrationProgress.max = state.dashboardSummary.hydrationTargetMl.coerceAtLeast(1)
                    hydrationProgress.progress = state.dashboardSummary.hydrationMl
                        .coerceAtMost(hydrationProgress.max)
                    state.message?.let { message ->
                        Toast.makeText(this@NutritionDashboardActivity, message, Toast.LENGTH_SHORT).show()
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
}
