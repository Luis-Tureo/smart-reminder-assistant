package com.luistureo.voicereminderapp.presentation.ui.dialog

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Toast
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.utils.DateTimeFormatterCore
import com.luistureo.voicereminderapp.domain.model.ReminderDraft
import com.luistureo.voicereminderapp.domain.model.ReminderRecurrence
import com.luistureo.voicereminderapp.domain.model.ReminderRecurrenceUnit
import com.luistureo.voicereminderapp.domain.model.ReminderSource
import com.luistureo.voicereminderapp.domain.model.ReminderWeekday

class ReminderEditorDialogController(
    private val context: Context
) {

    private enum class RecurrenceDialogOption(val labelResId: Int) {
        NONE(R.string.reminder_recurrence_none),
        DAILY(R.string.reminder_recurrence_daily),
        WEEKLY(R.string.reminder_recurrence_weekly),
        MONTHLY(R.string.reminder_recurrence_monthly),
        YEARLY(R.string.reminder_recurrence_yearly),
        SPECIFIC_WEEKDAYS(R.string.reminder_recurrence_specific_weekdays),
        EVERY_X_DAYS(R.string.reminder_recurrence_every_x_days),
        EVERY_X_WEEKS(R.string.reminder_recurrence_every_x_weeks),
        EVERY_X_MONTHS(R.string.reminder_recurrence_every_x_months)
    }

    fun show(
        initialDraft: ReminderDraft,
        onSave: (ReminderDraft) -> Unit
    ) {
        val view = android.view.LayoutInflater.from(context)
            .inflate(R.layout.dialog_reminder_editor, null, false)

        val titleInput = view.findViewById<TextInputEditText>(R.id.inputReminderTitle)
        val detailInput = view.findViewById<TextInputEditText>(R.id.inputReminderDetail)
        val dateButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSelectDate)
        val timeButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSelectTime)
        val urgentSwitch = view.findViewById<SwitchMaterial>(R.id.switchUrgent)
        val recurrenceInput = view.findViewById<MaterialAutoCompleteTextView>(R.id.inputRecurrenceType)
        val intervalLayout = view.findViewById<TextInputLayout>(R.id.layoutRecurrenceInterval)
        val intervalInput = view.findViewById<TextInputEditText>(R.id.inputRecurrenceInterval)
        val recurrenceActiveSwitch = view.findViewById<SwitchMaterial>(R.id.switchRecurrenceActive)
        val weekdaysLayout = view.findViewById<android.view.View>(R.id.layoutWeekdays)

        val weekdayChecks = mapOf(
            ReminderWeekday.MONDAY to view.findViewById<CheckBox>(R.id.checkMonday),
            ReminderWeekday.TUESDAY to view.findViewById<CheckBox>(R.id.checkTuesday),
            ReminderWeekday.WEDNESDAY to view.findViewById<CheckBox>(R.id.checkWednesday),
            ReminderWeekday.THURSDAY to view.findViewById<CheckBox>(R.id.checkThursday),
            ReminderWeekday.FRIDAY to view.findViewById<CheckBox>(R.id.checkFriday),
            ReminderWeekday.SATURDAY to view.findViewById<CheckBox>(R.id.checkSaturday),
            ReminderWeekday.SUNDAY to view.findViewById<CheckBox>(R.id.checkSunday)
        )

        val recurrenceOptions = RecurrenceDialogOption.entries
        val recurrenceLabels = recurrenceOptions.map { option -> context.getString(option.labelResId) }
        recurrenceInput.setAdapter(
            ArrayAdapter(
                context,
                android.R.layout.simple_list_item_1,
                recurrenceLabels
            )
        )

        titleInput.setText(initialDraft.title.orEmpty())
        detailInput.setText(initialDraft.text.orEmpty())
        urgentSwitch.isChecked = initialDraft.isUrgent

        var selectedDate = initialDraft.date.orEmpty()
        var selectedTime = initialDraft.time.orEmpty()

        dateButton.text = if (selectedDate.isBlank()) {
            context.getString(R.string.reminder_select_date)
        } else {
            selectedDate
        }

        timeButton.text = if (selectedTime.isBlank()) {
            context.getString(R.string.reminder_select_time)
        } else {
            selectedTime
        }

        val initialOption = resolveDialogOption(initialDraft.recurrence)
        recurrenceInput.setText(context.getString(initialOption.labelResId), false)
        intervalInput.setText(initialDraft.recurrence?.interval?.takeIf { it > 1 }?.toString().orEmpty())
        recurrenceActiveSwitch.isChecked = initialDraft.recurrence?.isActive ?: true
        initialDraft.recurrence?.weekdays.orEmpty().forEach { weekday ->
            weekdayChecks[weekday]?.isChecked = true
        }

        fun updateRecurrenceVisibility(option: RecurrenceDialogOption) {
            intervalLayout.isVisible = option == RecurrenceDialogOption.EVERY_X_DAYS ||
                    option == RecurrenceDialogOption.EVERY_X_WEEKS ||
                    option == RecurrenceDialogOption.EVERY_X_MONTHS
            recurrenceActiveSwitch.isVisible = option != RecurrenceDialogOption.NONE
            weekdaysLayout.isVisible = option == RecurrenceDialogOption.SPECIFIC_WEEKDAYS
        }

        updateRecurrenceVisibility(initialOption)

        recurrenceInput.setOnItemClickListener { _, _, position, _ ->
            updateRecurrenceVisibility(recurrenceOptions[position])
        }

        dateButton.setOnClickListener {
            val parsedDate = DateTimeFormatterCore.parseDateParts(selectedDate)
            val calendar = java.util.Calendar.getInstance().apply {
                if (parsedDate != null) {
                    set(java.util.Calendar.YEAR, parsedDate.year)
                    set(java.util.Calendar.MONTH, parsedDate.month - 1)
                    set(java.util.Calendar.DAY_OF_MONTH, parsedDate.day)
                }
            }

            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    selectedDate = DateTimeFormatterCore.formatDate(dayOfMonth, month + 1, year)
                    dateButton.text = selectedDate
                },
                calendar.get(java.util.Calendar.YEAR),
                calendar.get(java.util.Calendar.MONTH),
                calendar.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        }

        timeButton.setOnClickListener {
            val parsedTime = DateTimeFormatterCore.parseTimeParts(selectedTime)
            val calendar = java.util.Calendar.getInstance().apply {
                if (parsedTime != null) {
                    set(java.util.Calendar.HOUR_OF_DAY, parsedTime.hour)
                    set(java.util.Calendar.MINUTE, parsedTime.minute)
                }
            }

            TimePickerDialog(
                context,
                { _, hourOfDay, minute ->
                    selectedTime = DateTimeFormatterCore.formatTime(hourOfDay, minute)
                    timeButton.text = selectedTime
                },
                calendar.get(java.util.Calendar.HOUR_OF_DAY),
                calendar.get(java.util.Calendar.MINUTE),
                true
            ).show()
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(
                when {
                    initialDraft.reminderId != 0 -> R.string.reminder_editor_edit_title
                    initialDraft.source == ReminderSource.CAMERA -> R.string.reminder_editor_camera_title
                    else -> R.string.reminder_editor_create_title
                }
            )
            .setView(view)
            .setNegativeButton(R.string.reminder_cancel_action, null)
            .setPositiveButton(R.string.reminder_save_action, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val detail = detailInput.text?.toString()?.trim().orEmpty()
                if (detail.isBlank()) {
                    Toast.makeText(context, R.string.reminder_error_detail_required, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (selectedDate.isBlank()) {
                    Toast.makeText(context, R.string.reminder_error_date_required, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (selectedTime.isBlank()) {
                    Toast.makeText(context, R.string.reminder_error_time_required, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val selectedOption = recurrenceOptions[
                    recurrenceLabels.indexOf(recurrenceInput.text?.toString()).coerceAtLeast(0)
                ]

                val selectedWeekdays = weekdayChecks
                    .filterValues { it.isChecked }
                    .keys
                    .toSet()

                if (
                    selectedOption == RecurrenceDialogOption.SPECIFIC_WEEKDAYS &&
                    selectedWeekdays.isEmpty()
                ) {
                    Toast.makeText(context, R.string.reminder_error_weekdays_required, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val recurrence = buildRecurrence(
                    option = selectedOption,
                    intervalValue = intervalInput.text?.toString()?.toIntOrNull(),
                    isActive = recurrenceActiveSwitch.isChecked,
                    weekdays = selectedWeekdays
                )

                onSave(
                    ReminderDraft(
                        reminderId = initialDraft.reminderId,
                        title = titleInput.text?.toString()?.trim()?.takeIf { it.isNotBlank() },
                        text = detail,
                        date = selectedDate,
                        time = selectedTime,
                        isUrgent = urgentSwitch.isChecked,
                        source = initialDraft.source.takeIf { it != ReminderSource.VOICE }
                            ?: ReminderSource.MANUAL,
                        recurrence = recurrence
                    )
                )
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun buildRecurrence(
        option: RecurrenceDialogOption,
        intervalValue: Int?,
        isActive: Boolean,
        weekdays: Set<ReminderWeekday>
    ): ReminderRecurrence? {
        val safeInterval = intervalValue?.coerceAtLeast(1) ?: 1

        return when (option) {
            RecurrenceDialogOption.NONE -> null
            RecurrenceDialogOption.DAILY -> ReminderRecurrence(ReminderRecurrenceUnit.DAY, 1, isActive = isActive)
            RecurrenceDialogOption.WEEKLY -> ReminderRecurrence(ReminderRecurrenceUnit.WEEK, 1, isActive = isActive)
            RecurrenceDialogOption.MONTHLY -> ReminderRecurrence(ReminderRecurrenceUnit.MONTH, 1, isActive = isActive)
            RecurrenceDialogOption.YEARLY -> ReminderRecurrence(ReminderRecurrenceUnit.YEAR, 1, isActive = isActive)
            RecurrenceDialogOption.SPECIFIC_WEEKDAYS -> ReminderRecurrence(
                unit = ReminderRecurrenceUnit.WEEK,
                interval = 1,
                weekdays = weekdays,
                isActive = isActive
            )
            RecurrenceDialogOption.EVERY_X_DAYS -> ReminderRecurrence(ReminderRecurrenceUnit.DAY, safeInterval, isActive = isActive)
            RecurrenceDialogOption.EVERY_X_WEEKS -> ReminderRecurrence(ReminderRecurrenceUnit.WEEK, safeInterval, isActive = isActive)
            RecurrenceDialogOption.EVERY_X_MONTHS -> ReminderRecurrence(ReminderRecurrenceUnit.MONTH, safeInterval, isActive = isActive)
        }
    }

    private fun resolveDialogOption(recurrence: ReminderRecurrence?): RecurrenceDialogOption {
        recurrence ?: return RecurrenceDialogOption.NONE

        return when {
            recurrence.unit == ReminderRecurrenceUnit.DAY && recurrence.interval == 1 ->
                RecurrenceDialogOption.DAILY

            recurrence.unit == ReminderRecurrenceUnit.WEEK &&
                    recurrence.interval == 1 &&
                    recurrence.weekdays.isEmpty() ->
                RecurrenceDialogOption.WEEKLY

            recurrence.unit == ReminderRecurrenceUnit.MONTH && recurrence.interval == 1 ->
                RecurrenceDialogOption.MONTHLY

            recurrence.unit == ReminderRecurrenceUnit.YEAR && recurrence.interval == 1 ->
                RecurrenceDialogOption.YEARLY

            recurrence.unit == ReminderRecurrenceUnit.WEEK && recurrence.weekdays.isNotEmpty() ->
                RecurrenceDialogOption.SPECIFIC_WEEKDAYS

            recurrence.unit == ReminderRecurrenceUnit.DAY ->
                RecurrenceDialogOption.EVERY_X_DAYS

            recurrence.unit == ReminderRecurrenceUnit.WEEK ->
                RecurrenceDialogOption.EVERY_X_WEEKS

            recurrence.unit == ReminderRecurrenceUnit.MONTH ->
                RecurrenceDialogOption.EVERY_X_MONTHS

            else -> RecurrenceDialogOption.NONE
        }
    }
}
