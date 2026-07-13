package com.luistureo.voicereminderapp.presentation.nutrition

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMealStatus
import com.luistureo.voicereminderapp.presentation.nutrition.adapter.NutritionMealAdapter
import com.luistureo.voicereminderapp.presentation.nutrition.state.NutritionMealListItem
import com.luistureo.voicereminderapp.presentation.nutrition.viewmodel.NutritionViewModel
import com.luistureo.voicereminderapp.presentation.nutrition.viewmodel.NutritionViewModelFactory
import java.time.LocalDate
import kotlinx.coroutines.launch

class NutritionPlanningActivity : ComponentActivity() {
    private lateinit var viewModel: NutritionViewModel
    private lateinit var adapter: NutritionMealAdapter
    private lateinit var modeSpinner: Spinner
    private lateinit var dateText: TextView
    private lateinit var emptyText: TextView
    private var selectedDate: LocalDate = LocalDate.now()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nutrition_planning)
        viewModel = ViewModelProvider(this, NutritionViewModelFactory(applicationContext))[
            NutritionViewModel::class.java
        ]
        setupViews()
        observeState()
        load()
    }

    private fun setupViews() {
        findViewById<ImageButton>(R.id.btnBackNutritionPlanning).setOnClickListener { finish() }
        dateText = findViewById(R.id.tvNutritionPlanningDate)
        emptyText = findViewById(R.id.tvNutritionPlanningEmpty)
        modeSpinner = findViewById<Spinner>(R.id.spinnerNutritionPlanningMode).apply {
            adapter = ArrayAdapter(
                this@NutritionPlanningActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf(getString(R.string.nutrition_daily_view), getString(R.string.nutrition_weekly_view))
            )
            onItemSelectedListener = SimpleItemSelectedListener { load() }
        }
        adapter = NutritionMealAdapter(
            onEdit = ::openEditor,
            onDuplicate = viewModel::duplicateMeal,
            onMove = { item -> selectDate { date -> viewModel.moveMeal(item, date) } },
            onComplete = { viewModel.setMealStatus(it, NutritionMealStatus.COMPLETED) },
            onSkip = { viewModel.setMealStatus(it, NutritionMealStatus.SKIPPED) }
        )
        findViewById<RecyclerView>(R.id.recyclerNutritionPlanning).apply {
            layoutManager = LinearLayoutManager(this@NutritionPlanningActivity)
            adapter = this@NutritionPlanningActivity.adapter
        }
        findViewById<MaterialButton>(R.id.btnNutritionPlanningPrevious).setOnClickListener {
            selectedDate = selectedDate.minusDays(if (isWeekly()) 7 else 1)
            load()
        }
        findViewById<MaterialButton>(R.id.btnNutritionPlanningNext).setOnClickListener {
            selectedDate = selectedDate.plusDays(if (isWeekly()) 7 else 1)
            load()
        }
        dateText.setOnClickListener { selectDate { selectedDate = it; load() } }
        findViewById<MaterialButton>(R.id.btnNutritionPlanningAdd).setOnClickListener {
            openEditor(NutritionMealListItem(selectedDate, com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMeal(
                name = "", period = com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMealPeriod.BREAKFAST
            )))
        }
        findViewById<MaterialButton>(R.id.btnNutritionCopyDay).setOnClickListener {
            selectDate { target ->
                AlertDialog.Builder(this)
                    .setTitle(R.string.nutrition_copy_day)
                    .setMessage(getString(R.string.nutrition_copy_day_confirmation, NutritionUiFormatter.date(target)))
                    .setNegativeButton(R.string.nutrition_cancel, null)
                    .setPositiveButton(R.string.nutrition_copy) { _, _ ->
                        viewModel.copyDay(selectedDate, target)
                    }.show()
            }
        }
    }

    private fun openEditor(item: NutritionMealListItem) {
        startActivity(Intent(this, NutritionMealEditorActivity::class.java).apply {
            putExtra(NutritionMealEditorActivity.EXTRA_MEAL_ID, item.meal.id)
            putExtra(NutritionMealEditorActivity.EXTRA_DATE_EPOCH_DAY, item.date.toEpochDay())
        })
    }

    private fun load() {
        dateText.text = if (isWeekly()) {
            getString(
                R.string.nutrition_week_range,
                NutritionUiFormatter.date(selectedDate),
                NutritionUiFormatter.date(selectedDate.plusDays(6))
            )
        } else NutritionUiFormatter.date(selectedDate)
        viewModel.loadPlanning(selectedDate, isWeekly())
    }

    private fun isWeekly() = modeSpinner.selectedItemPosition == 1

    private fun selectDate(onSelected: (LocalDate) -> Unit) {
        DatePickerDialog(
            this,
            { _, year, month, day -> onSelected(LocalDate.of(year, month + 1, day)) },
            selectedDate.year,
            selectedDate.monthValue - 1,
            selectedDate.dayOfMonth
        ).show()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(state.mealItems)
                    emptyText.isVisible = state.mealItems.isEmpty()
                    state.message?.let {
                        Toast.makeText(this@NutritionPlanningActivity, it, Toast.LENGTH_SHORT).show()
                        viewModel.consumeMessage()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        load()
    }
}

