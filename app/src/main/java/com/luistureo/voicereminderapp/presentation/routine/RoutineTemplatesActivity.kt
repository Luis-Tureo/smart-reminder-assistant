package com.luistureo.voicereminderapp.presentation.routine

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
import com.luistureo.voicereminderapp.presentation.routine.adapter.RoutineTemplateAdapter
import com.luistureo.voicereminderapp.presentation.routine.viewmodel.RoutineViewModel
import com.luistureo.voicereminderapp.presentation.routine.viewmodel.RoutineViewModelFactory
import kotlinx.coroutines.launch
import com.luistureo.voicereminderapp.core.routine.RoutinePreferenceStore

class RoutineTemplatesActivity : ComponentActivity() {
    private lateinit var viewModel: RoutineViewModel
    private lateinit var adapter: RoutineTemplateAdapter
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_routine_templates)
        viewModel = ViewModelProvider(
            this,
            RoutineViewModelFactory(applicationContext)
        )[RoutineViewModel::class.java]
        findViewById<ImageButton>(R.id.btnBackRoutineTemplates).setOnClickListener { finish() }
        emptyText = findViewById(R.id.tvRoutineTemplatesEmpty)
        val openTemplate: (com.luistureo.voicereminderapp.domain.routine.model.RoutineTemplate) -> Unit = { template ->
            startActivity(Intent(this, RoutineEditorActivity::class.java)
                .putExtra(RoutineEditorActivity.EXTRA_TEMPLATE_ID, template.id))
        }
        adapter = RoutineTemplateAdapter(
            onView = openTemplate,
            onUse = openTemplate,
            onDelete = { viewModel.deletePersonalTemplate(it.id) }
        )
        findViewById<RecyclerView>(R.id.recyclerRoutineTemplates).apply {
            layoutManager = LinearLayoutManager(this@RoutineTemplatesActivity)
            adapter = this@RoutineTemplatesActivity.adapter
        }
        observeState()
        findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRestoreRoutineTemplates)
            .setOnClickListener { viewModel.restoreBuiltInTemplates() }
        viewModel.loadTemplates()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val preferences = RoutinePreferenceStore(applicationContext)
                    val visibleTemplates = state.templates.filter {
                        (it.builtIn && preferences.showBuiltInTemplates()) ||
                            (!it.builtIn && preferences.showPersonalTemplates())
                    }
                    adapter.submitList(visibleTemplates)
                    emptyText.isVisible = !state.isLoading && visibleTemplates.isEmpty()
                    state.message?.let { message ->
                        Toast.makeText(this@RoutineTemplatesActivity, message, Toast.LENGTH_SHORT).show()
                        viewModel.consumeMessage()
                    }
                }
            }
        }
    }
}
