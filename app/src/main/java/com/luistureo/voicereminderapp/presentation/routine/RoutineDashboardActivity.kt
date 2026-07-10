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
import com.google.android.material.button.MaterialButton
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.presentation.routine.adapter.RoutineDashboardAdapter
import com.luistureo.voicereminderapp.presentation.routine.viewmodel.RoutineViewModel
import com.luistureo.voicereminderapp.presentation.routine.viewmodel.RoutineViewModelFactory
import kotlinx.coroutines.launch
import com.luistureo.voicereminderapp.core.routine.RoutinePreferenceStore
import com.luistureo.voicereminderapp.core.routine.RoutineSuggestionCoordinator
import com.luistureo.voicereminderapp.data.local.database.ReminderDatabase
import com.luistureo.voicereminderapp.data.repository.RoutineRepositoryImpl

class RoutineDashboardActivity : ComponentActivity() {
    private lateinit var viewModel: RoutineViewModel
    private lateinit var adapter: RoutineDashboardAdapter
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_routine_dashboard)
        viewModel = ViewModelProvider(
            this,
            RoutineViewModelFactory(applicationContext)
        )[RoutineViewModel::class.java]
        setupViews()
        observeState()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadDashboard()
        lifecycleScope.launch {
            val repository = RoutineRepositoryImpl(
                ReminderDatabase.getDatabase(applicationContext).routineDao()
            )
            RoutineSuggestionCoordinator(
                repository,
                RoutinePreferenceStore(applicationContext)
            ).evaluate()
        }
    }

    private fun setupViews() {
        findViewById<ImageButton>(R.id.btnBackRoutineDashboard).setOnClickListener { finish() }
        emptyText = findViewById(R.id.tvRoutineDashboardEmpty)
        adapter = RoutineDashboardAdapter { item ->
            startActivity(
                Intent(this, RoutineDetailActivity::class.java)
                    .putExtra(RoutineDetailActivity.EXTRA_ROUTINE_ID, item.routine.id)
            )
        }
        findViewById<RecyclerView>(R.id.recyclerRoutineDashboard).apply {
            layoutManager = LinearLayoutManager(this@RoutineDashboardActivity)
            adapter = this@RoutineDashboardActivity.adapter
        }
        findViewById<MaterialButton>(R.id.btnCreateRoutine).setOnClickListener {
            startActivity(Intent(this, RoutineEditorActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnRoutineTemplates).setOnClickListener {
            startActivity(Intent(this, RoutineTemplatesActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnRoutineProgress).setOnClickListener {
            startActivity(Intent(this, RoutineProgressActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnRoutineSuggestions).setOnClickListener {
            startActivity(Intent(this, RoutineSuggestionsActivity::class.java))
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(state.dashboardItems)
                    emptyText.isVisible = !state.isLoading && state.dashboardItems.isEmpty()
                    state.message?.let { message ->
                        Toast.makeText(this@RoutineDashboardActivity, message, Toast.LENGTH_SHORT).show()
                        viewModel.consumeMessage()
                    }
                }
            }
        }
    }
}
