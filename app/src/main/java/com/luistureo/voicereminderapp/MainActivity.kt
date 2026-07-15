package com.luistureo.voicereminderapp

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import com.luistureo.voicereminderapp.core.alarm.ExactAlarmPermissionPolicy
import com.luistureo.voicereminderapp.core.alarm.ReminderScheduler
import com.luistureo.voicereminderapp.presentation.assistant.AssistantActivity
import com.luistureo.voicereminderapp.presentation.calendar.CalendarActivity

class MainActivity : ComponentActivity() {

    private lateinit var assistantCard: View
    private lateinit var calendarCard: View
    private lateinit var reminderScheduler: ReminderScheduler

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(
                    this,
                    getString(R.string.notification_permission_denied),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ViewCompat.setAccessibilityHeading(findViewById(R.id.tvHomeTitle), true)

        assistantCard = findViewById(R.id.cardAssistantReminder)
        calendarCard = findViewById(R.id.cardCalendar)
        reminderScheduler = ReminderScheduler(applicationContext)

        assistantCard.setOnClickListener {
            startActivity(Intent(this, AssistantActivity::class.java))
        }
        calendarCard.setOnClickListener {
            startActivity(Intent(this, CalendarActivity::class.java))
        }

        requestNotificationPermissionIfNeeded()
        showExactAlarmPermissionGuidanceIfNeeded()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun showExactAlarmPermissionGuidanceIfNeeded() {
        if (
            !ExactAlarmPermissionPolicy.shouldShowGuidance(
                sdkInt = Build.VERSION.SDK_INT,
                android12SdkInt = Build.VERSION_CODES.S,
                canScheduleExactAlarms = reminderScheduler.canScheduleExactAlarms()
            )
        ) {
            return
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.exact_alarm_permission_title))
            .setMessage(getString(R.string.exact_alarm_permission_message))
            .setNegativeButton(R.string.exact_alarm_permission_later, null)
            .setPositiveButton(R.string.exact_alarm_permission_open_settings) { _, _ ->
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@setPositiveButton
                runCatching {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:$packageName")
                        }
                    )
                }.onFailure {
                    Toast.makeText(
                        this,
                        getString(R.string.exact_alarm_permission_settings_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .show()
    }
}
