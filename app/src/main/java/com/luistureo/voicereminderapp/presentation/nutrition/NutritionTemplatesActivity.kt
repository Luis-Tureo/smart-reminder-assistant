package com.luistureo.voicereminderapp.presentation.nutrition

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
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
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.presentation.nutrition.adapter.NutritionTemplateAdapter
import com.luistureo.voicereminderapp.presentation.nutrition.viewmodel.NutritionViewModel
import com.luistureo.voicereminderapp.presentation.nutrition.viewmodel.NutritionViewModelFactory
import kotlinx.coroutines.launch

class NutritionTemplatesActivity : ComponentActivity() {
    private lateinit var viewModel: NutritionViewModel
    private lateinit var adapter: NutritionTemplateAdapter
    private lateinit var emptyText: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nutrition_templates)
        viewModel = ViewModelProvider(this, NutritionViewModelFactory(applicationContext))[
            NutritionViewModel::class.java
        ]
        findViewById<ImageButton>(R.id.btnBackNutritionTemplates).setOnClickListener { finish() }
        emptyText = findViewById(R.id.tvNutritionTemplatesEmpty)
        adapter = NutritionTemplateAdapter { template ->
            startActivity(Intent(this, NutritionTemplatePreviewActivity::class.java).apply {
                putExtra(NutritionTemplatePreviewActivity.EXTRA_TEMPLATE_ID, template.id)
            })
        }
        findViewById<RecyclerView>(R.id.recyclerNutritionTemplates).apply {
            layoutManager = LinearLayoutManager(this@NutritionTemplatesActivity)
            adapter = this@NutritionTemplatesActivity.adapter
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(state.templates)
                    emptyText.isVisible = state.templates.isEmpty()
                    state.message?.let {
                        Toast.makeText(this@NutritionTemplatesActivity, it, Toast.LENGTH_SHORT).show()
                        viewModel.consumeMessage()
                    }
                }
            }
        }
        viewModel.loadTemplates()
    }
}

