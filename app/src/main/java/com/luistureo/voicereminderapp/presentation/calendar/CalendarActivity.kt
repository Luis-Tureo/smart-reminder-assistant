package com.luistureo.voicereminderapp.presentation.calendar

import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.content.res.ColorStateList
import android.widget.ArrayAdapter
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.data.local.database.ReminderDatabase
import com.luistureo.voicereminderapp.data.repository.ReminderRepositoryImpl
import com.luistureo.voicereminderapp.domain.model.ReminderType
import com.luistureo.voicereminderapp.domain.usecase.GetRemindersUseCase
import kotlinx.coroutines.launch

class CalendarActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var monthTitleView: TextView
    private lateinit var previousMonthButton: ImageButton
    private lateinit var nextMonthButton: ImageButton
    private lateinit var monthSelectorView: MaterialAutoCompleteTextView
    private lateinit var yearSelectorView: MaterialAutoCompleteTextView
    private lateinit var plannerCardContainer: LinearLayout
    private lateinit var weekdayContainer: LinearLayout
    private lateinit var calendarGrid: GridLayout
    private lateinit var selectedDateTitleView: TextView
    private lateinit var selectedDateSummaryView: TextView
    private lateinit var holidaySummaryView: TextView
    private lateinit var emptyStateView: TextView
    private lateinit var detailContainer: LinearLayout

    private lateinit var calendarViewModel: CalendarViewModel

    private var isUpdatingSelectors: Boolean = false
    private var lastErrorMessage: String? = null
    private var lastRenderedMonthTitle: String? = null
    private var lastSelectedDateTitle: String? = null

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
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbarCalendar)
        monthTitleView = findViewById(R.id.tvCalendarMonthTitle)
        previousMonthButton = findViewById(R.id.btnPreviousMonth)
        nextMonthButton = findViewById(R.id.btnNextMonth)
        monthSelectorView = findViewById(R.id.inputMonthSelector)
        yearSelectorView = findViewById(R.id.inputYearSelector)
        plannerCardContainer = findViewById(R.id.containerCalendarPlannerCard)
        weekdayContainer = findViewById(R.id.containerCalendarWeekdays)
        calendarGrid = findViewById(R.id.gridCalendarDays)
        selectedDateTitleView = findViewById(R.id.tvSelectedDateTitle)
        selectedDateSummaryView = findViewById(R.id.tvSelectedDateSummary)
        holidaySummaryView = findViewById(R.id.tvHolidaySummary)
        emptyStateView = findViewById(R.id.tvCalendarEmptyState)
        detailContainer = findViewById(R.id.containerDayDetails)
    }

    private fun setupToolbar() {
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupViewModel() {
        val database = ReminderDatabase.getDatabase(this)
        val repository = ReminderRepositoryImpl(database.reminderDao())
        val factory = CalendarViewModelFactory(
            getRemindersUseCase = GetRemindersUseCase(repository)
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

        monthSelectorView.setOnItemClickListener { _, _, position, _ ->
            if (!isUpdatingSelectors) {
                calendarViewModel.onMonthSelected(position)
            }
        }

        yearSelectorView.setOnItemClickListener { _, _, position, _ ->
            if (!isUpdatingSelectors) {
                val selectedYear = yearSelectorView.adapter.getItem(position) as String
                calendarViewModel.onYearSelected(selectedYear.toInt())
            }
        }
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
        val shouldAnimateMonthChange =
            lastRenderedMonthTitle != null && lastRenderedMonthTitle != state.monthTitle
        val shouldAnimateSelectedDate =
            lastSelectedDateTitle != null && lastSelectedDateTitle != state.selectedDateTitle

        renderSelectors(state)
        monthTitleView.text = state.monthTitle
        selectedDateTitleView.text = state.selectedDateTitle
        selectedDateSummaryView.text = state.selectedDateSummary
        holidaySummaryView.isVisible = state.selectedHolidays.isNotEmpty()
        holidaySummaryView.text = state.selectedHolidays.joinToString(separator = " - ")
        emptyStateView.isVisible = state.selectedDateReminders.isEmpty()
        emptyStateView.text = state.emptyStateMessage

        renderCalendarDays(state.days)
        renderDayDetails(state.selectedDateReminders)
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
        isUpdatingSelectors = true

        monthSelectorView.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_list_item_1,
                state.monthOptions
            )
        )
        yearSelectorView.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_list_item_1,
                state.yearOptions.map { year -> year.toString() }
            )
        )

        monthSelectorView.setText(
            state.monthOptions.getOrNull(state.selectedMonthIndex).orEmpty(),
            false
        )
        yearSelectorView.setText(state.selectedYear.toString(), false)

        isUpdatingSelectors = false
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
            !day.holidayLabel.isNullOrBlank() -> R.color.calendar_day_holiday_background
            else -> R.color.calendar_day_background
        }

        val strokeColorRes = when {
            !day.isCurrentMonth -> R.color.calendar_day_outside_stroke
            day.isSelected -> R.color.calendar_day_selected_stroke
            day.isToday -> R.color.calendar_day_today_stroke
            !day.holidayLabel.isNullOrBlank() -> R.color.calendar_day_holiday_stroke
            else -> R.color.calendar_day_stroke
        }

        cardView.setCardBackgroundColor(ContextCompat.getColor(this, backgroundColorRes))
        cardView.strokeColor = ContextCompat.getColor(this, strokeColorRes)
        cardView.strokeWidth = resources.getDimensionPixelSize(R.dimen.calendar_day_stroke)
        cardView.radius = if (day.isSelected) 14f else 12f

        indicatorViews.forEachIndexed { index, indicatorView ->
            val indicator = day.indicators.getOrNull(index)
            indicatorView.isVisible = shouldShowSummary && indicator != null

            if (shouldShowSummary && indicator != null) {
                indicatorView.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this, indicator.style.dotColorResId)
                )
                indicatorView.alpha = 1f
            }
        }

        summaryCountView.isVisible = shouldShowSummary && !day.summaryCountLabel.isNullOrBlank()
        summaryCountView.text = day.summaryCountLabel
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
        monthTitleView.alpha = 0.2f
        monthTitleView.translationY = 8f
        monthTitleView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(180L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

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
            val detailView = LayoutInflater.from(this)
                .inflate(R.layout.item_calendar_detail, detailContainer, false)

            val titleView = detailView.findViewById<TextView>(R.id.tvCalendarDetailTitle)
            val descriptionView =
                detailView.findViewById<TextView>(R.id.tvCalendarDetailDescription)
            val timeView = detailView.findViewById<TextView>(R.id.tvCalendarDetailTime)
            val typeView = detailView.findViewById<TextView>(R.id.tvCalendarDetailType)
            val completedView = detailView.findViewById<TextView>(R.id.tvCalendarDetailCompleted)

            titleView.text = detail.title
            descriptionView.text = detail.detail
            timeView.text = detail.time
            typeView.text = when (detail.type) {
                ReminderType.BIRTHDAY -> getString(R.string.calendar_type_birthday)
                ReminderType.DEFAULT -> getString(R.string.calendar_type_default)
            }

            val typeStyle = when (detail.type) {
                ReminderType.BIRTHDAY -> CalendarIndicatorStyle.BIRTHDAY
                ReminderType.DEFAULT -> CalendarIndicatorStyle.REMINDER
            }

            typeView.setBackgroundResource(typeStyle.backgroundResId)
            typeView.setTextColor(ContextCompat.getColor(this, typeStyle.textColorResId))

            completedView.isVisible = detail.isCompleted
            if (detail.isCompleted) {
                completedView.setBackgroundResource(CalendarIndicatorStyle.COMPLETED.backgroundResId)
                completedView.setTextColor(
                    ContextCompat.getColor(this, CalendarIndicatorStyle.COMPLETED.textColorResId)
                )
            }

            detailContainer.addView(detailView)
        }
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
}
