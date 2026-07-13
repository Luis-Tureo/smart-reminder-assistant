package com.luistureo.voicereminderapp.presentation.nutrition

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
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
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionTemplate
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionTemplateMeal
import com.luistureo.voicereminderapp.presentation.nutrition.adapter.NutritionTemplatePreviewAdapter
import com.luistureo.voicereminderapp.presentation.nutrition.viewmodel.NutritionViewModel
import com.luistureo.voicereminderapp.presentation.nutrition.viewmodel.NutritionViewModelFactory
import java.time.LocalDate
import kotlinx.coroutines.launch

class NutritionTemplatePreviewActivity : ComponentActivity() {
    private lateinit var viewModel: NutritionViewModel
    private lateinit var adapter: NutritionTemplatePreviewAdapter
    private lateinit var nameText: TextView
    private lateinit var detailText: TextView
    private lateinit var dateButton: MaterialButton
    private var template: NutritionTemplate? = null
    private var editableMeals = emptyList<NutritionTemplateMeal>()
    private var selectedDate = LocalDate.now()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nutrition_template_preview)
        viewModel = ViewModelProvider(this, NutritionViewModelFactory(applicationContext))[
            NutritionViewModel::class.java
        ]
        setupViews()
        observeState()
        viewModel.loadTemplate(intent.getIntExtra(EXTRA_TEMPLATE_ID, 0))
    }

    private fun setupViews() {
        findViewById<ImageButton>(R.id.btnBackNutritionTemplatePreview).setOnClickListener { finish() }
        nameText = findViewById(R.id.tvNutritionTemplatePreviewName)
        detailText = findViewById(R.id.tvNutritionTemplatePreviewDetail)
        dateButton = findViewById(R.id.btnNutritionTemplateDate)
        adapter = NutritionTemplatePreviewAdapter(::editMeal) { meal ->
            editableMeals = editableMeals.filterNot { it.id == meal.id && it.orderPriority == meal.orderPriority }
            adapter.submitList(editableMeals)
        }
        findViewById<RecyclerView>(R.id.recyclerNutritionTemplatePreview).apply {
            layoutManager = LinearLayoutManager(this@NutritionTemplatePreviewActivity)
            adapter = this@NutritionTemplatePreviewActivity.adapter
        }
        dateButton.setOnClickListener {
            DatePickerDialog(this, { _, year, month, day ->
                selectedDate = LocalDate.of(year, month + 1, day)
                dateButton.text = NutritionUiFormatter.date(selectedDate)
            }, selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth).show()
        }
        dateButton.text = NutritionUiFormatter.date(selectedDate)
        findViewById<MaterialButton>(R.id.btnApplyNutritionTemplate).setOnClickListener {
            val current = template ?: return@setOnClickListener
            viewModel.applyTemplate(current.copy(meals = editableMeals), selectedDate) { finish() }
        }
    }

    private fun editMeal(meal: NutritionTemplateMeal) {
        val input = EditText(this).apply { setText(meal.name); selectAll() }
        AlertDialog.Builder(this)
            .setTitle(R.string.nutrition_edit_template_meal)
            .setView(input)
            .setNegativeButton(R.string.nutrition_cancel, null)
            .setPositiveButton(R.string.nutrition_save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotBlank()) {
                    editableMeals = editableMeals.map {
                        if (it.id == meal.id && it.orderPriority == meal.orderPriority) it.copy(name = name) else it
                    }
                    adapter.submitList(editableMeals)
                }
            }.show()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    state.selectedTemplate?.let { value ->
                        if (template?.id != value.id) {
                            template = value
                            editableMeals = value.meals
                            nameText.text = value.name
                            detailText.text = getString(
                                R.string.nutrition_template_preview_detail,
                                value.description,
                                value.preparationComplexity,
                                value.practicalBenefits
                            )
                            adapter.submitList(editableMeals)
                        }
                    }
                    state.message?.let {
                        Toast.makeText(this@NutritionTemplatePreviewActivity, it, Toast.LENGTH_SHORT).show()
                        viewModel.consumeMessage()
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_TEMPLATE_ID = "extra_nutrition_template_id"
    }
}

