package com.luistureo.voicereminderapp.presentation.routine

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.routine.RoutinePreferenceStore
import com.luistureo.voicereminderapp.data.local.database.ReminderDatabase
import com.luistureo.voicereminderapp.data.repository.RoutineRepositoryImpl
import com.luistureo.voicereminderapp.domain.routine.model.RoutineChartType
import com.luistureo.voicereminderapp.domain.routine.model.RoutineHistoryRange
import com.luistureo.voicereminderapp.domain.routine.service.RoutineStatisticsCalculator
import java.time.LocalDate
import kotlinx.coroutines.launch

class RoutineProgressActivity : ComponentActivity() {
    private lateinit var rangeSpinner: Spinner
    private lateinit var chartSpinner: Spinner
    private lateinit var summary: TextView
    private lateinit var chart: RoutineChartView
    private lateinit var preferences: RoutinePreferenceStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_routine_progress)
        preferences = RoutinePreferenceStore(applicationContext)
        findViewById<ImageButton>(R.id.btnBackRoutineProgress).setOnClickListener { finish() }
        rangeSpinner = findViewById(R.id.spinnerRoutineHistoryRange)
        chartSpinner = findViewById(R.id.spinnerRoutineChartType)
        summary = findViewById(R.id.tvRoutineStatisticsSummary)
        chart = findViewById(R.id.viewRoutineChart)
        rangeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf("Día", "Semana", "Mes", "Año"))
        chartSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf("Barras", "Circular", "Calendario", "Lista porcentual"))
        chartSpinner.setSelection(preferences.getChartType().ordinal)
        val listener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) = load()
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        rangeSpinner.onItemSelectedListener = listener
        chartSpinner.onItemSelectedListener = listener
    }

    private fun load() {
        lifecycleScope.launch {
            val range = RoutineHistoryRange.entries[rangeSpinner.selectedItemPosition]
            val type = RoutineChartType.entries[chartSpinner.selectedItemPosition]
            preferences.setChartType(type)
            val today = LocalDate.now()
            val repository = RoutineRepositoryImpl(ReminderDatabase.getDatabase(applicationContext).routineDao())
            val histories = repository.getAllHistory()
            val stats = RoutineStatisticsCalculator.calculate(
                histories, range, today, preferences.getStreakSettings()
            )
            summary.text = getString(R.string.routine_statistics_summary,
                stats.completedRoutines, stats.partiallyCompletedRoutines,
                stats.skippedRoutines, stats.notCompletedRoutines,
                stats.taskCompletionPercentage, stats.currentStreak, stats.bestStreak,
                stats.mostCompletedRoutine ?: getString(R.string.routine_statistics_none),
                stats.mostPendingTask ?: getString(R.string.routine_statistics_none),
                com.luistureo.voicereminderapp.domain.routine.service.RoutineStatisticsCalculator
                    .supportiveStreakMessage(stats.currentStreak))
            chart.render(stats, type)
        }
    }
}
