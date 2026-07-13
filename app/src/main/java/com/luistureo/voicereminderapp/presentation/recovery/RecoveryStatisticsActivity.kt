package com.luistureo.voicereminderapp.presentation.recovery

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.recovery.RecoveryRuntime
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryStatisticsRange
import com.luistureo.voicereminderapp.domain.recovery.service.RecoveryStatisticsCalculator
import java.time.LocalDate
import kotlinx.coroutines.launch

class RecoveryStatisticsActivity : ComponentActivity() {
    private val goalId by lazy { intent.getIntExtra(RecoveryDashboardActivity.EXTRA_GOAL_ID, 0) }
    private lateinit var rangeSpinner: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (goalId <= 0) { finish(); return }
        setContentView(R.layout.activity_recovery_statistics)
        findViewById<ImageButton>(R.id.btnBackRecoveryStatistics).setOnClickListener { finish() }
        rangeSpinner = findViewById(R.id.spinnerRecoveryStatisticsRange)
        rangeSpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            listOf(
                getString(R.string.recovery_statistics_day),
                getString(R.string.recovery_statistics_week),
                getString(R.string.recovery_statistics_month),
                getString(R.string.recovery_statistics_year)
            )
        )
        rangeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = load()
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun load() {
        lifecycleScope.launch {
            val repository = RecoveryRuntime.repository(applicationContext)
            val goal = repository.getGoal(goalId) ?: return@launch
            val checkIns = repository.getCheckIns(goal.historyKey)
            val stats = RecoveryStatisticsCalculator.calculate(
                checkIns,
                RecoveryStatisticsRange.entries[rangeSpinner.selectedItemPosition],
                LocalDate.now()
            )
            findViewById<TextView>(R.id.tvRecoveryStatisticsEmpty).isVisible = stats.checkIns == 0
            findViewById<View>(R.id.containerRecoveryStatistics).isVisible = stats.checkIns > 0
            findViewById<TextView>(R.id.tvRecoveryStatisticsSummary).text = listOf(
                getString(R.string.recovery_statistics_success, stats.successfulDays),
                getString(R.string.recovery_statistics_difficult, stats.difficultDays),
                getString(R.string.recovery_statistics_reduced, stats.reducedFrequencyDays),
                getString(R.string.recovery_statistics_checkins, stats.checkIns),
                getString(R.string.recovery_statistics_current, stats.currentStreak),
                getString(R.string.recovery_statistics_best, stats.bestStreak),
                getString(R.string.recovery_statistics_actions, stats.helpfulActionsUsed)
            ).joinToString("\n")
            val max = stats.checkIns.coerceAtLeast(1)
            findViewById<ProgressBar>(R.id.progressRecoverySuccess).apply { this.max = max; progress = stats.successfulDays }
            findViewById<ProgressBar>(R.id.progressRecoveryDifficult).apply { this.max = max; progress = stats.difficultDays }
            findViewById<TextView>(R.id.tvRecoverySuccessBarLabel).text = getString(R.string.recovery_statistics_success, stats.successfulDays)
            findViewById<TextView>(R.id.tvRecoveryDifficultBarLabel).text = getString(R.string.recovery_statistics_difficult, stats.difficultDays)
            findViewById<TextView>(R.id.tvRecoveryCommonTriggers).text = stats.commonTriggers
                .joinToString("\n") { "${it.first}: ${it.second}" }
                .ifBlank { getString(R.string.recovery_none) }
        }
    }
}
