package com.luistureo.voicereminderapp.presentation.nutrition

import android.os.Bundle
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionChartType
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionDietaryStyle
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionGoal
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMealPeriod
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionPreferences
import com.luistureo.voicereminderapp.presentation.nutrition.viewmodel.NutritionViewModel
import com.luistureo.voicereminderapp.presentation.nutrition.viewmodel.NutritionViewModelFactory
import kotlinx.coroutines.launch

class NutritionPreferencesActivity : ComponentActivity() {
    private lateinit var viewModel: NutritionViewModel
    private lateinit var styleSpinner: Spinner
    private lateinit var chartSpinner: Spinner
    private lateinit var dislikes: TextInputEditText
    private lateinit var exclusions: TextInputEditText
    private lateinit var allergies: TextInputEditText
    private lateinit var preferred: TextInputEditText
    private lateinit var allergyWarning: TextView
    private lateinit var reminders: MaterialCheckBox
    private lateinit var voice: MaterialCheckBox
    private lateinit var bubble: MaterialCheckBox
    private lateinit var privacy: MaterialCheckBox
    private val periodChecks = mutableMapOf<NutritionMealPeriod, MaterialCheckBox>()
    private val goalChecks = mutableMapOf<NutritionGoal, MaterialCheckBox>()
    private var bound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nutrition_preferences)
        viewModel = ViewModelProvider(this, NutritionViewModelFactory(applicationContext))[
            NutritionViewModel::class.java
        ]
        setupViews()
        observeState()
        viewModel.loadPreferences()
    }

    private fun setupViews() {
        findViewById<ImageButton>(R.id.btnBackNutritionPreferences).setOnClickListener { finish() }
        styleSpinner = findViewById(R.id.spinnerNutritionDietaryStyle)
        chartSpinner = findViewById(R.id.spinnerNutritionChartType)
        dislikes = findViewById(R.id.inputNutritionDislikes)
        exclusions = findViewById(R.id.inputNutritionExclusions)
        allergies = findViewById(R.id.inputNutritionAllergies)
        preferred = findViewById(R.id.inputNutritionPreferredFoods)
        allergyWarning = findViewById(R.id.tvNutritionAllergyWarning)
        reminders = findViewById(R.id.checkNutritionRemindersEnabled)
        voice = findViewById(R.id.checkNutritionVoiceEnabled)
        bubble = findViewById(R.id.checkNutritionBubbleEnabled)
        privacy = findViewById(R.id.checkNutritionPrivacyEnabled)
        periodChecks[NutritionMealPeriod.BREAKFAST] = findViewById(R.id.checkNutritionBreakfast)
        periodChecks[NutritionMealPeriod.MORNING_SNACK] = findViewById(R.id.checkNutritionMorningSnack)
        periodChecks[NutritionMealPeriod.LUNCH] = findViewById(R.id.checkNutritionLunch)
        periodChecks[NutritionMealPeriod.AFTERNOON_SNACK] = findViewById(R.id.checkNutritionAfternoonSnack)
        periodChecks[NutritionMealPeriod.DINNER] = findViewById(R.id.checkNutritionDinner)
        goalChecks[NutritionGoal.ORGANIZE_MEAL_TIMES] = findViewById(R.id.checkNutritionGoalMealTimes)
        goalChecks[NutritionGoal.REMEMBER_MEALS] = findViewById(R.id.checkNutritionGoalMealReminders)
        goalChecks[NutritionGoal.IMPROVE_HYDRATION] = findViewById(R.id.checkNutritionGoalHydration)
        goalChecks[NutritionGoal.PREPARE_AHEAD] = findViewById(R.id.checkNutritionGoalPreparation)
        goalChecks[NutritionGoal.INCREASE_VARIETY] = findViewById(R.id.checkNutritionGoalVariety)
        goalChecks[NutritionGoal.REDUCE_FORGOTTEN_MEALS] = findViewById(R.id.checkNutritionGoalReduceForgetfulness)
        goalChecks[NutritionGoal.PLAN_SHOPPING] = findViewById(R.id.checkNutritionGoalShopping)
        styleSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf(
                getString(R.string.nutrition_style_none),
                getString(R.string.nutrition_style_vegetarian),
                getString(R.string.nutrition_style_vegan)
            )
        )
        chartSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf(getString(R.string.nutrition_chart_bars), getString(R.string.nutrition_chart_percentages))
        )
        allergies.setOnFocusChangeListener { _, _ ->
            allergyWarning.isVisible = !allergies.text.isNullOrBlank()
        }
        findViewById<MaterialButton>(R.id.btnSaveNutritionPreferences).setOnClickListener {
            val current = viewModel.uiState.value.preferences
            val value = current.copy(
                dietaryStyle = NutritionDietaryStyle.entries[styleSpinner.selectedItemPosition],
                dislikes = parseList(dislikes.text?.toString()),
                exclusions = parseList(exclusions.text?.toString()),
                allergiesOrIntolerances = parseList(allergies.text?.toString()),
                preferredFoods = parseList(preferred.text?.toString()),
                organizationalGoals = goalChecks.filterValues { it.isChecked }.keys,
                enabledMealPeriods = periodChecks.filterValues { it.isChecked }.keys,
                remindersEnabled = reminders.isChecked,
                assistantVoiceEnabled = voice.isChecked,
                temporaryBubbleEnabled = bubble.isChecked,
                preferredChartType = NutritionChartType.entries[chartSpinner.selectedItemPosition],
                privacyModeEnabled = privacy.isChecked
            )
            viewModel.savePreferences(value) { finish() }
        }
    }

    private fun bind(value: NutritionPreferences) {
        if (bound) return
        bound = true
        styleSpinner.setSelection(value.dietaryStyle.ordinal)
        chartSpinner.setSelection(value.preferredChartType.ordinal)
        dislikes.setText(value.dislikes.joinToString(", "))
        exclusions.setText(value.exclusions.joinToString(", "))
        allergies.setText(value.allergiesOrIntolerances.joinToString(", "))
        preferred.setText(value.preferredFoods.joinToString(", "))
        allergyWarning.isVisible = value.allergiesOrIntolerances.isNotEmpty()
        periodChecks.forEach { (period, check) -> check.isChecked = period in value.enabledMealPeriods }
        goalChecks.forEach { (goal, check) -> check.isChecked = goal in value.organizationalGoals }
        reminders.isChecked = value.remindersEnabled
        voice.isChecked = value.assistantVoiceEnabled
        bubble.isChecked = value.temporaryBubbleEnabled
        privacy.isChecked = value.privacyModeEnabled
    }

    private fun parseList(value: String?): List<String> = value.orEmpty()
        .split(',', ';', '\n').map(String::trim).filter(String::isNotBlank)

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.preferencesLoaded) bind(state.preferences)
                    state.message?.let {
                        Toast.makeText(this@NutritionPreferencesActivity, it, Toast.LENGTH_SHORT).show()
                        viewModel.consumeMessage()
                    }
                }
            }
        }
    }
}
