package com.luistureo.voicereminderapp.presentation.calendar

import android.os.Bundle
import android.content.Intent
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
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.alarm.ReminderScheduler
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarAuthManager
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarReminderSynchronizer
import com.luistureo.voicereminderapp.data.local.database.ReminderDatabase
import com.luistureo.voicereminderapp.data.repository.ReminderRepositoryImpl
import com.luistureo.voicereminderapp.domain.model.ReminderSource
import com.luistureo.voicereminderapp.domain.model.ReminderType
import com.luistureo.voicereminderapp.domain.usecase.DeleteReminderUseCase
import com.luistureo.voicereminderapp.domain.usecase.GetRemindersUseCase
import com.luistureo.voicereminderapp.presentation.manual.ManualReminderActivity
import kotlinx.coroutines.launch

class CalendarActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var previousMonthButton: ImageButton
    private lateinit var nextMonthButton: ImageButton
    private lateinit var monthSelectorButton: MaterialButton
    private lateinit var yearSelectorButton: MaterialButton
    private lateinit var plannerCardContainer: LinearLayout
    private lateinit var googleCalendarStatusView: TextView
    private lateinit var googleCalendarSyncButton: MaterialButton
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

    private var activeFilter: CalendarIndicatorStyle? = null
    private var lastErrorMessage: String? = null
    private var lastRenderedMonthTitle: String? = null
    private var lastSelectedDateTitle: String? = null
    private var lastRenderedState: CalendarUiState? = null

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
        setContentView(R.layout.activity_calendar)

        initViews()
        setupToolbar()
        setupViewModel()
        setupControls()
        observeState()
    }

    override fun onResume() {
        super.onResume()
        calendarViewModel.reloadCalendar()
        refreshGoogleCalendarStatus()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbarCalendar)
        previousMonthButton = findViewById(R.id.btnPreviousMonth)
        nextMonthButton = findViewById(R.id.btnNextMonth)
        monthSelectorButton = findViewById(R.id.btnMonthSelector)
        yearSelectorButton = findViewById(R.id.btnYearSelector)
        plannerCardContainer = findViewById(R.id.containerCalendarPlannerCard)
        googleCalendarStatusView = findViewById(R.id.tvCalendarGoogleStatus)
        googleCalendarSyncButton = findViewById(R.id.btnCalendarGoogleSync)
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
    }

    private fun setupToolbar() {
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupViewModel() {
        val database = ReminderDatabase.getDatabase(this)
        val repository = ReminderRepositoryImpl(database.reminderDao())
        googleCalendarAuthManager = GoogleCalendarAuthManager(applicationContext)
        googleCalendarSynchronizer = GoogleCalendarReminderSynchronizer(
            context = applicationContext,
            reminderRepository = repository
        )
        val factory = CalendarViewModelFactory(
            getRemindersUseCase = GetRemindersUseCase(repository),
            deleteReminderUseCase = DeleteReminderUseCase(repository),
            googleCalendarAuthManager = googleCalendarAuthManager,
            googleCalendarSynchronizer = googleCalendarSynchronizer,
            reminderScheduler = ReminderScheduler(applicationContext)
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
            if (googleCalendarAuthManager.hasCalendarPermission()) {
                syncPendingGoogleCalendarChanges()
            } else {
                googleCalendarSignInLauncher.launch(
                    googleCalendarAuthManager.buildSignInIntent()
                )
            }
        }

        createReminderButton.setOnClickListener {
            showCreateReminderChoice()
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
        holidaySummaryView.isVisible =
            activeFilter == null && state.selectedHolidays.isNotEmpty()
        holidaySummaryView.text = state.selectedHolidays.joinToString(separator = " - ")
        renderLegendFilters()

        calendarGridCard.isVisible = CalendarActionRules.shouldShowMonthGrid(activeFilter)
        renderCalendarDays(state.days)
        if (activeFilter == null) {
            renderDayDetails(state.selectedDateReminders)
        } else {
            renderFilteredItems(CalendarActionRules.filterItems(state.filteredItems, activeFilter))
        }
        renderError(state.errorMessage)

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

        val numberColorRes = if (day.isSelected) {
            R.color.calendar_toolbar_text
        } else if (!day.isCurrentMonth) {
            R.color.calendar_outside_month_text
        } else {
            R.color.calendar_title_text
        }

        numberView.text = day.dayNumber
        numberView.setTextColor(ContextCompat.getColor(this, numberColorRes))
        numberView.alpha = if (day.isCurrentMonth) 1f else 0.76f
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
            !day.isCurrentMonth -> R.color.calendar_day_outside_stroke
            day.isSelected -> R.color.calendar_day_selected_stroke
            day.isToday -> R.color.calendar_day_today_stroke
            !day.holidayLabel.isNullOrBlank() ->
                R.color.calendar_day_holiday_stroke
            else -> R.color.calendar_day_stroke
        }

        cardView.setCardBackgroundColor(ContextCompat.getColor(this, backgroundColorRes))
        cardView.strokeColor = ContextCompat.getColor(this, strokeColorRes)
        cardView.strokeWidth = resources.getDimensionPixelSize(R.dimen.calendar_day_stroke)
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

    private fun renderDayDetails(reminders: List<CalendarReminderDetailUiModel>) {
        detailContainer.removeAllViews()

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
        val timeView = detailView.findViewById<TextView>(R.id.tvCalendarDetailTime)
        val typeView = detailView.findViewById<TextView>(R.id.tvCalendarDetailType)
        val completedView = detailView.findViewById<TextView>(R.id.tvCalendarDetailCompleted)
        val deleteButton = detailView.findViewById<ImageButton>(R.id.btnCalendarDetailDelete)

        titleView.text = detail.title
        descriptionView.text = listOfNotNull(dateTitle, detail.detail.takeIf { it.isNotBlank() })
            .joinToString(separator = "\n")
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

        return detailView
    }

    private fun renderSelectedDateSummary(state: CalendarUiState) {
        val currentFilter = activeFilter
        selectedDateActionsContainer.isVisible = currentFilter == null

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
        emptyStateView.text = getString(R.string.calendar_empty_no_visible_filters)
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

    private fun showCreateReminderChoice() {
        val options = arrayOf(
            getString(R.string.calendar_create_manual_choice),
            getString(R.string.calendar_create_camera_choice)
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.calendar_create_choice_title)
            .setItems(options) { _, which ->
                val source = CalendarActionRules.creationChoices()[which]
                openReminderCreation(source)
            }
            .show()
    }

    private fun showDeleteConfirmation(detail: CalendarReminderDetailUiModel) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.calendar_delete_title))
            .setMessage(getString(R.string.calendar_delete_message, detail.title))
            .setNegativeButton(R.string.reminder_cancel_action, null)
            .setPositiveButton(R.string.delete_reminder) { _, _ ->
                calendarViewModel.deleteCalendarItem(detail)
            }
            .show()
    }

    private fun refreshGoogleCalendarStatus() {
        if (!::googleCalendarAuthManager.isInitialized) return

        val account = googleCalendarAuthManager.getSignedInAccount()
        val isConnected = googleCalendarAuthManager.hasCalendarPermission(account)

        googleCalendarStatusView.text = if (isConnected) {
            getString(
                R.string.google_calendar_connected,
                account?.email.orEmpty().ifBlank { getString(R.string.google_calendar_account) }
            )
        } else {
            getString(R.string.calendar_google_connect_hint)
        }

        googleCalendarSyncButton.text = if (isConnected) {
            getString(R.string.google_calendar_sync_button)
        } else {
            getString(R.string.calendar_google_connect_button)
        }
    }

    private fun syncPendingGoogleCalendarChanges() {
        lifecycleScope.launch {
            runCatching {
                googleCalendarSynchronizer.syncPendingReminders()
            }.onSuccess { summary ->
                calendarViewModel.reloadCalendar()
                refreshGoogleCalendarStatus()
                Toast.makeText(
                    this@CalendarActivity,
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
                    this@CalendarActivity,
                    exception.message ?: getString(R.string.google_calendar_sync_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
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
}
