package com.luistureo.voicereminderapp.presentation.calendar

import android.Manifest
import android.os.Bundle
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.content.res.ColorStateList
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.alarm.ExactAlarmPermissionPolicy
import com.luistureo.voicereminderapp.core.alarm.ReminderScheduler
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarAuthManager
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarAuthException
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarErrorCode
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarReconnectDecision
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarReminderSynchronizer
import com.luistureo.voicereminderapp.core.calendar.microsoft.MicrosoftCalendarAuthController
import com.luistureo.voicereminderapp.core.calendar.microsoft.MicrosoftCalendarAuthProvider
import com.luistureo.voicereminderapp.core.calendar.microsoft.MicrosoftCalendarAuthResult
import com.luistureo.voicereminderapp.core.calendar.microsoft.MicrosoftCalendarErrorCode
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarSyncLogger
import com.luistureo.voicereminderapp.core.calendar.unified.CalendarAutoSyncStateStore
import com.luistureo.voicereminderapp.core.calendar.unified.MeetingUrlPolicy
import com.luistureo.voicereminderapp.core.calendar.unified.MeetingOpenCoordinator
import com.luistureo.voicereminderapp.core.calendar.unified.MeetingContentSanitizer
import com.luistureo.voicereminderapp.core.calendar.unified.UnifiedCalendarSynchronizer
import com.luistureo.voicereminderapp.data.local.database.ReminderDatabase
import com.luistureo.voicereminderapp.data.repository.ReminderRepositoryImpl
import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import com.luistureo.voicereminderapp.domain.model.ReminderSource
import com.luistureo.voicereminderapp.domain.model.ReminderType
import com.luistureo.voicereminderapp.domain.usecase.DeleteReminderUseCase
import com.luistureo.voicereminderapp.domain.usecase.GetRemindersUseCase
import com.luistureo.voicereminderapp.domain.usecase.UpdateReminderUseCase
import com.luistureo.voicereminderapp.presentation.assistant.AssistantActivity
import com.luistureo.voicereminderapp.presentation.manual.ManualReminderActivity
import com.luistureo.voicereminderapp.presentation.manual.PasteTextReminderActivity
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

class CalendarActivity : AppCompatActivity() {

    private val accessibilityDateFormatter = java.time.format.DateTimeFormatter.ofPattern(
        "EEEE d 'de' MMMM",
        Locale.forLanguageTag("es-CL")
    )

    private lateinit var previousMonthButton: ImageButton
    private lateinit var nextMonthButton: ImageButton
    private lateinit var monthSelectorButton: MaterialButton
    private lateinit var yearSelectorButton: MaterialButton
    private lateinit var plannerCardContainer: LinearLayout
    private lateinit var calendarSyncStatusView: TextView
    private lateinit var calendarLastSyncView: TextView
    private lateinit var calendarSyncInlineNoticeView: TextView
    private lateinit var calendarSyncErrorContainer: View
    private lateinit var calendarSyncErrorView: TextView
    private lateinit var googleCalendarSyncButton: MaterialButton
    private lateinit var microsoftCalendarSyncButton: MaterialButton
    private lateinit var googleCalendarDisconnectButton: MaterialButton
    private lateinit var microsoftCalendarDisconnectButton: MaterialButton
    private lateinit var calendarDisconnectActionsContainer: View
    private lateinit var calendarGridCard: MaterialCardView
    private lateinit var weekdayContainer: LinearLayout
    private lateinit var calendarGrid: GridLayout
    private lateinit var selectedDateTitleView: TextView
    private lateinit var selectedDateSummaryView: TextView
    private lateinit var holidaySummaryView: TextView
    private lateinit var emptyStateView: TextView
    private lateinit var detailContainer: LinearLayout
    private lateinit var selectedDateActionsContainer: LinearLayout
    private lateinit var createReminderButton: MaterialButton
    private lateinit var legendFilterCards: Map<CalendarIndicatorStyle, MaterialCardView>
    private lateinit var legendFilterDots: Map<CalendarIndicatorStyle, View>
    private lateinit var legendFilterLabels: Map<CalendarIndicatorStyle, TextView>
    private lateinit var legendShowAllButton: MaterialButton

    private lateinit var calendarViewModel: CalendarViewModel
    private lateinit var googleCalendarAuthManager: GoogleCalendarAuthManager
    private lateinit var googleCalendarSynchronizer: GoogleCalendarReminderSynchronizer
    private lateinit var unifiedCalendarSynchronizer: UnifiedCalendarSynchronizer
    private lateinit var microsoftCalendarAuthController: MicrosoftCalendarAuthController
    private lateinit var calendarAutoSyncStateStore: CalendarAutoSyncStateStore
    private lateinit var reminderScheduler: ReminderScheduler

    private var activeFilter: CalendarIndicatorStyle? = null
    private var lastErrorMessage: String? = null
    private var lastDuplicateWarningLogKey: String? = null
    private var lastRenderedMonthTitle: String? = null
    private var lastSelectedDateTitle: String? = null
    private var lastRenderedState: CalendarUiState? = null
    private var googleProviderUiState = CalendarProviderUiState.DISCONNECTED
    private var microsoftProviderUiState = CalendarProviderUiState.DISCONNECTED
    private var googleSyncError: String? = null
    private var microsoftSyncError: String? = null
    private var lastLoggedGoogleState: CalendarProviderUiState? = null
    private var lastLoggedMicrosoftState: CalendarProviderUiState? = null
    private var lastVisibleMonthSyncError: CalendarSyncInlineError? = null
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

