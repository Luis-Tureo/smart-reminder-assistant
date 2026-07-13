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
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.luistureo.voicereminderapp.core.alarm.ExactAlarmPermissionPolicy
import com.luistureo.voicereminderapp.core.alarm.ReminderScheduler
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarAuthManager
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarAuthException
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarErrorCode
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarReconnectDecision
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarReminderSynchronizer
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarSyncLogger
import com.luistureo.voicereminderapp.core.calendar.unified.UnifiedCalendarSynchronizer
import com.luistureo.voicereminderapp.core.modules.ModuleSelectionStore
import com.luistureo.voicereminderapp.core.preference.NextDaySummaryPreferenceStore
import com.luistureo.voicereminderapp.core.utils.DateTimeFormatter
import com.luistureo.voicereminderapp.data.local.database.ReminderDatabase
import com.luistureo.voicereminderapp.data.repository.ReminderRepositoryImpl
import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import com.luistureo.voicereminderapp.domain.model.ReminderSource
import com.luistureo.voicereminderapp.domain.usecase.DeleteReminderUseCase
import com.luistureo.voicereminderapp.domain.usecase.GetReminderByIdUseCase
import com.luistureo.voicereminderapp.domain.usecase.GetRemindersUseCase
import com.luistureo.voicereminderapp.domain.usecase.SaveReminderDraftUseCase
import com.luistureo.voicereminderapp.domain.usecase.UpdateReminderUseCase
import com.luistureo.voicereminderapp.presentation.assistant.AssistantActivity
import com.luistureo.voicereminderapp.presentation.calendar.CalendarActivity
import com.luistureo.voicereminderapp.presentation.calendar.GoogleCalendarErrorUi
import com.luistureo.voicereminderapp.presentation.loan.LoanListActivity
import com.luistureo.voicereminderapp.presentation.manual.ManualReminderActivity
import com.luistureo.voicereminderapp.presentation.modules.ModuleSelectionActivity
import com.luistureo.voicereminderapp.presentation.nutrition.NutritionDashboardActivity
import com.luistureo.voicereminderapp.presentation.recovery.RecoveryDashboardActivity
import com.luistureo.voicereminderapp.presentation.routine.RoutineDashboardActivity
import com.luistureo.voicereminderapp.presentation.state.ReminderUiEvent
import com.luistureo.voicereminderapp.presentation.ui.adapter.HomeReminderAdapter
import com.luistureo.voicereminderapp.presentation.viewmodel.ReminderViewModel
import com.luistureo.voicereminderapp.presentation.viewmodel.ReminderViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var assistantReminderCard: View
    private lateinit var calendarCard: View
    private lateinit var loanCard: View
    private lateinit var dailyRoutinesCard: View
    private lateinit var nutritionCard: View
    private lateinit var recoveryCard: View
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
    private lateinit var unifiedCalendarSynchronizer: UnifiedCalendarSynchronizer
    private lateinit var reminderRepository: ReminderRepositoryImpl
    private var googleRecoveryAttempted = false
    private var googleActivationRequested = false

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
            CalendarSyncLogger.authResultReceived(
                CalendarProvider.GOOGLE_CALENDAR,
                if (result.resultCode == RESULT_OK) "ok" else "cancelled"
            )
            val data = result.data
            if (data == null) {
                val code = if (result.resultCode == RESULT_CANCELED) {
                    GoogleCalendarErrorCode.AUTH_CANCELLED
                } else {
                    GoogleCalendarErrorCode.AUTH_UNKNOWN
                }
                if (code == GoogleCalendarErrorCode.AUTH_CANCELLED) {
                    googleCalendarAuthManager.cancelPendingAuth()
                    CalendarSyncLogger.authCancelled(
                        CalendarProvider.GOOGLE_CALENDAR,
                        reason = code.value
                    )
                } else {
                    googleCalendarAuthManager.failPendingAuth(code)
                }
                showHomeGoogleAuthError(code)
                return@registerForActivityResult
            }

            val accountResult = runCatching {
                GoogleSignIn.getSignedInAccountFromIntent(data)
                    .getResult(ApiException::class.java)
            }
            val account = accountResult.getOrNull()

            if (accountResult.isFailure) {
                val code = GoogleCalendarErrorCode.fromSignInFailure(
                    accountResult.exceptionOrNull()
                        ?: IllegalStateException("home_auth_result_invalid")
                )
                if (code == GoogleCalendarErrorCode.AUTH_CANCELLED) {
                    googleCalendarAuthManager.cancelPendingAuth()
                } else {
                    googleCalendarAuthManager.failPendingAuth(code)
                    CalendarSyncLogger.googleAuthFailed(code.value)
                }
                showHomeGoogleAuthError(code)
                return@registerForActivityResult
            }

            completeHomeGoogleSignIn(account)
        }

    private val googleCalendarRecoveryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                completeHomeGoogleSignIn(googleCalendarAuthManager.getSignedInAccount())
            } else {
                googleCalendarAuthManager.failPendingAuth(
                    GoogleCalendarErrorCode.AUTH_SCOPE_DENIED
                )
                showHomeGoogleAuthError(GoogleCalendarErrorCode.AUTH_SCOPE_DENIED)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val moduleSelectionStore = ModuleSelectionStore(applicationContext)
        if (
            !moduleSelectionStore.isSelectionCompleted() ||
            moduleSelectionStore.selectedModuleIds().isEmpty()
        ) {
            startActivity(ModuleSelectionActivity.firstLaunchIntent(this))
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
            return
        }
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
        loanCard = findViewById(R.id.cardLoan)
        dailyRoutinesCard = findViewById(R.id.cardDailyRoutines)
        nutritionCard = findViewById(R.id.cardNutrition)
        recoveryCard = findViewById(R.id.cardRecovery)
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
        unifiedCalendarSynchronizer = UnifiedCalendarSynchronizer(
            context = applicationContext,
            reminderRepository = reminderRepository,
            googleCalendarSynchronizer = googleCalendarSynchronizer
        )

        val factory = ReminderViewModelFactory(
            context = applicationContext,
            saveReminderDraftUseCase = SaveReminderDraftUseCase(reminderRepository),
            getRemindersUseCase = GetRemindersUseCase(reminderRepository),
            getReminderByIdUseCase = GetReminderByIdUseCase(reminderRepository),
            deleteReminderUseCase = DeleteReminderUseCase(reminderRepository),
            updateReminderUseCase = UpdateReminderUseCase(reminderRepository),
            unifiedCalendarSynchronizer = unifiedCalendarSynchronizer
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
                showDeleteReminderConfirmation(reminder)
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

    private fun showDeleteReminderConfirmation(
        reminder: com.luistureo.voicereminderapp.domain.model.Reminder
    ) {
        val linkedExternalProviders = reminder.externalIdsByProvider
            .filterKeys { it != CalendarProvider.APP }
            .filterValues { it.isNotBlank() }
            .keys
        if (linkedExternalProviders.isNotEmpty()) {
            val options = arrayOf(
                getString(R.string.calendar_delete_local_only),
                getString(R.string.calendar_delete_with_synced_calendars),
                getString(R.string.reminder_cancel_action)
            )
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.calendar_delete_title))
                .setMessage(getString(R.string.calendar_delete_synced_message, reminder.title))
                .setItems(options) { dialog, which ->
                    when (which) {
                        0 -> reminderViewModel.deleteReminder(
                            reminder = reminder,
                            deleteExternalCalendars = false
                        )
                        1 -> reminderViewModel.deleteReminder(
                            reminder = reminder,
                            deleteExternalCalendars = true
                        )
                        else -> dialog.dismiss()
                    }
                }
                .show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.calendar_delete_title))
            .setMessage(getString(R.string.calendar_delete_message, reminder.title))
            .setNegativeButton(R.string.reminder_cancel_action, null)
            .setPositiveButton(R.string.delete_reminder) { _, _ ->
                reminderViewModel.deleteReminder(
                    reminder = reminder,
                    deleteExternalCalendars = false
                )
            }
            .show()
    }

    private fun setupActionButtons() {
        assistantReminderCard.setOnClickListener {
            startActivity(Intent(this, AssistantActivity::class.java))
        }

        calendarCard.setOnClickListener {
            startActivity(Intent(this, CalendarActivity::class.java))
        }

        loanCard.setOnClickListener {
            startActivity(Intent(this, LoanListActivity::class.java))
        }

        dailyRoutinesCard.setOnClickListener {
            startActivity(Intent(this, RoutineDashboardActivity::class.java))
        }

        nutritionCard.setOnClickListener {
            startActivity(Intent(this, NutritionDashboardActivity::class.java))
        }

        recoveryCard.setOnClickListener {
            startActivity(Intent(this, RecoveryDashboardActivity::class.java))
        }

        manualReminderCard.setOnClickListener {
            openManualReminderScreen(null, ReminderSource.MANUAL)
        }

        cameraReminderCard.setOnClickListener {
            openManualReminderScreen(null, ReminderSource.CAMERA)
        }

        googleCalendarSyncButton.setOnClickListener {
            CalendarSyncLogger.authButtonPressed(CalendarProvider.GOOGLE_CALENDAR)
            when {
                googleCalendarAuthManager.isConnected() -> {
                    googleCalendarAuthManager.pauseSync()
                    refreshGoogleCalendarStatus()
                }
                googleCalendarAuthManager.isPaused() -> startHomeGoogleAuth()
                else -> startHomeGoogleAuth()
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
        val isConnected = googleCalendarAuthManager.isConnected(account)

        googleCalendarStatusText.text = if (isConnected || googleCalendarAuthManager.isPaused(account)) {
            getString(
                R.string.google_calendar_connected,
                account?.email.orEmpty().ifBlank { getString(R.string.google_calendar_account) }
            )
        } else {
            getString(R.string.google_calendar_not_connected)
        }

        googleCalendarSyncButton.text = if (isConnected) {
            getString(R.string.calendar_sync_google_deactivate)
        } else if (googleCalendarAuthManager.isPaused(account)) {
            getString(R.string.calendar_sync_google_activate)
        } else {
            getString(R.string.calendar_sync_google_connect)
        }
    }

    private fun startHomeGoogleAuth(preserveActivationRequest: Boolean = false) {
        if (!preserveActivationRequest) {
            googleActivationRequested = googleCalendarAuthManager.hasConnectedSession() &&
                    !googleCalendarAuthManager.isSyncEnabled
        }
        googleRecoveryAttempted = false
        CalendarSyncLogger.ui(
            "home_google_sync_button",
            mapOf("buttonAction" to "connect_google")
        )
        CalendarSyncLogger.authStarted(CalendarProvider.GOOGLE_CALENDAR)
        googleCalendarAuthManager.prepareReconnect { decision ->
            runOnUiThread {
                when (decision) {
                    GoogleCalendarReconnectDecision.REUSE_VALID_SESSION -> {
                        lifecycleScope.launch {
                            runCatching { googleCalendarAuthManager.reactivateExistingSession() }
                                .onSuccess { connected ->
                                    if (connected) syncPendingCalendarChanges()
                                    else startHomeGoogleAuth(preserveActivationRequest = true)
                                }
                                .onFailure { exception ->
                                    val code = GoogleCalendarErrorCode.fromSyncFailure(exception)
                                    if (code.invalidatesSession) {
                                        startHomeGoogleAuth(preserveActivationRequest = true)
                                    } else {
                                        showHomeGoogleAuthError(code)
                                    }
                                }
                        }
                    }
                    GoogleCalendarReconnectDecision.NEEDS_LOGIN -> launchHomeGoogleAuth()
                }
            }
        }
    }

    private fun completeHomeGoogleSignIn(account: GoogleSignInAccount?) {
        lifecycleScope.launch {
            runCatching {
                googleCalendarAuthManager.completeSignIn(
                    account = account,
                    activated = googleActivationRequested
                )
            }
                .onSuccess { connected ->
                    if (connected) {
                        CalendarSyncLogger.authSuccess(CalendarProvider.GOOGLE_CALENDAR)
                        refreshGoogleCalendarStatus()
                        syncPendingCalendarChanges()
                    } else {
                        showHomeGoogleAuthError(GoogleCalendarErrorCode.AUTH_SCOPE_DENIED)
                    }
                }
                .onFailure { exception ->
                    val recoverable = exception as? GoogleCalendarAuthException.UserActionRequired
                    if (recoverable?.recoveryIntent != null && !googleRecoveryAttempted) {
                        googleRecoveryAttempted = true
                        runCatching {
                            googleCalendarRecoveryLauncher.launch(recoverable.recoveryIntent)
                        }.onFailure { launchFailure ->
                            val code = GoogleCalendarErrorCode.fromSignInFailure(launchFailure)
                            googleCalendarAuthManager.failPendingAuth(code)
                            showHomeGoogleAuthError(code)
                        }
                        return@onFailure
                    }
                    val code = GoogleCalendarErrorCode.fromSyncFailure(exception)
                    googleCalendarAuthManager.failPendingAuth(code)
                    CalendarSyncLogger.googleAuthFailed(code.value)
                    showHomeGoogleAuthError(code)
                }
        }
    }

    private fun launchHomeGoogleAuth() {
        runCatching {
            CalendarSyncLogger.authLaunched(CalendarProvider.GOOGLE_CALENDAR)
            googleCalendarSignInLauncher.launch(googleCalendarAuthManager.buildSignInIntent())
        }.onFailure { exception ->
            val code = GoogleCalendarErrorCode.fromSignInFailure(exception)
            googleCalendarAuthManager.failPendingAuth(code)
            CalendarSyncLogger.googleAuthFailed(code.value)
            showHomeGoogleAuthError(code)
        }
    }

    private fun showHomeGoogleAuthError(code: GoogleCalendarErrorCode) {
        googleCalendarStatusText.text = getString(GoogleCalendarErrorUi.messageRes(code))
        googleCalendarSyncButton.text = if (
            googleCalendarAuthManager.hasConnectedSession() &&
            !googleCalendarAuthManager.isSyncEnabled
        ) {
            getString(R.string.calendar_sync_google_activate)
        } else if (googleCalendarAuthManager.hasConnectedSession()) {
            getString(R.string.calendar_sync_google_deactivate)
        } else {
            getString(R.string.calendar_sync_google_connect)
        }
        CalendarSyncLogger.inlineErrorShown(
            CalendarProvider.GOOGLE_CALENDAR,
            code.value
        )
    }

    private fun syncPendingCalendarChanges() {
        lifecycleScope.launch {
            runCatching {
                unifiedCalendarSynchronizer.syncPendingReminders()
            }.onSuccess { summary ->
                reminderViewModel.loadReminders()
                refreshGoogleCalendarStatus()
            }.onFailure { exception ->
                val code = GoogleCalendarErrorCode.fromSyncFailure(exception)
                CalendarSyncLogger.error(
                    CalendarProvider.GOOGLE_CALENDAR,
                    action = "home_sync_pending",
                    fallbackReason = code.value
                )
                googleCalendarAuthManager.markConnectionError(code)
                showHomeGoogleAuthError(code)
            }
        }
    }
}
