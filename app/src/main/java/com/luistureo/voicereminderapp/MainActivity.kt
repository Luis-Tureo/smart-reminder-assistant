package com.luistureo.voicereminderapp

import android.Manifest
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.material.button.MaterialButton
import com.luistureo.voicereminderapp.core.alarm.ExactAlarmPermissionPolicy
import com.luistureo.voicereminderapp.core.alarm.ReminderScheduler
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarAuthManager
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarReminderSynchronizer
import com.luistureo.voicereminderapp.core.preference.NextDaySummaryPreferenceStore
import com.luistureo.voicereminderapp.core.utils.DateTimeFormatter
import com.luistureo.voicereminderapp.data.local.database.ReminderDatabase
import com.luistureo.voicereminderapp.data.repository.ReminderRepositoryImpl
import com.luistureo.voicereminderapp.domain.model.ReminderSource
import com.luistureo.voicereminderapp.domain.usecase.DeleteReminderUseCase
import com.luistureo.voicereminderapp.domain.usecase.GetReminderByIdUseCase
import com.luistureo.voicereminderapp.domain.usecase.GetRemindersUseCase
import com.luistureo.voicereminderapp.domain.usecase.SaveReminderDraftUseCase
import com.luistureo.voicereminderapp.domain.usecase.UpdateReminderUseCase
import com.luistureo.voicereminderapp.presentation.assistant.AssistantActivity
import com.luistureo.voicereminderapp.presentation.calendar.CalendarActivity
import com.luistureo.voicereminderapp.presentation.manual.ManualReminderActivity
import com.luistureo.voicereminderapp.presentation.state.ReminderUiEvent
import com.luistureo.voicereminderapp.presentation.ui.adapter.HomeReminderAdapter
import com.luistureo.voicereminderapp.presentation.viewmodel.ReminderViewModel
import com.luistureo.voicereminderapp.presentation.viewmodel.ReminderViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var assistantReminderCard: View
    private lateinit var calendarCard: View
    private lateinit var manualReminderCard: View
    private lateinit var cameraReminderCard: View
    private lateinit var googleCalendarSyncButton: MaterialButton
    private lateinit var googleCalendarStatusText: TextView
    private lateinit var nextDaySummaryTimeButton: MaterialButton
    private lateinit var remindersRecyclerView: RecyclerView
    private lateinit var homeEmptyStateContainer: View
    private lateinit var homeEmptyStateButton: MaterialButton

    private lateinit var reminderAdapter: HomeReminderAdapter
    private lateinit var reminderViewModel: ReminderViewModel
    private lateinit var reminderScheduler: ReminderScheduler
    private lateinit var summaryPreferenceStore: NextDaySummaryPreferenceStore
    private lateinit var googleCalendarAuthManager: GoogleCalendarAuthManager
    private lateinit var googleCalendarSynchronizer: GoogleCalendarReminderSynchronizer
    private lateinit var reminderRepository: ReminderRepositoryImpl

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

    private val googleCalendarSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK || result.data == null) {
                refreshGoogleCalendarStatus()
                Toast.makeText(
                    this,
                    getString(R.string.google_calendar_sign_in_cancelled),
                    Toast.LENGTH_SHORT
                ).show()
                return@registerForActivityResult
            }

            val account = runCatching {
                GoogleSignIn.getSignedInAccountFromIntent(result.data).result
            }.getOrNull()

            if (googleCalendarAuthManager.hasCalendarPermission(account)) {
                syncPendingGoogleCalendarChanges()
            } else {
                refreshGoogleCalendarStatus()
                Toast.makeText(
                    this,
                    getString(R.string.google_calendar_permission_missing),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupViewModel()
        setupCore()
        setupRecyclerView()
        setupActionButtons()
        observeState()
        observeEvents()
        requestNotificationPermissionIfNeeded()
        showExactAlarmPermissionGuidanceIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        if (::reminderViewModel.isInitialized) {
            reminderViewModel.loadReminders()
        }
        if (::googleCalendarAuthManager.isInitialized) {
            refreshGoogleCalendarStatus()
        }
    }

    private fun initViews() {
        assistantReminderCard = findViewById(R.id.cardAssistantReminder)
        calendarCard = findViewById(R.id.cardCalendar)
        manualReminderCard = findViewById(R.id.cardManualReminder)
        cameraReminderCard = findViewById(R.id.cardCameraReminder)
        googleCalendarSyncButton = findViewById(R.id.btnGoogleCalendarSync)
        googleCalendarStatusText = findViewById(R.id.tvGoogleCalendarStatus)
        nextDaySummaryTimeButton = findViewById(R.id.btnNextDaySummaryTime)
        remindersRecyclerView = findViewById(R.id.recyclerReminders)
        homeEmptyStateContainer = findViewById(R.id.homeEmptyStateCard)
        homeEmptyStateButton = findViewById(R.id.btnHomeEmptyStateCreateReminder)
    }

    private fun setupViewModel() {
        val database = ReminderDatabase.getDatabase(this)
        reminderRepository = ReminderRepositoryImpl(database.reminderDao())
        googleCalendarSynchronizer = GoogleCalendarReminderSynchronizer(
            context = applicationContext,
            reminderRepository = reminderRepository
        )

        val factory = ReminderViewModelFactory(
            context = applicationContext,
            saveReminderDraftUseCase = SaveReminderDraftUseCase(reminderRepository),
            getRemindersUseCase = GetRemindersUseCase(reminderRepository),
            getReminderByIdUseCase = GetReminderByIdUseCase(reminderRepository),
            deleteReminderUseCase = DeleteReminderUseCase(reminderRepository),
            updateReminderUseCase = UpdateReminderUseCase(reminderRepository),
            googleCalendarSynchronizer = googleCalendarSynchronizer
        )

        reminderViewModel = ViewModelProvider(this, factory)[ReminderViewModel::class.java]
    }

    private fun setupCore() {
        summaryPreferenceStore = NextDaySummaryPreferenceStore(applicationContext)
        reminderScheduler = ReminderScheduler(applicationContext)
        googleCalendarAuthManager = GoogleCalendarAuthManager(applicationContext)
    }

    private fun setupRecyclerView() {
        reminderAdapter = HomeReminderAdapter(
            items = emptyList(),
            onDelete = { reminder ->
                reminderViewModel.deleteReminder(reminder)
            },
            onUpdate = { reminder ->
                reminderViewModel.updateReminder(reminder)
            },
            onEdit = { reminder ->
                openManualReminderScreen(reminder.id, reminder.source)
            }
        )

        remindersRecyclerView.layoutManager = LinearLayoutManager(this)
        remindersRecyclerView.adapter = reminderAdapter
    }

    private fun setupActionButtons() {
        assistantReminderCard.setOnClickListener {
            startActivity(Intent(this, AssistantActivity::class.java))
        }

        calendarCard.setOnClickListener {
            startActivity(Intent(this, CalendarActivity::class.java))
        }

        manualReminderCard.setOnClickListener {
            openManualReminderScreen(null, ReminderSource.MANUAL)
        }

        cameraReminderCard.setOnClickListener {
            openManualReminderScreen(null, ReminderSource.CAMERA)
        }

        googleCalendarSyncButton.setOnClickListener {
            if (googleCalendarAuthManager.hasCalendarPermission()) {
                syncPendingGoogleCalendarChanges()
            } else {
                googleCalendarSignInLauncher.launch(
                    googleCalendarAuthManager.buildSignInIntent()
                )
            }
        }

        nextDaySummaryTimeButton.setOnClickListener {
            showNextDaySummaryTimePicker()
        }

        homeEmptyStateButton.setOnClickListener {
            openManualReminderScreen(null, ReminderSource.MANUAL)
        }

        refreshNextDaySummaryTimeButtonLabel()
        refreshGoogleCalendarStatus()
    }

    private fun openManualReminderScreen(
        reminderId: Int?,
        defaultSource: ReminderSource
    ) {
        val intent = Intent(this, ManualReminderActivity::class.java).apply {
            if (reminderId != null) {
                putExtra(ManualReminderActivity.EXTRA_REMINDER_ID, reminderId)
            }

            putExtra(ManualReminderActivity.EXTRA_DEFAULT_SOURCE, defaultSource.name)
        }

        startActivity(intent)
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                reminderViewModel.uiState.collect { state ->
                    reminderAdapter.updateData(state.homeTimelineItems)
                    homeEmptyStateContainer.isVisible = state.homeTimelineItems.isEmpty()
                    remindersRecyclerView.isVisible = state.homeTimelineItems.isNotEmpty()

                    state.error?.let { error ->
                        Toast.makeText(
                            this@MainActivity,
                            error.asString(this@MainActivity),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                reminderViewModel.events.collect { event ->
                    if (event is ReminderUiEvent.ShowMessage) {
                        Toast.makeText(
                            this@MainActivity,
                            event.message,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun showExactAlarmPermissionGuidanceIfNeeded() {
        if (!::reminderScheduler.isInitialized) return
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

    private fun showNextDaySummaryTimePicker() {
        val summaryTime = summaryPreferenceStore.getSummaryTime()

        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                summaryPreferenceStore.setSummaryTime(hourOfDay, minute)
                reminderScheduler.scheduleNextDaySummary()
                refreshNextDaySummaryTimeButtonLabel()

                val formattedTime = DateTimeFormatter.formatTime(hourOfDay, minute)
                Toast.makeText(
                    this,
                    getString(R.string.home_next_day_summary_time_saved, formattedTime),
                    Toast.LENGTH_SHORT
                ).show()
            },
            summaryTime.hour,
            summaryTime.minute,
            true
        ).show()
    }

    private fun refreshNextDaySummaryTimeButtonLabel() {
        nextDaySummaryTimeButton.text = getString(R.string.home_next_day_summary_time_button)
    }

    private fun refreshGoogleCalendarStatus() {
        val account = googleCalendarAuthManager.getSignedInAccount()
        val isConnected = googleCalendarAuthManager.hasCalendarPermission(account)

        googleCalendarStatusText.text = if (isConnected) {
            getString(
                R.string.google_calendar_connected,
                account?.email.orEmpty().ifBlank { getString(R.string.google_calendar_account) }
            )
        } else {
            getString(R.string.google_calendar_not_connected)
        }

        googleCalendarSyncButton.text = if (isConnected) {
            getString(R.string.google_calendar_sync_button)
        } else {
            getString(R.string.google_calendar_connect_button)
        }
    }

    private fun syncPendingGoogleCalendarChanges() {
        lifecycleScope.launch {
            runCatching {
                googleCalendarSynchronizer.syncPendingReminders()
            }.onSuccess { summary ->
                reminderViewModel.loadReminders()
                refreshGoogleCalendarStatus()
                Toast.makeText(
                    this@MainActivity,
                    getString(
                        R.string.google_calendar_sync_finished,
                        summary.syncedCount,
                        summary.failedCount,
                        summary.pendingDeleteCount
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure { exception ->
                refreshGoogleCalendarStatus()
                Toast.makeText(
                    this@MainActivity,
                    exception.message ?: getString(R.string.google_calendar_sync_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
