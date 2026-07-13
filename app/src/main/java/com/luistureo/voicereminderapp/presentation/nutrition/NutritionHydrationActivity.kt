package com.luistureo.voicereminderapp.presentation.nutrition

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.presentation.nutrition.adapter.NutritionHydrationAdapter
import com.luistureo.voicereminderapp.presentation.nutrition.viewmodel.NutritionViewModel
import com.luistureo.voicereminderapp.presentation.nutrition.viewmodel.NutritionViewModelFactory
import kotlinx.coroutines.launch

class NutritionHydrationActivity : ComponentActivity() {
    private lateinit var viewModel: NutritionViewModel
    private lateinit var adapter: NutritionHydrationAdapter
    private lateinit var enabledCheck: MaterialCheckBox
    private lateinit var targetInput: TextInputEditText
    private lateinit var containerInput: TextInputEditText
    private lateinit var intervalInput: TextInputEditText
    private lateinit var customAmountInput: TextInputEditText
    private lateinit var startButton: MaterialButton
    private lateinit var endButton: MaterialButton
    private lateinit var totalText: TextView
    private lateinit var progress: ProgressBar
    private var startMinutes: Int? = null
    private var endMinutes: Int? = null
    private var bound = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nutrition_hydration)
        viewModel = ViewModelProvider(this, NutritionViewModelFactory(applicationContext))[
            NutritionViewModel::class.java
        ]
        setupViews()
        observeState()
        viewModel.loadHydration()
    }

    private fun setupViews() {
        findViewById<ImageButton>(R.id.btnBackNutritionHydration).setOnClickListener { finish() }
        enabledCheck = findViewById(R.id.checkNutritionHydrationEnabled)
        targetInput = findViewById(R.id.inputNutritionHydrationTarget)
        containerInput = findViewById(R.id.inputNutritionHydrationContainer)
        intervalInput = findViewById(R.id.inputNutritionHydrationInterval)
        customAmountInput = findViewById(R.id.inputNutritionHydrationCustom)
        startButton = findViewById(R.id.btnNutritionHydrationStart)
        endButton = findViewById(R.id.btnNutritionHydrationEnd)
        totalText = findViewById(R.id.tvNutritionHydrationTotal)
        progress = findViewById(R.id.progressNutritionHydrationDetail)
        adapter = NutritionHydrationAdapter { viewModel.deleteHydrationEntry(it.id) }
        findViewById<RecyclerView>(R.id.recyclerNutritionHydration).apply {
            layoutManager = LinearLayoutManager(this@NutritionHydrationActivity)
            adapter = this@NutritionHydrationActivity.adapter
        }
        startButton.setOnClickListener { selectTime(startMinutes ?: 8 * 60) { startMinutes = it; refreshTimes() } }
        endButton.setOnClickListener { selectTime(endMinutes ?: 20 * 60) { endMinutes = it; refreshTimes() } }
        findViewById<MaterialButton>(R.id.btnNutritionHydrationAddContainer).setOnClickListener {
            viewModel.addHydration(containerInput.text?.toString()?.toIntOrNull() ?: 250)
        }
        findViewById<MaterialButton>(R.id.btnNutritionHydrationAdd250).setOnClickListener {
            viewModel.addHydration(250)
        }
        findViewById<MaterialButton>(R.id.btnNutritionHydrationAdd500).setOnClickListener {
            viewModel.addHydration(500)
        }
        findViewById<MaterialButton>(R.id.btnNutritionHydrationAddCustom).setOnClickListener {
            viewModel.addHydration(customAmountInput.text?.toString()?.toIntOrNull() ?: 0)
        }
        findViewById<MaterialButton>(R.id.btnSaveNutritionHydrationSettings).setOnClickListener {
            val current = viewModel.uiState.value.preferences
            viewModel.saveHydrationSettings(
                current.copy(
                    hydrationEnabled = enabledCheck.isChecked,
                    hydrationTargetMl = targetInput.text?.toString()?.toIntOrNull()?.coerceAtLeast(0) ?: 0,
                    hydrationContainerMl = containerInput.text?.toString()?.toIntOrNull()?.coerceIn(1, 10_000) ?: 250,
                    hydrationReminderStartMinutes = startMinutes,
                    hydrationReminderEndMinutes = endMinutes,
                    hydrationReminderIntervalMinutes = intervalInput.text?.toString()?.toIntOrNull()
                )
            )
        }
    }

    private fun bindPreferences(
        value: com.luistureo.voicereminderapp.domain.nutrition.model.NutritionPreferences
    ) {
        if (bound) return
        bound = true
        enabledCheck.isChecked = value.hydrationEnabled
        targetInput.setText(value.hydrationTargetMl.takeIf { it > 0 }?.toString().orEmpty())
        containerInput.setText(value.hydrationContainerMl.toString())
        intervalInput.setText(value.hydrationReminderIntervalMinutes?.toString().orEmpty())
        startMinutes = value.hydrationReminderStartMinutes
        endMinutes = value.hydrationReminderEndMinutes
        refreshTimes()
    }

    private fun refreshTimes() {
        startButton.text = formatMinutes(startMinutes, R.string.nutrition_hydration_start)
        endButton.text = formatMinutes(endMinutes, R.string.nutrition_hydration_end)
    }

    private fun formatMinutes(value: Int?, fallback: Int): String = value?.let {
        String.format(java.util.Locale.ROOT, "%02d:%02d", it / 60, it % 60)
    } ?: getString(fallback)

    private fun selectTime(initial: Int, onSelected: (Int) -> Unit) {
        TimePickerDialog(this, { _, hour, minute -> onSelected(hour * 60 + minute) },
            initial / 60, initial % 60, true).show()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.preferencesLoaded) bindPreferences(state.preferences)
                    adapter.submitList(state.hydrationEntries)
                    val total = state.hydrationEntries.sumOf { it.amountMl }
                    totalText.text = getString(
                        R.string.nutrition_hydration_progress,
                        total,
                        state.preferences.hydrationTargetMl
                    )
                    progress.max = state.preferences.hydrationTargetMl.coerceAtLeast(1)
                    progress.progress = total.coerceAtMost(progress.max)
                    state.message?.let {
                        Toast.makeText(this@NutritionHydrationActivity, it, Toast.LENGTH_SHORT).show()
                        viewModel.consumeMessage()
                    }
                }
            }
        }
    }
}
