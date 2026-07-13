package com.luistureo.voicereminderapp.presentation.nutrition

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionChartRange
import com.luistureo.voicereminderapp.presentation.nutrition.viewmodel.NutritionViewModel
import com.luistureo.voicereminderapp.presentation.nutrition.viewmodel.NutritionViewModelFactory
import kotlinx.coroutines.launch

class NutritionStatisticsActivity : ComponentActivity() {
    private lateinit var viewModel: NutritionViewModel
    private lateinit var chart: NutritionChartView
    private lateinit var summary: TextView
    private lateinit var hydration: TextView
    private lateinit var shopping: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nutrition_statistics)
        viewModel = ViewModelProvider(this, NutritionViewModelFactory(applicationContext))[
            NutritionViewModel::class.java
        ]
        findViewById<ImageButton>(R.id.btnBackNutritionStatistics).setOnClickListener { finish() }
        chart = findViewById(R.id.chartNutritionStatistics)
        summary = findViewById(R.id.tvNutritionStatisticsMeals)
        hydration = findViewById(R.id.tvNutritionStatisticsHydration)
        shopping = findViewById(R.id.tvNutritionStatisticsShopping)
        findViewById<Spinner>(R.id.spinnerNutritionStatisticsRange).apply {
            adapter = ArrayAdapter(
                this@NutritionStatisticsActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf(
                    getString(R.string.nutrition_range_day),
                    getString(R.string.nutrition_range_week),
                    getString(R.string.nutrition_range_month)
                )
            )
            onItemSelectedListener = SimpleItemSelectedListener { position ->
                viewModel.loadStatistics(NutritionChartRange.entries[position])
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val value = state.statistics
                    chart.setStatistics(value)
                    if (value != null) {
                        summary.text = getString(
                            R.string.nutrition_statistics_meals,
                            value.completedMeals,
                            value.plannedMeals,
                            value.completionPercentage,
                            value.skippedMeals
                        )
                        hydration.text = getString(R.string.nutrition_statistics_hydration, value.hydrationMl)
                        shopping.text = getString(
                            R.string.nutrition_statistics_shopping,
                            value.shoppingItemsCompleted
                        )
                    }
                    state.message?.let {
                        Toast.makeText(this@NutritionStatisticsActivity, it, Toast.LENGTH_SHORT).show()
                        viewModel.consumeMessage()
                    }
                }
            }
        }
    }
}

