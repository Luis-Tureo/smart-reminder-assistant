package com.luistureo.voicereminderapp.presentation.routine

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.notification.NotificationHelper
import com.luistureo.voicereminderapp.core.routine.RoutineAlarmType
import com.luistureo.voicereminderapp.core.routine.RoutinePostponePolicy
import com.luistureo.voicereminderapp.core.routine.RoutinePreferenceStore
import com.luistureo.voicereminderapp.core.routine.RoutineScheduler
import com.luistureo.voicereminderapp.data.local.database.ReminderDatabase
import com.luistureo.voicereminderapp.data.repository.RoutineRepositoryImpl
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.launch

class RoutinePostponeActivity : ComponentActivity() {
    private lateinit var options: RadioGroup
    private lateinit var customLayout: TextInputLayout
    private lateinit var customInput: TextInputEditText
    private lateinit var preferenceStore: RoutinePreferenceStore
    private val routineId by lazy { intent.getIntExtra(EXTRA_ROUTINE_ID, 0) }
    private val notificationId by lazy { intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0) }
    private val dateEpochDay by lazy {
        intent.getLongExtra(EXTRA_DATE_EPOCH_DAY, Long.MIN_VALUE)
    }
    private val alarmType by lazy {
        intent.getStringExtra(EXTRA_ALARM_TYPE)
            ?.let { runCatching { RoutineAlarmType.valueOf(it) }.getOrNull() }
            ?: RoutineAlarmType.START
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        if (routineId <= 0 || dateEpochDay != LocalDate.now().toEpochDay()) {
            if (notificationId != 0) {
                NotificationHelper(applicationContext).cancelNotification(notificationId)
            }
            finish()
            return
        }
        setContentView(R.layout.activity_routine_postpone)
        preferenceStore = RoutinePreferenceStore(applicationContext)
        options = findViewById(R.id.groupRoutinePostpone)
        customLayout = findViewById(R.id.layoutRoutinePostponeCustom)
        customInput = findViewById(R.id.inputRoutinePostponeCustom)
        selectStoredOption(preferenceStore.getPostponeMinutes())
        options.setOnCheckedChangeListener { _, checkedId ->
            customLayout.isVisible = checkedId == R.id.radioRoutinePostponeCustom
        }
        findViewById<MaterialButton>(R.id.btnCancelRoutinePostpone).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnConfirmRoutinePostpone).setOnClickListener {
            postpone()
        }
    }

    private fun selectStoredOption(minutes: Int) {
        val optionId = when (minutes) {
            5 -> R.id.radioRoutinePostpone5
            10 -> R.id.radioRoutinePostpone10
            30 -> R.id.radioRoutinePostpone30
            60 -> R.id.radioRoutinePostpone60
            else -> R.id.radioRoutinePostponeCustom
        }
        options.check(optionId)
        customLayout.isVisible = optionId == R.id.radioRoutinePostponeCustom
        if (optionId == R.id.radioRoutinePostponeCustom) {
            customInput.setText(String.format(Locale.getDefault(), "%d", minutes))
        }
    }

    private fun postpone() {
        customLayout.error = null
        val minutes = when (options.checkedRadioButtonId) {
            R.id.radioRoutinePostpone5 -> 5
            R.id.radioRoutinePostpone30 -> 30
            R.id.radioRoutinePostpone60 -> 60
            R.id.radioRoutinePostponeCustom -> customInput.text?.toString()?.toIntOrNull()
            else -> 10
        }
        val resolvedMinutes = minutes?.takeIf {
            it in RoutinePostponePolicy.MIN_CUSTOM_MINUTES..RoutinePostponePolicy.MAX_CUSTOM_MINUTES
        }
        if (resolvedMinutes == null) {
            customLayout.error = getString(R.string.routine_postpone_custom_error)
            return
        }
        lifecycleScope.launch {
            val repository = RoutineRepositoryImpl(
                ReminderDatabase.getDatabase(applicationContext).routineDao()
            )
            val routine = repository.getRoutineById(routineId)
            if (routine == null || !routine.enabled) {
                finish()
                return@launch
            }
            preferenceStore.setPostponeMinutes(resolvedMinutes)
            RoutineScheduler(applicationContext).schedulePostpone(
                routine = routine,
                sourceType = alarmType,
                minutes = resolvedMinutes
            )
            if (notificationId != 0) {
                NotificationHelper(applicationContext).cancelNotification(notificationId)
            }
            Toast.makeText(
                this@RoutinePostponeActivity,
                resources.getQuantityString(
                    R.plurals.routine_postponed_confirmation,
                    resolvedMinutes,
                    resolvedMinutes
                ),
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }

    companion object {
        const val EXTRA_ROUTINE_ID = "extra_routine_id"
        const val EXTRA_ALARM_TYPE = "extra_routine_alarm_type"
        const val EXTRA_DATE_EPOCH_DAY = "extra_routine_date_epoch_day"
        const val EXTRA_NOTIFICATION_ID = "extra_routine_notification_id"
    }
}
