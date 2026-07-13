package com.luistureo.voicereminderapp.presentation.nutrition

import android.app.AlertDialog
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
import com.google.android.material.textfield.TextInputEditText
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.presentation.nutrition.adapter.NutritionShoppingAdapter
import com.luistureo.voicereminderapp.presentation.nutrition.viewmodel.NutritionViewModel
import com.luistureo.voicereminderapp.presentation.nutrition.viewmodel.NutritionViewModelFactory
import kotlinx.coroutines.launch

class NutritionShoppingActivity : ComponentActivity() {
    private lateinit var viewModel: NutritionViewModel
    private lateinit var adapter: NutritionShoppingAdapter
    private lateinit var nameInput: TextInputEditText
    private lateinit var quantityInput: TextInputEditText
    private lateinit var categorySpinner: Spinner
    private lateinit var emptyText: TextView
    private var duplicateDialogVisible = false
    private var lastRequestedQuantity: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nutrition_shopping)
        viewModel = ViewModelProvider(this, NutritionViewModelFactory(applicationContext))[
            NutritionViewModel::class.java
        ]
        setupViews()
        observeState()
        viewModel.loadShopping()
    }

    private fun setupViews() {
        findViewById<ImageButton>(R.id.btnBackNutritionShopping).setOnClickListener { finish() }
        nameInput = findViewById(R.id.inputNutritionShoppingName)
        quantityInput = findViewById(R.id.inputNutritionShoppingQuantity)
        categorySpinner = findViewById(R.id.spinnerNutritionShoppingCategory)
        emptyText = findViewById(R.id.tvNutritionShoppingEmpty)
        categorySpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            resources.getStringArray(R.array.nutrition_shopping_categories).toList()
        )
        adapter = NutritionShoppingAdapter { item, checked ->
            viewModel.setShoppingChecked(item.id, checked)
        }
        findViewById<RecyclerView>(R.id.recyclerNutritionShopping).apply {
            layoutManager = LinearLayoutManager(this@NutritionShoppingActivity)
            adapter = this@NutritionShoppingActivity.adapter
        }
        findViewById<MaterialButton>(R.id.btnNutritionShoppingAdd).setOnClickListener {
            lastRequestedQuantity = quantityInput.text?.toString()
            viewModel.addShopping(
                nameInput.text?.toString().orEmpty(),
                lastRequestedQuantity,
                categorySpinner.selectedItem?.toString().orEmpty()
            )
            nameInput.text?.clear()
            quantityInput.text?.clear()
        }
        findViewById<MaterialButton>(R.id.btnNutritionShoppingFromPlan).setOnClickListener {
            viewModel.addShoppingFromPlan()
        }
        findViewById<MaterialButton>(R.id.btnNutritionShoppingRemoveCompleted).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.nutrition_remove_completed)
                .setMessage(R.string.nutrition_remove_completed_confirmation)
                .setNegativeButton(R.string.nutrition_cancel, null)
                .setPositiveButton(R.string.nutrition_remove) { _, _ -> viewModel.removeCompletedShopping() }
                .show()
        }
        findViewById<MaterialButton>(R.id.btnNutritionShoppingClear).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.nutrition_clear_list)
                .setMessage(R.string.nutrition_clear_list_confirmation)
                .setNegativeButton(R.string.nutrition_cancel, null)
                .setPositiveButton(R.string.nutrition_clear) { _, _ -> viewModel.clearShopping() }
                .show()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(state.shoppingItems)
                    emptyText.isVisible = state.shoppingItems.isEmpty()
                    val duplicate = state.duplicateShoppingCandidate
                    if (duplicate != null && !duplicateDialogVisible) {
                        duplicateDialogVisible = true
                        AlertDialog.Builder(this@NutritionShoppingActivity)
                            .setTitle(R.string.nutrition_duplicate_product_title)
                            .setMessage(R.string.nutrition_duplicate_product_message)
                            .setNegativeButton(R.string.nutrition_cancel) { _, _ ->
                                duplicateDialogVisible = false
                                viewModel.dismissShoppingDuplicate()
                            }
                            .setPositiveButton(R.string.nutrition_increase_quantity) { _, _ ->
                                duplicateDialogVisible = false
                                viewModel.resolveShoppingDuplicate(
                                    duplicate,
                                    lastRequestedQuantity
                                )
                            }.show()
                    }
                    state.message?.let {
                        Toast.makeText(this@NutritionShoppingActivity, it, Toast.LENGTH_SHORT).show()
                        viewModel.consumeMessage()
                    }
                }
            }
        }
    }
}
