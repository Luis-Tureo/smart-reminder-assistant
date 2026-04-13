package com.luistureo.voicereminderapp.ios

import com.luistureo.voicereminderapp.core.reminder.ReminderDraftFormStateResolver
import com.luistureo.voicereminderapp.core.utils.DateTimeFormatterCore
import com.luistureo.voicereminderapp.core.utils.ReminderDisplayFormatter
import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.model.ReminderDraft
import com.luistureo.voicereminderapp.domain.model.ReminderSource
import com.luistureo.voicereminderapp.domain.repository.ReminderRepository
import com.luistureo.voicereminderapp.domain.usecase.DeleteReminderUseCase
import com.luistureo.voicereminderapp.domain.usecase.GetReminderByIdUseCase
import com.luistureo.voicereminderapp.domain.usecase.GetRemindersUseCase
import com.luistureo.voicereminderapp.domain.usecase.SaveReminderDraftUseCase
import com.luistureo.voicereminderapp.domain.usecase.UpdateReminderUseCase
import kotlin.Throws

data class IosReminderListItem(
    val id: Int,
    val title: String,
    val detail: String,
    val scheduledDate: String,
    val scheduledTime: String,
    val recurrenceLabel: String?,
    val isCompleted: Boolean,
    val isUrgent: Boolean
)

data class IosReminderValidationResult(
    val isValid: Boolean,
    val errorMessage: String?
)

class IosReminderAppController(
    private val repository: ReminderRepository = IosReminderRepository()
) {

    constructor() : this(IosReminderRepository())

    private val saveReminderDraftUseCase = SaveReminderDraftUseCase(repository)
    private val getRemindersUseCase = GetRemindersUseCase(repository)
    private val getReminderByIdUseCase = GetReminderByIdUseCase(repository)
    private val deleteReminderUseCase = DeleteReminderUseCase(repository)
    private val updateReminderUseCase = UpdateReminderUseCase(repository)

    fun buildDateInput(day: Int, month: Int, year: Int): String {
        return DateTimeFormatterCore.formatDate(day = day, month = month, year = year)
    }

    fun buildTimeInput(hour: Int, minute: Int): String {
        return DateTimeFormatterCore.formatTime(hour = hour, minute = minute)
    }

    fun validateDraft(
        detail: String,
        date: String,
        time: String
    ): IosReminderValidationResult {
        val formState = ReminderDraftFormStateResolver.resolve(
            textValue = detail.trim(),
            dateValue = date.trim(),
            timeValue = time.trim()
        )

        val errorMessage = when {
            formState.hasMissingText -> "Ingresa un detalle para el recordatorio."
            formState.hasMissingDate -> "Selecciona una fecha."
            formState.hasMissingTime -> "Selecciona una hora."
            formState.dateTime.date.isIncomplete -> "Completa la fecha antes de guardar."
            formState.dateTime.time.isIncomplete -> "Completa la hora antes de guardar."
            formState.dateTime.date.isInvalid -> "La fecha no es valida."
            formState.dateTime.time.isInvalid -> "La hora no es valida."
            else -> null
        }

        return IosReminderValidationResult(
            isValid = errorMessage == null,
            errorMessage = errorMessage
        )
    }

    @Throws(Exception::class)
    suspend fun fetchReminders(): List<IosReminderListItem> {
        return getRemindersUseCase()
            .sortedWith(
                compareBy<Reminder> { it.isCompleted }
                    .thenBy { it.scheduleState.nextTriggerAtEpochMillis ?: it.scheduledAtEpochMillis }
                    .thenBy { it.title.lowercase() }
            )
            .map { reminder -> reminder.toListItem() }
    }

    @Throws(Exception::class)
    suspend fun saveReminder(
        title: String?,
        detail: String,
        date: String,
        time: String,
        isUrgent: Boolean
    ): IosReminderListItem {
        val validation = validateDraft(detail = detail, date = date, time = time)
        require(validation.isValid) {
            validation.errorMessage ?: "No fue posible guardar el recordatorio."
        }

        val reminder = saveReminderDraftUseCase(
            ReminderDraft(
                title = title?.trim()?.takeIf { it.isNotBlank() },
                text = detail.trim(),
                date = date.trim(),
                time = time.trim(),
                isUrgent = isUrgent,
                source = ReminderSource.MANUAL
            )
        )

        return reminder.toListItem()
    }

    @Throws(Exception::class)
    suspend fun deleteReminder(reminderId: Int) {
        val reminder = getReminderByIdUseCase(reminderId) ?: return
        deleteReminderUseCase(reminder)
    }

    @Throws(Exception::class)
    suspend fun setReminderCompleted(
        reminderId: Int,
        isCompleted: Boolean
    ): IosReminderListItem? {
        val reminder = getReminderByIdUseCase(reminderId) ?: return null
        val updatedReminder = reminder.copy(isCompleted = isCompleted)

        updateReminderUseCase(updatedReminder)

        return updatedReminder.toListItem()
    }

    private fun Reminder.toListItem(): IosReminderListItem {
        return IosReminderListItem(
            id = id,
            title = title,
            detail = detail,
            scheduledDate = ReminderDisplayFormatter.formatScheduledDate(this),
            scheduledTime = ReminderDisplayFormatter.formatScheduledTime(this),
            recurrenceLabel = ReminderDisplayFormatter.formatRecurrenceLabel(this),
            isCompleted = isCompleted,
            isUrgent = isUrgent
        )
    }
}