    private val assistantFlowLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val savedDate = result.data
                ?.getStringExtra(AssistantActivity.EXTRA_SAVED_DATE)
                ?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }

            if (result.resultCode == RESULT_OK && savedDate != null) {
                calendarViewModel.onDaySelected(savedDate)
            } else {
                calendarViewModel.reloadCalendar()
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
                    CalendarSyncLogger.authCancelled(
                        CalendarProvider.GOOGLE_CALENDAR,
                        reason = code.value
                    )
                    googleCalendarAuthManager.cancelPendingAuth()
                }
                showGoogleInlineError(code)
                return@registerForActivityResult
            }

            val accountResult = runCatching {
                GoogleSignIn.getSignedInAccountFromIntent(data)
                    .getResult(ApiException::class.java)
            }
            val account = accountResult.getOrNull()

            if (accountResult.isFailure) {
                val code = GoogleCalendarErrorCode.fromSignInFailure(
                    accountResult.exceptionOrNull() ?: IllegalStateException("sign_in_failed")
                )
                if (code == GoogleCalendarErrorCode.AUTH_CANCELLED) {
                    CalendarSyncLogger.authCancelled(
                        CalendarProvider.GOOGLE_CALENDAR,
                        reason = code.value
                    )
                    googleCalendarAuthManager.cancelPendingAuth()
                } else {
                    CalendarSyncLogger.googleAuthFailed(code.value)
                }
                showGoogleInlineError(code)
                return@registerForActivityResult
            }

            completeGoogleSignIn(account)
        }

    private val googleCalendarRecoveryLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            CalendarSyncLogger.authResultReceived(
                CalendarProvider.GOOGLE_CALENDAR,
                if (result.resultCode == RESULT_OK) "scope_recovery_ok" else "scope_denied"
            )
            if (result.resultCode == RESULT_OK) {
                completeGoogleSignIn(googleCalendarAuthManager.getSignedInAccount())
            } else {
                showGoogleInlineError(GoogleCalendarErrorCode.AUTH_SCOPE_DENIED)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeFilter = savedInstanceState
            ?.getString(STATE_ACTIVE_FILTER)
            ?.let { value -> runCatching { CalendarIndicatorStyle.valueOf(value) }.getOrNull() }
        setContentView(R.layout.activity_calendar)
        initViews()
        setupViewModel()
        savedInstanceState
            ?.getString(STATE_SELECTED_DATE)
            ?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull() }
            ?.let(calendarViewModel::onDaySelected)
        setupControls()
        observeState()
        requestNotificationPermissionIfNeeded()
        showExactAlarmPermissionGuidanceIfNeeded()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        activeFilter?.let { outState.putString(STATE_ACTIVE_FILTER, it.name) }
        lastRenderedState?.days
            ?.firstOrNull { it.isSelected }
            ?.date
            ?.let { outState.putString(STATE_SELECTED_DATE, it.toString()) }
    }

    override fun onResume() {
        super.onResume()
        calendarViewModel.reloadCalendar()
        refreshGoogleCalendarStatus()
        if (::unifiedCalendarSynchronizer.isInitialized) {
            microsoftCalendarAuthController.refreshConnectionState { isConnected ->
                runOnUiThread {
                    refreshGoogleCalendarStatus()
                    if (isConnected && !googleCalendarAuthManager.isConnected()) {
                        syncMicrosoftCalendarChanges()
                    }
                }
            }
        }
    }

    private fun initViews() {
        previousMonthButton = findViewById(R.id.btnPreviousMonth)
        nextMonthButton = findViewById(R.id.btnNextMonth)
        monthSelectorButton = findViewById(R.id.btnMonthSelector)
        yearSelectorButton = findViewById(R.id.btnYearSelector)
        plannerCardContainer = findViewById(R.id.containerCalendarPlannerCard)
        calendarSyncStatusView = findViewById(R.id.tvCalendarSyncStatus)
        calendarLastSyncView = findViewById(R.id.tvCalendarLastSync)
        calendarSyncInlineNoticeView = findViewById(R.id.tvCalendarSyncInlineNotice)
        calendarSyncErrorContainer = findViewById(R.id.containerCalendarSyncError)
        calendarSyncErrorView = findViewById(R.id.tvCalendarSyncError)
        googleCalendarSyncButton = findViewById(R.id.btnCalendarGoogleSync)
        microsoftCalendarSyncButton = findViewById(R.id.btnCalendarMicrosoftSync)
        googleCalendarDisconnectButton = findViewById(R.id.btnCalendarGoogleDisconnect)
        microsoftCalendarDisconnectButton = findViewById(R.id.btnCalendarMicrosoftDisconnect)
        calendarDisconnectActionsContainer = findViewById(
            R.id.containerCalendarDisconnectActions
        )
        calendarGridCard = findViewById(R.id.cardCalendarGrid)
        weekdayContainer = findViewById(R.id.containerCalendarWeekdays)
        calendarGrid = findViewById(R.id.gridCalendarDays)
        selectedDateTitleView = findViewById(R.id.tvSelectedDateTitle)
        selectedDateSummaryView = findViewById(R.id.tvSelectedDateSummary)
        holidaySummaryView = findViewById(R.id.tvHolidaySummary)
        emptyStateView = findViewById(R.id.tvCalendarEmptyState)
        detailContainer = findViewById(R.id.containerDayDetails)
        selectedDateActionsContainer = findViewById(R.id.containerSelectedDateActions)
        createReminderButton = findViewById(R.id.btnCalendarCreateReminder)
        legendShowAllButton = findViewById(R.id.btnCalendarLegendShowAll)
        legendFilterCards = mapOf(
            CalendarIndicatorStyle.REMINDER to findViewById(R.id.cardCalendarFilterReminder),
            CalendarIndicatorStyle.BIRTHDAY to findViewById(R.id.cardCalendarFilterBirthday),
            CalendarIndicatorStyle.HOLIDAY to findViewById(R.id.cardCalendarFilterHoliday),
            CalendarIndicatorStyle.COMPLETED to findViewById(R.id.cardCalendarFilterCompleted)
        )
        legendFilterDots = mapOf(
            CalendarIndicatorStyle.REMINDER to findViewById(R.id.dotCalendarFilterReminder),
            CalendarIndicatorStyle.BIRTHDAY to findViewById(R.id.dotCalendarFilterBirthday),
            CalendarIndicatorStyle.HOLIDAY to findViewById(R.id.dotCalendarFilterHoliday),
            CalendarIndicatorStyle.COMPLETED to findViewById(R.id.dotCalendarFilterCompleted)
        )
        legendFilterLabels = mapOf(
            CalendarIndicatorStyle.REMINDER to findViewById(R.id.tvCalendarFilterReminder),
            CalendarIndicatorStyle.BIRTHDAY to findViewById(R.id.tvCalendarFilterBirthday),
            CalendarIndicatorStyle.HOLIDAY to findViewById(R.id.tvCalendarFilterHoliday),
            CalendarIndicatorStyle.COMPLETED to findViewById(R.id.tvCalendarFilterCompleted)
        )
        listOf(
            findViewById<TextView>(R.id.tvUnifiedScreenTitle),
            selectedDateTitleView,
            findViewById<TextView>(R.id.tvCalendarConnectionsHeading),
            findViewById<TextView>(R.id.tvCalendarFiltersHeading)
        ).forEach { heading ->
            ViewCompat.setAccessibilityHeading(heading, true)
        }
        ViewCompat.setAccessibilityLiveRegion(
            selectedDateTitleView,
            ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE
        )
    }

    private fun setupViewModel() {
        val database = ReminderDatabase.getDatabase(this)
        val repository = ReminderRepositoryImpl(database.reminderDao())
        googleCalendarAuthManager = GoogleCalendarAuthManager(applicationContext)
        googleCalendarSynchronizer = GoogleCalendarReminderSynchronizer(
            context = applicationContext,
            reminderRepository = repository
        )
        unifiedCalendarSynchronizer = UnifiedCalendarSynchronizer(
            context = applicationContext,
            reminderRepository = repository,
            googleCalendarSynchronizer = googleCalendarSynchronizer
        )
        microsoftCalendarAuthController = MicrosoftCalendarAuthProvider.get(applicationContext)
        calendarAutoSyncStateStore = CalendarAutoSyncStateStore(applicationContext)
        reminderScheduler = ReminderScheduler(applicationContext)
        val factory = CalendarViewModelFactory(
            getRemindersUseCase = GetRemindersUseCase(repository),
            deleteReminderUseCase = DeleteReminderUseCase(repository),
            updateReminderUseCase = UpdateReminderUseCase(repository),
            googleCalendarAuthManager = googleCalendarAuthManager,
            unifiedCalendarSynchronizer = unifiedCalendarSynchronizer,
            reminderScheduler = reminderScheduler
        )

        calendarViewModel = ViewModelProvider(this, factory)[CalendarViewModel::class.java]
    }

    private fun setupControls() {
        previousMonthButton.setOnClickListener {
            calendarViewModel.goToPreviousMonth()
        }

        nextMonthButton.setOnClickListener {
            calendarViewModel.goToNextMonth()
        }

        monthSelectorButton.setOnClickListener {
            lastRenderedState?.let(::showMonthPickerDialog)
        }

        yearSelectorButton.setOnClickListener {
            lastRenderedState?.let(::showYearPickerDialog)
        }

        legendFilterCards.forEach { (style, cardView) ->
            cardView.setOnClickListener {
                activeFilter = style
                lastRenderedState?.let(::renderState)
            }
        }

        legendShowAllButton.setOnClickListener {
            activeFilter = null
            lastRenderedState?.let(::renderState)
        }

        googleCalendarSyncButton.setOnClickListener {
            handleGoogleCalendarSyncClick()
        }

        microsoftCalendarSyncButton.setOnClickListener {
            handleMicrosoftCalendarSyncClick()
        }
        googleCalendarDisconnectButton.setOnClickListener {
            showDisconnectConfirmation(CalendarProvider.GOOGLE_CALENDAR)
        }
        microsoftCalendarDisconnectButton.setOnClickListener {
            showDisconnectConfirmation(CalendarProvider.MICROSOFT_CALENDAR)
        }

        createReminderButton.setOnClickListener {
            showReminderCreationOptions()
        }
        refreshGoogleCalendarStatus()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                calendarViewModel.uiState.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: CalendarUiState) {
        lastRenderedState = state
        val shouldAnimateMonthChange =
            lastRenderedMonthTitle != null && lastRenderedMonthTitle != state.monthTitle
        val shouldAnimateSelectedDate =
            lastSelectedDateTitle != null && lastSelectedDateTitle != state.selectedDateTitle

        renderSelectors(state)
        selectedDateTitleView.text = activeFilter?.let(::buildFilterTitle) ?: state.selectedDateTitle
        renderSelectedDateSummary(state)
        selectedDateTitleView.contentDescription = getString(
            R.string.calendar_day_accessibility,
            selectedDateTitleView.text,
            selectedDateSummaryView.text
        )
        holidaySummaryView.isVisible =
            activeFilter == null && state.selectedHolidays.isNotEmpty()
        holidaySummaryView.text = state.selectedHolidays.joinToString(separator = " - ")
        renderLegendFilters()

        calendarGridCard.isVisible = CalendarActionRules.shouldShowMonthGrid(activeFilter)
        renderCalendarDays(state.days)
        if (activeFilter == null) {
            renderDayDetails(
                reminders = state.selectedDateReminders,
                duplicateWarning = state.selectedDateDuplicateWarning
            )
        } else {
            renderFilteredItems(CalendarActionRules.filterItems(state.filteredItems, activeFilter))
        }
        renderError(state.errorMessage)
        renderVisibleMonthSyncError(state.syncError)
        if (state.showDeleteSyncSuccess) {
            calendarSyncInlineNoticeView.text = getString(
                R.string.calendar_sync_delete_finished_inline
            )
            calendarSyncInlineNoticeView.isVisible = true
        }
        if (shouldAnimateMonthChange) {
            animateMonthChange()
        }

        if (shouldAnimateSelectedDate) {
            animateSelectedDateFeedback()
        }

        lastRenderedMonthTitle = state.monthTitle
        lastSelectedDateTitle = state.selectedDateTitle
    }

    private fun renderSelectors(state: CalendarUiState) {
        monthSelectorButton.text = state.monthOptions.getOrNull(state.selectedMonthIndex).orEmpty()
        yearSelectorButton.text = state.selectedYear.toString()
    }

    private fun showMonthPickerDialog(state: CalendarUiState) {
        AlertDialog.Builder(this)
            .setTitle(R.string.calendar_month_label)
            .setSingleChoiceItems(
                state.monthOptions.toTypedArray(),
                state.selectedMonthIndex
            ) { dialog, which ->
                calendarViewModel.onMonthSelected(which)
                dialog.dismiss()
            }
            .show()
    }

    private fun showYearPickerDialog(state: CalendarUiState) {
        val years = state.yearOptions.map { it.toString() }.toTypedArray()
        val selectedIndex = state.yearOptions.indexOf(state.selectedYear).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(R.string.calendar_year_label)
            .setSingleChoiceItems(years, selectedIndex) { dialog, which ->
                years.getOrNull(which)?.toIntOrNull()?.let(calendarViewModel::onYearSelected)
                dialog.dismiss()
            }
            .show()
    }

    private fun renderCalendarDays(days: List<CalendarDayUiModel>) {
        calendarGrid.removeAllViews()
        calendarGrid.rowCount = ((days.size + 6) / 7).coerceAtLeast(1)

        val horizontalSpacing =
            resources.getDimensionPixelSize(R.dimen.calendar_day_spacing_horizontal)
        val verticalSpacing =
            resources.getDimensionPixelSize(R.dimen.calendar_day_spacing_vertical)

        days.forEachIndexed { index, day ->
            val dayView = LayoutInflater.from(this)
                .inflate(R.layout.item_calendar_day, calendarGrid, false)

            bindDayView(dayView, day)

            val layoutParams = GridLayout.LayoutParams(
                GridLayout.spec(index / 7, 1f),
                GridLayout.spec(index % 7, 1f)
            ).apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                setGravity(Gravity.FILL_HORIZONTAL)
                setMargins(
                    horizontalSpacing,
                    verticalSpacing,
                    horizontalSpacing,
                    verticalSpacing
                )
            }

            calendarGrid.addView(dayView, layoutParams)
        }
    }

    private fun bindDayView(dayView: View, day: CalendarDayUiModel) {
        val cardView = dayView.findViewById<MaterialCardView>(R.id.cardCalendarDay)
        val numberView = dayView.findViewById<TextView>(R.id.tvCalendarDayNumber)
        val todayMarker = dayView.findViewById<View>(R.id.tvCalendarTodayMarker)
        val indicatorViews = listOf(
            dayView.findViewById<View>(R.id.viewCalendarIndicatorOne),
            dayView.findViewById<View>(R.id.viewCalendarIndicatorTwo),
            dayView.findViewById<View>(R.id.viewCalendarIndicatorThree)
        )
        val summaryCountView = dayView.findViewById<TextView>(R.id.tvCalendarIndicatorCount)
        val shouldShowSummary = day.isCurrentMonth
        val visibleIndicators = day.indicators

        val numberColorRes = when {
            day.isSelected -> R.color.calendar_toolbar_text
            day.isSunday && !day.isCurrentMonth -> R.color.calendar_sunday_outside_text
            day.isSunday -> R.color.calendar_sunday_text
            !day.isCurrentMonth -> R.color.calendar_outside_month_text
            else -> R.color.calendar_title_text
        }

        numberView.text = day.dayNumber
        numberView.setTextColor(ContextCompat.getColor(this, numberColorRes))
        numberView.alpha = if (day.isCurrentMonth) 1f else 0.76f
        cardView.isSelected = day.isSelected
        val accessibilityDetails = buildList {
            if (day.isSelected) add(getString(R.string.calendar_day_selected))
            if (day.isToday) add(getString(R.string.calendar_day_today))
            if (day.isSunday) add(getString(R.string.calendar_day_sunday))
            day.holidayLabel?.takeIf { it.isNotBlank() }?.let(::add)
            day.indicators.forEach { indicator ->
                add(
                    when (indicator.style) {
                        CalendarIndicatorStyle.REMINDER ->
                            getString(R.string.calendar_legend_reminder)
                        CalendarIndicatorStyle.BIRTHDAY ->
                            getString(R.string.calendar_legend_birthday)
                        CalendarIndicatorStyle.HOLIDAY ->
                            getString(R.string.calendar_legend_holiday)
                        CalendarIndicatorStyle.COMPLETED ->
                            getString(R.string.calendar_legend_completed)
                    }
                )
            }
        }.distinct()
        cardView.contentDescription = getString(
            R.string.calendar_day_accessibility,
            day.date.format(accessibilityDateFormatter),
            accessibilityDetails.joinToString(separator = ". ")
                .ifBlank { getString(R.string.calendar_empty_no_visible_filters) }
        )
        ViewCompat.setAccessibilityDelegate(
            cardView,
            object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfoCompat
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = android.widget.Button::class.java.name
                    info.isSelected = day.isSelected
                }
            }
        )
        todayMarker.isVisible = day.isToday && day.isCurrentMonth
        if (day.isToday && day.isCurrentMonth) {
            val todayColorRes = if (day.isSelected) {
                R.color.calendar_indicator_birthday_background
            } else {
                R.color.calendar_day_today_stroke
            }
            todayMarker.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(this, todayColorRes))
        }

        val backgroundColorRes = when {
            !day.isCurrentMonth -> R.color.calendar_day_outside_background
            day.isSelected -> R.color.calendar_day_selected_background
            day.isToday -> R.color.calendar_day_today_background
            !day.holidayLabel.isNullOrBlank() ->
                R.color.calendar_day_holiday_background
            else -> R.color.calendar_day_background
        }

        val strokeColorRes = when {
            day.isSunday && !day.isCurrentMonth -> R.color.calendar_sunday_outside_outline
            day.isSunday -> R.color.calendar_sunday_outline
            !day.isCurrentMonth -> R.color.calendar_day_outside_stroke
            day.isSelected -> R.color.calendar_day_selected_stroke
            day.isToday -> R.color.calendar_day_today_stroke
            !day.holidayLabel.isNullOrBlank() ->
                R.color.calendar_day_holiday_stroke
            else -> R.color.calendar_day_stroke
        }

        cardView.setCardBackgroundColor(ContextCompat.getColor(this, backgroundColorRes))
        cardView.strokeColor = ContextCompat.getColor(this, strokeColorRes)
        cardView.strokeWidth = resources.getDimensionPixelSize(
            if (day.isSunday) {
                R.dimen.calendar_sunday_stroke
            } else {
                R.dimen.calendar_day_stroke
            }
        )
        cardView.radius = if (day.isSelected) 14f else 12f

        indicatorViews.forEachIndexed { index, indicatorView ->
            val indicator = visibleIndicators.getOrNull(index)
            indicatorView.isVisible = shouldShowSummary && indicator != null

            if (shouldShowSummary && indicator != null) {
                indicatorView.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this, indicator.style.dotColorResId)
                )
                indicatorView.alpha = 1f
            }
        }

        val hiddenIndicatorCount = (day.indicators.size - visibleIndicators.size).coerceAtLeast(0)
        summaryCountView.isVisible = shouldShowSummary && hiddenIndicatorCount > 0
        summaryCountView.text = "+$hiddenIndicatorCount"
        summaryCountView.alpha = 1f

        if (day.isSelected) {
            cardView.scaleX = 0.96f
            cardView.scaleY = 0.96f
            cardView.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(140L)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        } else {
            cardView.scaleX = 1f
            cardView.scaleY = 1f
        }

        cardView.setOnClickListener {
            calendarViewModel.onDaySelected(day.date)
        }
    }

    private fun animateMonthChange() {
        weekdayContainer.alpha = 0.75f
        calendarGrid.alpha = 0.3f
        calendarGrid.translationY = 8f
        plannerCardContainer.animate()
            .translationY(0f)
            .setDuration(1L)
            .start()
        weekdayContainer.animate()
            .alpha(1f)
            .setDuration(180L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
        calendarGrid.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(180L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun animateSelectedDateFeedback() {
        selectedDateTitleView.alpha = 0.45f
        selectedDateSummaryView.alpha = 0.45f
        detailContainer.alpha = 0.35f

        selectedDateTitleView.animate()
            .alpha(1f)
            .setDuration(160L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        selectedDateSummaryView.animate()
            .alpha(1f)
            .setDuration(160L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        detailContainer.animate()
            .alpha(1f)
            .setDuration(180L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun renderDayDetails(
        reminders: List<CalendarReminderDetailUiModel>,
        duplicateWarning: CalendarDuplicateWarningUiModel?
    ) {
        detailContainer.removeAllViews()

        if (duplicateWarning != null) {
            detailContainer.addView(
                LayoutInflater.from(this).inflate(
                    R.layout.item_calendar_duplicate_warning,
                    detailContainer,
                    false
                )
            )
            val logKey = "${duplicateWarning.selectedDate}:${duplicateWarning.duplicateCount}"
            if (lastDuplicateWarningLogKey != logKey) {
                CalendarSyncLogger.duplicateWarningShownInline(
                    duplicateCount = duplicateWarning.duplicateCount,
                    selectedDay = duplicateWarning.selectedDate.toString()
                )
                lastDuplicateWarningLogKey = logKey
            }
        } else {
            lastDuplicateWarningLogKey = null
        }

        reminders.forEach { detail ->
            detailContainer.addView(createDetailView(detail))
        }
    }

    private fun renderFilteredItems(items: List<CalendarFilteredListItemUiModel>) {
        detailContainer.removeAllViews()

        items.forEach { item ->
            val detail = item.detail ?: CalendarReminderDetailUiModel(
                id = "holiday-${item.date}-${item.holidayName}",
                title = item.holidayName.orEmpty(),
                detail = item.dateTitle,
                time = getString(R.string.calendar_type_holiday),
                type = ReminderType.DEFAULT,
                isCompleted = false,
                canDelete = false
            )
            detailContainer.addView(
                createDetailView(
                    detail = detail,
                    dateTitle = item.dateTitle,
                    forcedStyle = item.style
                )
            )
        }
    }

    private fun createDetailView(
        detail: CalendarReminderDetailUiModel,
        dateTitle: String? = null,
        forcedStyle: CalendarIndicatorStyle? = null
    ): View {
        val detailView = LayoutInflater.from(this)
            .inflate(R.layout.item_calendar_detail, detailContainer, false)

        val titleView = detailView.findViewById<TextView>(R.id.tvCalendarDetailTitle)
        val descriptionView =
            detailView.findViewById<TextView>(R.id.tvCalendarDetailDescription)
        val providerView = detailView.findViewById<TextView>(R.id.tvCalendarDetailProvider)
        val externalNoteView =
            detailView.findViewById<TextView>(R.id.tvCalendarDetailExternalNote)
        val timeView = detailView.findViewById<TextView>(R.id.tvCalendarDetailTime)
        val typeView = detailView.findViewById<TextView>(R.id.tvCalendarDetailType)
        val completedView = detailView.findViewById<TextView>(R.id.tvCalendarDetailCompleted)
        val scheduleConflictView =
            detailView.findViewById<TextView>(R.id.tvCalendarDetailScheduleConflict)
        val deleteButton = detailView.findViewById<ImageButton>(R.id.btnCalendarDetailDelete)
        val openMeetingButton = detailView.findViewById<MaterialButton>(R.id.btnCalendarOpenMeeting)
        val reactivateButton = detailView.findViewById<MaterialButton>(R.id.btnCalendarReactivate)
        val syncGoogleButton = detailView.findViewById<MaterialButton>(R.id.btnCalendarSyncGoogle)
        val syncMicrosoftButton =
            detailView.findViewById<MaterialButton>(R.id.btnCalendarSyncMicrosoft)
        val actionsContainer =
            detailView.findViewById<LinearLayout>(R.id.containerCalendarDetailActions)

        titleView.text = detail.title
        val safeDescription = MeetingContentSanitizer.cleanDescription(detail.detail)
        val renderedDescription = listOfNotNull(
            dateTitle?.takeIf { it.isNotBlank() },
            safeDescription.takeIf { it.isNotBlank() }
        )
            .distinct()
            .joinToString(separator = "\n")
        descriptionView.isVisible = renderedDescription.isNotBlank()
        descriptionView.text = renderedDescription
        val providerLines = detail.providerLines.filter(String::isNotBlank).distinct()
        providerView.isVisible = providerLines.isNotEmpty()
        providerView.text = providerLines.joinToString(separator = "\n")
        externalNoteView.isVisible = !detail.externalEditNote.isNullOrBlank()
        externalNoteView.text = detail.externalEditNote.orEmpty()
        timeView.text = detail.time
        typeView.text = when (forcedStyle ?: detail.toIndicatorStyle()) {
            CalendarIndicatorStyle.BIRTHDAY -> getString(R.string.calendar_type_birthday)
            CalendarIndicatorStyle.HOLIDAY -> getString(R.string.calendar_type_holiday)
            CalendarIndicatorStyle.COMPLETED -> getString(R.string.calendar_completed_label)
            CalendarIndicatorStyle.REMINDER -> getString(R.string.calendar_type_default)
        }

        val typeStyle = forcedStyle ?: when (detail.type) {
            ReminderType.BIRTHDAY -> CalendarIndicatorStyle.BIRTHDAY
            ReminderType.DEFAULT -> CalendarIndicatorStyle.REMINDER
        }

        typeView.setBackgroundResource(typeStyle.backgroundResId)
        typeView.setTextColor(ContextCompat.getColor(this, typeStyle.textColorResId))

        completedView.isVisible = detail.isCompleted && forcedStyle != CalendarIndicatorStyle.COMPLETED
        scheduleConflictView.isVisible = detail.hasScheduleConflict
        if (detail.isCompleted) {
            completedView.setBackgroundResource(CalendarIndicatorStyle.COMPLETED.backgroundResId)
            completedView.setTextColor(
                ContextCompat.getColor(this, CalendarIndicatorStyle.COMPLETED.textColorResId)
            )
        }

        deleteButton.isVisible = detail.canDelete
        deleteButton.setOnClickListener {
            showDeleteConfirmation(detail)
        }
        detailView.isClickable = detail.canEdit
        detailView.isFocusable = detail.canEdit
        detailView.setOnClickListener {
            if (detail.canEdit) {
                openReminderEditor(detail)
            }
        }
        val canOpenMeeting = MeetingUrlPolicy.isSupportedMeetingUrl(detail.meetingUrl)
        openMeetingButton.isVisible = canOpenMeeting
        CalendarSyncLogger.joinButtonVisibility(
            MeetingUrlPolicy.providerForUrl(detail.meetingUrl),
            canOpenMeeting
        )
        openMeetingButton.setOnClickListener {
            detail.meetingUrl?.let(::openMeetingUrl)
        }
        reactivateButton.isVisible = detail.canReactivate
        reactivateButton.setOnClickListener {
            calendarViewModel.reactivateCalendarItem(detail)
        }
        syncGoogleButton.isVisible = CalendarProvider.GOOGLE_CALENDAR in detail.syncActions
        syncGoogleButton.setOnClickListener {
            calendarViewModel.syncCalendarItemWithProvider(
                detail,
                CalendarProvider.GOOGLE_CALENDAR
            )
        }
        syncMicrosoftButton.isVisible =
            CalendarProvider.MICROSOFT_CALENDAR in detail.syncActions
        syncMicrosoftButton.setOnClickListener {
            calendarViewModel.syncCalendarItemWithProvider(
                detail,
                CalendarProvider.MICROSOFT_CALENDAR
            )
        }
        actionsContainer.isVisible = canOpenMeeting ||
                detail.canReactivate ||
                syncGoogleButton.isVisible ||
                syncMicrosoftButton.isVisible
        val reactivateLayoutParams = reactivateButton.layoutParams as LinearLayout.LayoutParams
        reactivateLayoutParams.bottomMargin = if (canOpenMeeting && detail.canReactivate) {
            resources.getDimensionPixelSize(R.dimen.calendar_detail_action_spacing)
        } else {
            0
        }
        reactivateButton.layoutParams = reactivateLayoutParams
        applySuspendedStyle(
            views = listOf(titleView, descriptionView, providerView, externalNoteView, timeView),
            isSuspended = detail.isSuspended
        )

        return detailView
    }

    private fun applySuspendedStyle(
        views: List<TextView>,
        isSuspended: Boolean
    ) {
        views.forEach { view ->
            view.paintFlags = if (isSuspended) {
                view.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                view.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            view.alpha = if (isSuspended) 0.62f else 1f
        }
    }

    private fun openMeetingUrl(url: String) {
        if (!MeetingUrlPolicy.isSupportedMeetingUrl(url)) {
            CalendarSyncLogger.meetingOpenResult(
                CalendarProvider.APP,
                appOpenAttempted = false,
                browserFallbackUsed = false,
                invalidUrlRejected = true,
                result = "no_crash"
            )
            return
        }
        val provider = MeetingUrlPolicy.providerForUrl(url) ?: CalendarProvider.APP
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        if (uri == null) {
            CalendarSyncLogger.meetingOpenResult(
                provider,
                appOpenAttempted = false,
                browserFallbackUsed = false,
                invalidUrlRejected = true,
                result = "no_crash"
            )
            return
        }
        val providerIntent = providerPackages(provider)
            .asSequence()
            .map { packageName ->
                Intent(Intent.ACTION_VIEW, uri).setPackage(packageName)
            }
            .firstOrNull { it.resolveActivity(packageManager) != null }
        CalendarSyncLogger.meetingLinkOpen(
            provider,
            "app_open_attempted",
            if (providerIntent != null) "yes" else "not_installed"
        )
        val result = MeetingOpenCoordinator.open(
            url = url,
            providerAppAvailable = providerIntent != null,
            openProviderApp = {
                try {
                    startActivity(providerIntent ?: throw ActivityNotFoundException())
                } catch (exception: ActivityNotFoundException) {
                    throw exception
                }
            },
            openBrowser = {
                CalendarSyncLogger.meetingLinkOpen(provider, "browser_fallback_used", "started")
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
        )
        CalendarSyncLogger.meetingLinkOpen(provider, "meeting_open_finished", result.name.lowercase())
        CalendarSyncLogger.meetingOpenResult(
            provider = provider,
            appOpenAttempted = providerIntent != null,
            browserFallbackUsed = result == MeetingOpenCoordinator.Result.BROWSER_OPENED ||
                    result == MeetingOpenCoordinator.Result.NO_HANDLER,
            invalidUrlRejected = result == MeetingOpenCoordinator.Result.INVALID_URL,
            result = result.name.lowercase()
        )
    }

    private fun providerPackages(provider: CalendarProvider): List<String> = when (provider) {
        CalendarProvider.GOOGLE_CALENDAR -> listOf("com.google.android.apps.tachyon")
        CalendarProvider.MICROSOFT_CALENDAR -> listOf("com.microsoft.teams2", "com.microsoft.teams")
        CalendarProvider.APP -> emptyList()
    }

    private fun renderSelectedDateSummary(state: CalendarUiState) {
        val currentFilter = activeFilter
        selectedDateActionsContainer.isVisible = currentFilter == null
        val selectedDate = state.days.firstOrNull { it.isSelected }?.date
        createReminderButton.isVisible = selectedDate?.let {
            CalendarActionRules.canCreateReminderOnDate(it)
        } ?: false

        if (currentFilter != null) {
            val filteredCount = state.filteredItems.count { it.style == currentFilter }
            selectedDateSummaryView.text = if (filteredCount == 0) {
                getString(R.string.calendar_filter_empty)
            } else {
                getString(R.string.calendar_filter_count, filteredCount)
            }
            emptyStateView.isVisible = filteredCount == 0
            emptyStateView.text = getString(R.string.calendar_filter_empty)
            return
        }

        val filteredReminders = state.selectedDateReminders
        val visibleHolidayCount = state.selectedHolidays.size

        selectedDateSummaryView.text = buildSelectedDateSummary(
            reminderCount = filteredReminders.size,
            holidayCount = visibleHolidayCount
        )

        emptyStateView.isVisible = filteredReminders.isEmpty() && visibleHolidayCount == 0
        emptyStateView.text = getString(R.string.unified_empty_day_title)
    }

    private fun renderLegendFilters() {
        val selectedStroke = ContextCompat.getColor(this, R.color.calendar_filter_selected_stroke)
        val unselectedBackground =
            ContextCompat.getColor(this, R.color.calendar_filter_unselected_background)
        val unselectedStroke =
            ContextCompat.getColor(this, R.color.calendar_filter_unselected_stroke)
        val unselectedText =
            ContextCompat.getColor(this, R.color.calendar_filter_unselected_text)

        legendFilterCards.forEach { (style, cardView) ->
            val isActive = style == activeFilter
            val backgroundColor = if (isActive) {
                ContextCompat.getColor(this, style.filterBackgroundColorResId)
            } else {
                unselectedBackground
            }

            cardView.setCardBackgroundColor(backgroundColor)
            cardView.strokeColor = if (isActive) selectedStroke else unselectedStroke
            cardView.strokeWidth = resources.getDimensionPixelSize(
                if (isActive) {
                    R.dimen.calendar_filter_stroke_selected
                } else {
                    R.dimen.calendar_filter_stroke_unselected
                }
            )
            cardView.isChecked = isActive
            cardView.contentDescription = buildString {
                append(legendFilterLabels[style]?.text?.toString().orEmpty())
                append(": ")
                append(if (isActive) "filtrando" else "sin filtro")
            }
            cardView.alpha = if (activeFilter == null || isActive) 1f else 0.72f

            legendFilterDots[style]?.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, style.dotColorResId)
            )
            legendFilterDots[style]?.alpha = if (activeFilter == null || isActive) 1f else 0.35f

            legendFilterLabels[style]?.setTextColor(
                if (activeFilter == null || isActive) {
                    ContextCompat.getColor(this, style.textColorResId)
                } else {
                    unselectedText
                }
            )
        }

        legendShowAllButton.text = if (activeFilter == null) {
            getString(R.string.calendar_legend_show_all)
        } else {
            getString(R.string.calendar_clear_filter)
        }
        legendShowAllButton.contentDescription = if (activeFilter == null) {
            getString(R.string.calendar_legend_show_all_accessibility)
        } else {
            getString(R.string.calendar_clear_filter_accessibility)
        }
    }

    private fun buildSelectedDateSummary(
        reminderCount: Int,
        holidayCount: Int
    ): String {
        return when {
            reminderCount > 0 && holidayCount > 0 ->
                "$reminderCount ${pluralize(reminderCount, "evento")} - $holidayCount ${pluralize(holidayCount, "feriado")}"

            reminderCount > 0 ->
                "$reminderCount ${pluralize(reminderCount, "evento")}"

            holidayCount > 0 ->
                "$holidayCount ${pluralize(holidayCount, "feriado")}"

            else -> "Sin actividad para la fecha seleccionada"
        }
    }

    private fun pluralize(value: Int, singular: String): String {
        return if (value == 1) singular else "${singular}s"
    }

    private fun renderError(errorMessage: String?) {
        if (!errorMessage.isNullOrBlank() && errorMessage != lastErrorMessage) {
            lastErrorMessage = errorMessage
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
        }

        if (errorMessage.isNullOrBlank()) {
            lastErrorMessage = null
        }
    }

    private fun openReminderCreation(source: ReminderSource) {
        val selectedDate = lastRenderedState?.days
            ?.firstOrNull { it.isSelected }
            ?.date
            ?: return
        val formattedDate = CalendarActionRules.formatPrefilledDate(selectedDate)

        startActivity(
            Intent(this, ManualReminderActivity::class.java).apply {
                putExtra(ManualReminderActivity.EXTRA_DEFAULT_SOURCE, source.name)
                putExtra(ManualReminderActivity.EXTRA_PREFILLED_DATE, formattedDate)
                putExtra(ManualReminderActivity.EXTRA_LOCK_DATE, CalendarActionRules.shouldLockDate(formattedDate))
            }
        )
    }

    private fun openReminderEditor(detail: CalendarReminderDetailUiModel) {
        val reminderId = detail.localReminderId ?: return
        openReminderEditor(reminderId)
    }

    private fun openAssistantForSelectedDate() {
        val selectedDate = lastRenderedState?.days
            ?.firstOrNull { it.isSelected }
            ?.date
            ?: return

        assistantFlowLauncher.launch(
            Intent(this, AssistantActivity::class.java).apply {
                putExtra(AssistantActivity.EXTRA_SELECTED_DATE, selectedDate.toString())
            }
        )
    }

    private fun openPasteTextReminder() {
        val selectedDate = lastRenderedState?.days
            ?.firstOrNull { it.isSelected }
            ?.date

        startActivity(
            Intent(this, PasteTextReminderActivity::class.java).apply {
                selectedDate?.let { date ->
                    putExtra(
                        PasteTextReminderActivity.EXTRA_SELECTED_DATE,
                        CalendarActionRules.formatPrefilledDate(date)
                    )
                }
            }
        )
        CalendarSyncLogger.ui("paste_text_reminder_opened_from_calendar")
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

    private fun openReminderEditor(reminderId: Int) {
        startActivity(
            Intent(this, ManualReminderActivity::class.java).apply {
                putExtra(ManualReminderActivity.EXTRA_REMINDER_ID, reminderId)
                putExtra(ManualReminderActivity.EXTRA_DEFAULT_SOURCE, ReminderSource.MANUAL.name)
            }
        )
    }

    private fun showReminderCreationOptions() {
        val content = LayoutInflater.from(this)
            .inflate(R.layout.dialog_reminder_creation_options, null, false)
        val voiceOption = content.findViewById<View>(R.id.cardReminderCreationVoice)
        val manualOption = content.findViewById<View>(R.id.cardReminderCreationManual)
        val cameraOption = content.findViewById<View>(R.id.cardReminderCreationCamera)
        val pasteOption = content.findViewById<View>(R.id.cardReminderCreationPaste)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.calendar_create_reminder)
            .setMessage(R.string.calendar_creation_options_supporting)
            .setView(content)
            .setNegativeButton(R.string.calendar_creation_close, null)
            .create()

        listOf(voiceOption, manualOption, cameraOption, pasteOption).forEach { option ->
            ViewCompat.setAccessibilityDelegate(
                option,
                object : AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(
                        host: View,
                        info: AccessibilityNodeInfoCompat
                    ) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.className = android.widget.Button::class.java.name
                    }
                }
            )
        }

        fun selectOption(action: () -> Unit) {
            dialog.dismiss()
            action()
        }

        voiceOption.setOnClickListener {
            selectOption(::openAssistantForSelectedDate)
        }
        manualOption.setOnClickListener {
            selectOption { openReminderCreation(ReminderSource.MANUAL) }
        }
        cameraOption.setOnClickListener {
            selectOption { openReminderCreation(ReminderSource.CAMERA) }
        }
        pasteOption.setOnClickListener {
            selectOption(::openPasteTextReminder)
        }
        dialog.setOnShowListener {
            voiceOption.requestFocus()
        }
        dialog.setOnDismissListener {
            createReminderButton.requestFocus()
        }
        dialog.show()
    }

    private fun showDeleteConfirmation(detail: CalendarReminderDetailUiModel) {
        showDeleteConfirmation(
            title = detail.title,
            providerExternalIds = detail.providerExternalIds
        ) { deleteExternalCalendars ->
            calendarViewModel.deleteCalendarItem(
                item = detail,
                deleteExternalCalendars = deleteExternalCalendars
            )
        }
    }

    private fun showDeleteConfirmation(
        title: String,
        providerExternalIds: Map<CalendarProvider, String>,
        onDelete: (Boolean) -> Unit
    ) {
        val linkedExternalProviders = providerExternalIds
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
                .setMessage(getString(R.string.calendar_delete_synced_message, title))
                .setItems(options) { dialog, which ->
                    when (which) {
                        0 -> onDelete(false)
                        1 -> onDelete(true)
                        else -> dialog.dismiss()
                    }
                }
                .show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.calendar_delete_title))
            .setMessage(getString(R.string.calendar_delete_message, title))
            .setNegativeButton(R.string.reminder_cancel_action, null)
            .setPositiveButton(R.string.delete_reminder) { _, _ ->
                onDelete(false)
            }
            .show()
    }

    private fun refreshGoogleCalendarStatus() {
        if (!::googleCalendarAuthManager.isInitialized) return

        val account = googleCalendarAuthManager.getSignedInAccount()
        val hasGoogleSession = googleCalendarAuthManager.hasConnectedSession(account)
        googleCalendarAuthManager.lastErrorCode?.let { code ->
            if (googleSyncError == null) {
                googleSyncError = getString(GoogleCalendarErrorUi.messageRes(code))
            }
        }
        val isMicrosoftConnected = ::microsoftCalendarAuthController.isInitialized &&
                microsoftCalendarAuthController.isConnected
        val hasMicrosoftSession = ::microsoftCalendarAuthController.isInitialized &&
                microsoftCalendarAuthController.hasSession
        microsoftCalendarAuthController.lastErrorCode?.let { code ->
            if (microsoftSyncError == null) {
                microsoftSyncError = getString(MicrosoftCalendarErrorUi.messageRes(code))
            }
        }
        googleProviderUiState = CalendarSyncUiPolicy.resolveGoogleProviderState(
            currentState = googleProviderUiState,
            syncEnabled = googleCalendarAuthManager.isSyncEnabled,
            hasSession = hasGoogleSession,
            hasError = googleSyncError != null || googleCalendarAuthManager.hasConnectionError
        )
        microsoftProviderUiState = CalendarSyncUiPolicy.resolveProviderState(
            currentState = microsoftProviderUiState,
            syncEnabled = microsoftCalendarAuthController.isSyncEnabled,
            hasSession = hasMicrosoftSession,
            hasError = microsoftSyncError != null
        )
        val panelState = CalendarSyncUiPolicy.resolve(
            googleConnected = googleCalendarAuthManager.isConnected(account),
            microsoftConnected = isMicrosoftConnected,
            microsoftAuthConfigured = microsoftCalendarAuthController.isAuthConfigured,
            googlePaused = hasGoogleSession && !googleCalendarAuthManager.isSyncEnabled,
            microsoftPaused = hasMicrosoftSession &&
                    !microsoftCalendarAuthController.isSyncEnabled
        )
        val googleMainAction = CalendarSyncUiPolicy.googleMainAction(
            state = googleProviderUiState,
            hasSession = hasGoogleSession
        )

        calendarSyncStatusView.text = when (panelState.status) {
            CalendarSyncStatus.GOOGLE_AND_MICROSOFT ->
                getString(R.string.calendar_sync_status_both_active)
            CalendarSyncStatus.GOOGLE ->
                getString(R.string.calendar_sync_status_google_active)
            CalendarSyncStatus.GOOGLE_PAUSED ->
                getString(R.string.calendar_sync_status_google_paused)
            CalendarSyncStatus.MICROSOFT_PAUSED ->
                getString(R.string.calendar_sync_status_microsoft_paused)
            CalendarSyncStatus.MICROSOFT ->
                getString(R.string.calendar_sync_status_microsoft_active)
            CalendarSyncStatus.NOT_CONNECTED ->
                getString(R.string.calendar_sync_status_none)
        }
        val lastSyncAt = listOfNotNull(
            calendarAutoSyncStateStore.get(CalendarProvider.GOOGLE_CALENDAR)
                .lastSuccessAtEpochMillis.takeIf { hasGoogleSession && it > 0L },
            calendarAutoSyncStateStore.get(CalendarProvider.MICROSOFT_CALENDAR)
                .lastSuccessAtEpochMillis.takeIf { hasMicrosoftSession && it > 0L }
        ).maxOrNull() ?: 0L
        calendarLastSyncView.text = CalendarSyncUiPolicy.lastSyncLabel(
            lastSyncAt,
            System.currentTimeMillis()
        ).orEmpty()
        calendarLastSyncView.isVisible = calendarLastSyncView.text.isNotBlank()

        googleCalendarSyncButton.text = getString(
            when {
                googleMainAction == CalendarSyncButtonAction.ACTIVATE_GOOGLE ->
                    R.string.calendar_sync_google_activate
                googleMainAction == CalendarSyncButtonAction.PAUSE_GOOGLE ->
                    R.string.calendar_sync_google_pause
                else -> R.string.calendar_sync_google_connect
            }
        )
        microsoftCalendarSyncButton.text = getString(
            when {
                panelState.microsoftButtonAction ==
                        CalendarSyncButtonAction.ACTIVATE_MICROSOFT ->
                    R.string.calendar_sync_microsoft_activate
                panelState.microsoftButtonAction ==
                        CalendarSyncButtonAction.PAUSE_MICROSOFT ->
                    R.string.calendar_sync_microsoft_pause
                else -> R.string.calendar_sync_microsoft_connect
            }
        )
        googleCalendarSyncButton.isEnabled =
            googleProviderUiState != CalendarProviderUiState.SYNCING &&
                    googleProviderUiState != CalendarProviderUiState.CONNECTING
        microsoftCalendarSyncButton.isEnabled =
            microsoftProviderUiState != CalendarProviderUiState.SYNCING
        googleCalendarDisconnectButton.isVisible = hasGoogleSession
        microsoftCalendarDisconnectButton.isVisible = hasMicrosoftSession
        calendarDisconnectActionsContainer.isVisible = hasGoogleSession || hasMicrosoftSession
        renderInlineSyncError()
        logProviderStateChanges()
    }

    private fun handleGoogleCalendarSyncClick() {
        CalendarSyncLogger.authButtonPressed(CalendarProvider.GOOGLE_CALENDAR)
        val hasSession = googleCalendarAuthManager.hasConnectedSession()
        val action = CalendarSyncUiPolicy.googleMainAction(
            state = googleProviderUiState,
            hasSession = hasSession
        )
        CalendarSyncLogger.googleButtonTapped(
            stateBefore = googleProviderUiState.name,
            action = action.name
        )
        if (action == CalendarSyncButtonAction.PAUSE_GOOGLE) {
            CalendarSyncLogger.ui(
                "google_sync_button",
                    mapOf("buttonAction" to "pause_google")
            )
            pauseCalendarProvider(CalendarProvider.GOOGLE_CALENDAR)
        } else {
            startGoogleCalendarAuth()
        }
    }

    private fun handleMicrosoftCalendarSyncClick() {
        CalendarSyncLogger.authButtonPressed(CalendarProvider.MICROSOFT_CALENDAR)
        CalendarSyncLogger.ui(
            "microsoft_sync_button",
            mapOf(
                "buttonAction" to when {
                    microsoftCalendarAuthController.isConnected -> "pause_microsoft"
                    microsoftCalendarAuthController.isAuthConfigured -> "connect_microsoft"
                    else -> "connect_microsoft"
                }
            )
        )

        if (microsoftCalendarAuthController.isConnected) {
            pauseCalendarProvider(CalendarProvider.MICROSOFT_CALENDAR)
            return
        }

        if (microsoftCalendarAuthController.hasSession) {
            activateMicrosoftCalendar()
            return
        }

        startMicrosoftCalendarAuth()
    }

    private fun startGoogleCalendarAuth(preserveActivationRequest: Boolean = false) {
        if (!preserveActivationRequest) {
            googleActivationRequested = googleCalendarAuthManager.hasConnectedSession() &&
                    !googleCalendarAuthManager.isSyncEnabled
        }
        googleSyncError = null
        googleRecoveryAttempted = false
        googleCalendarAuthManager.clearConnectionError()
        googleProviderUiState = CalendarProviderUiState.CONNECTING
        refreshGoogleCalendarStatus()
        CalendarSyncLogger.ui("google_sync_button", mapOf("buttonAction" to "connect_google"))
        CalendarSyncLogger.authStarted(CalendarProvider.GOOGLE_CALENDAR)
        googleCalendarAuthManager.prepareReconnect { decision ->
            runOnUiThread {
                when (decision) {
                    GoogleCalendarReconnectDecision.REUSE_VALID_SESSION -> {
                        verifyReusedGoogleSession()
                    }
                    GoogleCalendarReconnectDecision.NEEDS_LOGIN -> {
                        launchGoogleCalendarAuth()
                    }
                }
            }
        }
    }

    private fun completeGoogleSignIn(
        account: GoogleSignInAccount?
    ) {
        lifecycleScope.launch {
            runCatching {
                googleCalendarAuthManager.completeSignIn(
                    account = account,
                    activated = googleActivationRequested
                )
            }
                .onSuccess { connected ->
                    if (!connected) {
                        showGoogleInlineError(GoogleCalendarErrorCode.AUTH_SCOPE_DENIED)
                        return@onSuccess
                    }
                    CalendarSyncLogger.authSuccess(CalendarProvider.GOOGLE_CALENDAR)
                    googleSyncError = null
                    googleProviderUiState = CalendarProviderUiState.CONNECTED
                    refreshGoogleCalendarStatus()
                    CalendarSyncLogger.uiUpdated(
                        CalendarProvider.GOOGLE_CALENDAR,
                        "connected"
                    )
                    syncGoogleCalendarChanges()
                }
                .onFailure { exception ->
                    val recoverable = exception as? GoogleCalendarAuthException.UserActionRequired
                    if (
                        recoverable?.recoveryIntent != null &&
                        !googleRecoveryAttempted
                    ) {
                        googleRecoveryAttempted = true
                        CalendarSyncLogger.authLaunched(CalendarProvider.GOOGLE_CALENDAR)
                        runCatching {
                            googleCalendarRecoveryLauncher.launch(recoverable.recoveryIntent)
                        }.onFailure { launchFailure ->
                            val code = GoogleCalendarErrorCode.fromSignInFailure(launchFailure)
                            showGoogleInlineError(code)
                        }
                        return@onFailure
                    }
                    val code = GoogleCalendarErrorCode.fromSyncFailure(exception)
                    CalendarSyncLogger.googleAuthFailed(code.value)
                    showGoogleInlineError(code)
                }
        }
    }

    private fun verifyReusedGoogleSession() {
        lifecycleScope.launch {
            runCatching { googleCalendarAuthManager.reactivateExistingSession() }
                .onSuccess { connected ->
                    if (!connected) {
                        startGoogleCalendarAuth(preserveActivationRequest = true)
                        return@onSuccess
                    }
                    CalendarSyncLogger.authSuccess(CalendarProvider.GOOGLE_CALENDAR)
                    googleProviderUiState = CalendarProviderUiState.CONNECTED
                    refreshGoogleCalendarStatus()
                    syncGoogleCalendarChanges()
                }
                .onFailure { exception ->
                    val code = GoogleCalendarErrorCode.fromSyncFailure(exception)
                    if (code.invalidatesSession) {
                        startGoogleCalendarAuth(preserveActivationRequest = true)
                    } else {
                        showGoogleInlineError(code)
                    }
                }
        }
    }

    private fun launchGoogleCalendarAuth() {
        runCatching {
            CalendarSyncLogger.authLaunched(CalendarProvider.GOOGLE_CALENDAR)
            googleCalendarSignInLauncher.launch(googleCalendarAuthManager.buildSignInIntent())
        }.onFailure { exception ->
            val code = GoogleCalendarErrorCode.fromSignInFailure(exception)
            CalendarSyncLogger.googleAuthFailed(code.value)
            showGoogleInlineError(code)
        }
    }

    private fun startMicrosoftCalendarAuth() {
        microsoftSyncError = null
        microsoftProviderUiState = CalendarProviderUiState.SYNCING
        refreshGoogleCalendarStatus()
        microsoftCalendarAuthController.signIn(this) { result ->
            runOnUiThread {
                when (result) {
                    MicrosoftCalendarAuthResult.Success -> {
                        microsoftSyncError = null
                        refreshGoogleCalendarStatus()
                        syncMicrosoftCalendarChanges()
                    }
                    MicrosoftCalendarAuthResult.Cancelled -> {
                        showMicrosoftInlineError(MicrosoftCalendarErrorCode.AUTH_CANCELLED)
                    }
                    MicrosoftCalendarAuthResult.MissingConfig -> {
                        CalendarSyncLogger.fallback(
                            provider = CalendarProvider.MICROSOFT_CALENDAR,
                            action = "auth_start",
                            fallbackReason = "missing_config"
                        )
                        showMicrosoftInlineError(
                            MicrosoftCalendarErrorCode.AUTH_MSAL_CLIENT_ERROR
                        )
                    }
                    is MicrosoftCalendarAuthResult.Error -> {
                        CalendarSyncLogger.error(
                            provider = CalendarProvider.MICROSOFT_CALENDAR,
                            action = "auth",
                            fallbackReason = result.cause.javaClass.simpleName
                        )
                        showMicrosoftInlineError(
                            MicrosoftCalendarErrorCode.fromAuthFailure(result.cause)
                        )
                    }
                }
            }
        }
    }

    private fun showDisconnectConfirmation(provider: CalendarProvider) {
        val providerName = when (provider) {
            CalendarProvider.GOOGLE_CALENDAR -> "Google"
            CalendarProvider.MICROSOFT_CALENDAR -> "Microsoft"
            CalendarProvider.APP -> return
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.calendar_sync_disconnect_title, providerName))
            .setMessage(R.string.calendar_sync_disconnect_message)
            .setNegativeButton(R.string.reminder_cancel_action, null)
            .setPositiveButton(R.string.calendar_sync_disconnect_confirm) { _, _ ->
                disconnectCalendarProvider(provider)
            }
            .show()
    }

    private fun pauseCalendarProvider(provider: CalendarProvider) {
        when (provider) {
            CalendarProvider.GOOGLE_CALENDAR -> {
                googleCalendarAuthManager.pauseSync()
                handleProviderPauseResult(provider, Result.success(Unit))
            }
            CalendarProvider.MICROSOFT_CALENDAR ->
                handleProviderPauseResult(
                    provider,
                    runCatching { microsoftCalendarAuthController.pauseSync() }
                )
            CalendarProvider.APP -> Unit
        }
    }

    private fun handleProviderPauseResult(
        provider: CalendarProvider,
        result: Result<Unit>
    ) {
        result.onSuccess {
            when (provider) {
                CalendarProvider.GOOGLE_CALENDAR -> {
                    googleSyncError = null
                    googleProviderUiState = CalendarProviderUiState.PAUSED
                    calendarSyncInlineNoticeView.text = getString(
                        R.string.calendar_sync_google_disabled_inline
                    )
                }
                CalendarProvider.MICROSOFT_CALENDAR -> {
                    microsoftSyncError = null
                    microsoftProviderUiState = CalendarProviderUiState.PAUSED
                    calendarSyncInlineNoticeView.text = getString(
                        R.string.calendar_sync_microsoft_disabled_inline
                    )
                }
                CalendarProvider.APP -> return@onSuccess
            }
            calendarSyncInlineNoticeView.isVisible = true
            CalendarSyncLogger.deactivate(provider, phase = "finished")
            refreshGoogleCalendarStatus()
        }.onFailure { exception ->
            CalendarSyncLogger.error(
                provider,
                action = "deactivate",
                fallbackReason = exception.javaClass.simpleName
            )
            showProviderInlineError(provider, exception)
        }
    }

    private fun disconnectCalendarProvider(provider: CalendarProvider) {
        val callback: (Result<Unit>) -> Unit = { result ->
            runOnUiThread {
                result.onSuccess {
                    when (provider) {
                        CalendarProvider.GOOGLE_CALENDAR -> {
                            googleSyncError = null
                            googleProviderUiState = CalendarProviderUiState.DISCONNECTED
                        }
                        CalendarProvider.MICROSOFT_CALENDAR -> {
                            microsoftSyncError = null
                            microsoftProviderUiState = CalendarProviderUiState.DISCONNECTED
                        }
                        CalendarProvider.APP -> Unit
                    }
                    calendarAutoSyncStateStore.clear(provider)
                    refreshGoogleCalendarStatus()
                }.onFailure { error ->
                    showProviderInlineError(provider, error)
                }
            }
        }
        when (provider) {
            CalendarProvider.GOOGLE_CALENDAR -> googleCalendarAuthManager.disconnect(callback)
            CalendarProvider.MICROSOFT_CALENDAR ->
                microsoftCalendarAuthController.signOut(callback)
            CalendarProvider.APP -> Unit
        }
    }

    private fun activateMicrosoftCalendar() {
        microsoftSyncError = null
        microsoftProviderUiState = CalendarProviderUiState.SYNCING
        refreshGoogleCalendarStatus()
        microsoftCalendarAuthController.activateExistingSession { result ->
            runOnUiThread {
                when (result) {
                    MicrosoftCalendarAuthResult.Success -> {
                        microsoftProviderUiState = CalendarProviderUiState.CONNECTED
                        syncMicrosoftCalendarChanges()
                    }
                    MicrosoftCalendarAuthResult.Cancelled ->
                        showMicrosoftInlineError(MicrosoftCalendarErrorCode.AUTH_CANCELLED)
                    MicrosoftCalendarAuthResult.MissingConfig ->
                        showMicrosoftInlineError(MicrosoftCalendarErrorCode.AUTH_MSAL_CLIENT_ERROR)
                    is MicrosoftCalendarAuthResult.Error -> {
                        val code = MicrosoftCalendarErrorCode.fromAuthFailure(result.cause)
                        if (code.invalidatesSession) {
                            startMicrosoftCalendarAuth()
                        } else {
                            showMicrosoftInlineError(code)
                        }
                    }
                }
            }
        }
    }

    private fun showInlineSyncError(provider: CalendarProvider, reason: String) {
        if (provider == CalendarProvider.GOOGLE_CALENDAR) {
            showGoogleInlineError(GoogleCalendarErrorCode.fromLegacyReason(reason))
            return
        }
        showMicrosoftInlineError(
            MicrosoftCalendarErrorCode.fromStoredValue(reason)
                ?: MicrosoftCalendarErrorCode.fromFailure(IllegalStateException(reason))
        )
    }

    private fun showProviderInlineError(provider: CalendarProvider, error: Throwable) {
        when (provider) {
            CalendarProvider.GOOGLE_CALENDAR ->
                showGoogleInlineError(GoogleCalendarErrorCode.fromSyncFailure(error))
            CalendarProvider.MICROSOFT_CALENDAR ->
                showMicrosoftInlineError(MicrosoftCalendarErrorCode.fromFailure(error))
            CalendarProvider.APP -> Unit
        }
    }

    private fun showMicrosoftInlineError(code: MicrosoftCalendarErrorCode) {
        microsoftCalendarAuthController.markConnectionError(code)
        microsoftSyncError = getString(MicrosoftCalendarErrorUi.messageRes(code))
        microsoftProviderUiState = CalendarProviderUiState.ERROR
        CalendarSyncLogger.inlineErrorShown(CalendarProvider.MICROSOFT_CALENDAR, code.value)
        refreshGoogleCalendarStatus()
    }

    private fun showGoogleInlineError(code: GoogleCalendarErrorCode) {
        if (
            !googleCalendarAuthManager.hasConnectionError ||
            googleCalendarAuthManager.lastErrorCode != code
        ) {
            googleCalendarAuthManager.markConnectionError(code)
        }
        googleSyncError = getString(GoogleCalendarErrorUi.messageRes(code))
        googleProviderUiState = CalendarProviderUiState.ERROR
        CalendarSyncLogger.inlineErrorShown(CalendarProvider.GOOGLE_CALENDAR, code.value)
        refreshGoogleCalendarStatus()
    }

    private fun renderVisibleMonthSyncError(error: CalendarSyncInlineError?) {
        if (error == lastVisibleMonthSyncError) return
        val previousError = lastVisibleMonthSyncError
        lastVisibleMonthSyncError = error
        if (error != null) {
            if (error.provider == CalendarProvider.GOOGLE_CALENDAR) {
                showGoogleInlineError(GoogleCalendarErrorCode.fromLegacyReason(error.reason))
            } else {
                showMicrosoftInlineError(
                    MicrosoftCalendarErrorCode.fromStoredValue(error.reason)
                        ?: MicrosoftCalendarErrorCode.fromFailure(
                            IllegalStateException(error.reason)
                        )
                )
            }
            return
        }
        when (previousError?.provider) {
            CalendarProvider.GOOGLE_CALENDAR -> {
                googleSyncError = null
                googleCalendarAuthManager.clearConnectionError()
            }
            CalendarProvider.MICROSOFT_CALENDAR -> {
                microsoftSyncError = null
                microsoftCalendarAuthController.clearConnectionError()
                CalendarSyncLogger.inlineErrorCleared(CalendarProvider.MICROSOFT_CALENDAR)
            }
            else -> Unit
        }
        refreshGoogleCalendarStatus()
    }

    private fun renderInlineSyncError() {
        val message = googleSyncError ?: microsoftSyncError
        calendarSyncErrorContainer.isVisible = message != null
        calendarSyncErrorView.text = message.orEmpty()
    }

    private fun logProviderStateChanges() {
        if (lastLoggedGoogleState != googleProviderUiState) {
            val previousState = lastLoggedGoogleState
            lastLoggedGoogleState = googleProviderUiState
            CalendarSyncLogger.providerStateChanged(
                CalendarProvider.GOOGLE_CALENDAR,
                googleProviderUiState.name,
                previousState?.name
            )
        }
        if (lastLoggedMicrosoftState != microsoftProviderUiState) {
            val previousState = lastLoggedMicrosoftState
            lastLoggedMicrosoftState = microsoftProviderUiState
            CalendarSyncLogger.providerStateChanged(
                CalendarProvider.MICROSOFT_CALENDAR,
                microsoftProviderUiState.name,
                previousState?.name
            )
        }
    }

    private fun syncGoogleCalendarChanges() {
        googleProviderUiState = CalendarProviderUiState.SYNCING
        refreshGoogleCalendarStatus()
        lifecycleScope.launch {
            runCatching {
                googleCalendarSynchronizer.syncPendingReminders()
            }.onSuccess { summary ->
                showInlineDeleteSuccess(summary.completedDeleteCount)
                val failureCode = summary.failureCode
                if (failureCode == null) {
                    googleSyncError = null
                    googleCalendarAuthManager.clearConnectionError()
                    calendarAutoSyncStateStore.recordSuccess(
                        CalendarProvider.GOOGLE_CALENDAR,
                        System.currentTimeMillis()
                    )
                    googleProviderUiState = CalendarProviderUiState.CONNECTED
                    calendarViewModel.reloadCalendar(forceExternalSync = true)
                    refreshGoogleCalendarStatus()
                } else {
                    CalendarSyncLogger.error(
                        CalendarProvider.GOOGLE_CALENDAR,
                        action = "sync_google_button",
                        fallbackReason = failureCode.value
                    )
                    calendarAutoSyncStateStore.recordFailure(
                        CalendarProvider.GOOGLE_CALENDAR,
                        failureCode.value,
                        System.currentTimeMillis()
                    )
                    showGoogleInlineError(failureCode)
                    calendarViewModel.reloadCalendar()
                }
            }.onFailure { exception ->
                val code = GoogleCalendarErrorCode.fromSyncFailure(exception)
                CalendarSyncLogger.error(
                    CalendarProvider.GOOGLE_CALENDAR,
                    action = "sync_google_button",
                    fallbackReason = code.value
                )
                showGoogleInlineError(code)
                calendarAutoSyncStateStore.recordFailure(
                    CalendarProvider.GOOGLE_CALENDAR,
                    code.value,
                    System.currentTimeMillis()
                )
            }
        }
    }

    private fun syncMicrosoftCalendarChanges() {
        microsoftProviderUiState = CalendarProviderUiState.SYNCING
        refreshGoogleCalendarStatus()
        lifecycleScope.launch {
            runCatching {
                unifiedCalendarSynchronizer.syncMicrosoftCalendar()
            }.onSuccess { summary ->
                showInlineDeleteSuccess(summary.completedDeleteCount)
                calendarViewModel.reloadCalendar()
                val failureCode = summary.microsoftFailureCode
                if (failureCode == null) {
                    val hadInlineError = microsoftSyncError != null
                    microsoftSyncError = null
                    microsoftCalendarAuthController.clearConnectionError()
                    if (hadInlineError) {
                        CalendarSyncLogger.inlineErrorCleared(
                            CalendarProvider.MICROSOFT_CALENDAR
                        )
                    }
                    calendarAutoSyncStateStore.recordSuccess(
                        CalendarProvider.MICROSOFT_CALENDAR,
                        System.currentTimeMillis()
                    )
                    microsoftProviderUiState = CalendarProviderUiState.CONNECTED
                    refreshGoogleCalendarStatus()
                } else {
                    calendarAutoSyncStateStore.recordFailure(
                        CalendarProvider.MICROSOFT_CALENDAR,
                        failureCode.value,
                        System.currentTimeMillis(),
                        summary.microsoftRetryAfterMillis
                    )
                    showMicrosoftInlineError(failureCode)
                }
            }.onFailure { exception ->
                val code = MicrosoftCalendarErrorCode.fromFailure(exception)
                calendarAutoSyncStateStore.recordFailure(
                    CalendarProvider.MICROSOFT_CALENDAR,
                    code.value,
                    System.currentTimeMillis(),
                    (exception as? com.luistureo.voicereminderapp.core.calendar.microsoft.MicrosoftGraphApiException)
                        ?.retryAfterSeconds?.times(1_000L)
                )
                CalendarSyncLogger.error(
                    CalendarProvider.MICROSOFT_CALENDAR,
                    action = "sync_microsoft_button",
                    fallbackReason = code.value
                )
                showMicrosoftInlineError(code)
            }
        }
    }

    private fun showInlineDeleteSuccess(completedDeleteCount: Int) {
        if (completedDeleteCount <= 0) return
        calendarSyncInlineNoticeView.text = getString(
            R.string.calendar_sync_delete_finished_inline
        )
        calendarSyncInlineNoticeView.isVisible = true
    }

    private fun buildFilterTitle(style: CalendarIndicatorStyle): String {
        return when (style) {
            CalendarIndicatorStyle.REMINDER -> getString(R.string.calendar_filter_title_reminder)
            CalendarIndicatorStyle.BIRTHDAY -> getString(R.string.calendar_filter_title_birthday)
            CalendarIndicatorStyle.HOLIDAY -> getString(R.string.calendar_filter_title_holiday)
            CalendarIndicatorStyle.COMPLETED -> getString(R.string.calendar_filter_title_completed)
        }
    }

    private val CalendarIndicatorStyle.backgroundResId: Int
        get() = when (this) {
            CalendarIndicatorStyle.REMINDER -> R.drawable.bg_calendar_indicator_reminder
            CalendarIndicatorStyle.BIRTHDAY -> R.drawable.bg_calendar_indicator_birthday
            CalendarIndicatorStyle.HOLIDAY -> R.drawable.bg_calendar_indicator_holiday
            CalendarIndicatorStyle.COMPLETED -> R.drawable.bg_calendar_indicator_completed
        }

    private val CalendarIndicatorStyle.textColorResId: Int
        get() = when (this) {
            CalendarIndicatorStyle.REMINDER -> R.color.calendar_indicator_reminder_text
            CalendarIndicatorStyle.BIRTHDAY -> R.color.calendar_indicator_birthday_text
            CalendarIndicatorStyle.HOLIDAY -> R.color.calendar_indicator_holiday_text
            CalendarIndicatorStyle.COMPLETED -> R.color.calendar_indicator_completed_text
        }

    private val CalendarIndicatorStyle.dotColorResId: Int
        get() = when (this) {
            CalendarIndicatorStyle.REMINDER -> R.color.calendar_indicator_reminder_text
            CalendarIndicatorStyle.BIRTHDAY -> R.color.calendar_indicator_birthday_text
            CalendarIndicatorStyle.HOLIDAY -> R.color.calendar_indicator_holiday_text
            CalendarIndicatorStyle.COMPLETED -> R.color.calendar_indicator_completed_text
        }

    private val CalendarIndicatorStyle.filterBackgroundColorResId: Int
        get() = when (this) {
            CalendarIndicatorStyle.REMINDER -> R.color.calendar_indicator_reminder_background
            CalendarIndicatorStyle.BIRTHDAY -> R.color.calendar_indicator_birthday_background
            CalendarIndicatorStyle.HOLIDAY -> R.color.calendar_indicator_holiday_background
            CalendarIndicatorStyle.COMPLETED -> R.color.calendar_indicator_completed_background
        }

    private fun CalendarReminderDetailUiModel.toIndicatorStyle(): CalendarIndicatorStyle {
        return when {
            isCompleted -> CalendarIndicatorStyle.COMPLETED
            type == ReminderType.BIRTHDAY -> CalendarIndicatorStyle.BIRTHDAY
            else -> CalendarIndicatorStyle.REMINDER
        }
    }

    companion object {
        private const val STATE_ACTIVE_FILTER = "state_calendar_active_filter"
        private const val STATE_SELECTED_DATE = "state_calendar_selected_date"
    }
}
