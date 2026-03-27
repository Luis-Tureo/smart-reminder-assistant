package com.luistureo.voicereminderapp.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.utils.DateTimeFormatter
import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.usecase.AddReminderUseCase
import com.luistureo.voicereminderapp.domain.usecase.DeleteReminderUseCase
import com.luistureo.voicereminderapp.domain.usecase.GetRemindersUseCase
import com.luistureo.voicereminderapp.domain.usecase.UpdateReminderUseCase
import com.luistureo.voicereminderapp.presentation.common.UiText
import com.luistureo.voicereminderapp.presentation.state.ReminderFormState
import com.luistureo.voicereminderapp.presentation.state.ReminderUiEvent
import com.luistureo.voicereminderapp.presentation.state.ReminderUiState
import com.luistureo.voicereminderapp.presentation.state.VoiceReminderState
import com.luistureo.voicereminderapp.presentation.state.VoiceReminderStep
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

class ReminderViewModel(
    private val addReminderUseCase: AddReminderUseCase,
    private val getRemindersUseCase: GetRemindersUseCase,
    private val deleteReminderUseCase: DeleteReminderUseCase,
    private val updateReminderUseCase: UpdateReminderUseCase
) : ViewModel() {

    private data class ParsedVoiceInput(
        val reminderText: String? = null,
        val resolvedDay: Int? = null,
        val resolvedMonth: Int? = null,
        val resolvedYear: Int? = null,
        val resolvedHour: Int? = null,
        val resolvedMinute: Int? = null
    )

    private data class ResolvedDate(
        val day: Int,
        val month: Int,
        val year: Int
    )

    private val _uiState = MutableStateFlow(ReminderUiState())
    val uiState: StateFlow<ReminderUiState> = _uiState.asStateFlow()

    private val _voiceState = MutableStateFlow(VoiceReminderState())
    val voiceState: StateFlow<VoiceReminderState> = _voiceState.asStateFlow()

    private val _formState = MutableStateFlow(ReminderFormState())
    val formState: StateFlow<ReminderFormState> = _formState.asStateFlow()

    private val _events = MutableSharedFlow<ReminderUiEvent>()
    val events: SharedFlow<ReminderUiEvent> = _events.asSharedFlow()

    init {
        loadReminders()
    }

    fun startVoiceReminderFlow() {
        val calendar = Calendar.getInstance()

        _voiceState.value = VoiceReminderState(
            step = VoiceReminderStep.WAITING_FOR_REMINDER_TEXT,
            reminderMonth = calendar.get(Calendar.MONTH) + 1,
            reminderYear = calendar.get(Calendar.YEAR),
            isVoiceFlowActive = true,
            lastAssistantMessage = "Hola, dime el recordatorio. También puedes decirlo completo, por ejemplo: comprar pan mañana a las 9 de la noche."
        )
    }

    fun processVoiceInput(input: String) {
        val cleanedInput = input.trim()

        if (cleanedInput.isBlank()) {
            emitVoiceMessage("No logré entenderte bien. Intenta nuevamente.")
            return
        }

        val currentState = _voiceState.value
        val normalizedInput = normalizeText(cleanedInput)

        if (!currentState.isVoiceFlowActive) {
            startVoiceReminderFlow()
            return
        }

        if (currentState.step == VoiceReminderStep.WAITING_FOR_CONFIRMATION) {
            when {
                isAffirmativeConfirmation(normalizedInput) -> {
                    saveVoiceReminder()
                    return
                }

                isNegativeConfirmation(normalizedInput) -> {
                    emitVoiceMessage("De acuerdo, no guardaré este recordatorio.")
                    resetVoiceState()
                    return
                }

                else -> {
                    val correctedState = mergeVoiceStateWithInput(currentState, cleanedInput)
                    _voiceState.value = correctedState
                    guideVoiceConversation(correctedState)
                    return
                }
            }
        }

        if (isGeneralCancelCommand(normalizedInput)) {
            emitVoiceMessage("De acuerdo, cancelé la creación del recordatorio.")
            resetVoiceState()
            return
        }

        val newState = mergeVoiceStateWithInput(currentState, cleanedInput)
        _voiceState.value = newState
        guideVoiceConversation(newState)
    }

    fun loadReminders() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null
            )

            try {
                val reminders = getRemindersUseCase()

                _uiState.value = _uiState.value.copy(
                    reminders = reminders,
                    isLoading = false,
                    error = null
                )
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = if (exception.message.isNullOrBlank()) {
                        UiText.StringResource(R.string.message_unknown_error)
                    } else {
                        UiText.DynamicString(exception.message!!)
                    }
                )
            }
        }
    }

    // Solo guarda la fecha seleccionada
    fun onDateSelected(year: Int, month: Int, day: Int) {
        val current = _formState.value
        _formState.value = current.copy(
            selectedYear = year,
            selectedMonth = month,
            selectedDay = day
        )
    }

    // Solo guarda la hora seleccionada
    fun onTimeSelected(hour: Int, minute: Int) {
        val current = _formState.value
        _formState.value = current.copy(
            selectedHour = hour,
            selectedMinute = minute
        )
    }

    fun saveReminder(text: String) {
        viewModelScope.launch {
            val form = _formState.value

            val hasDate = DateTimeFormatter.hasValidDate(
                form.selectedYear,
                form.selectedMonth,
                form.selectedDay
            )

            val hasTime = DateTimeFormatter.hasValidTime(
                form.selectedHour,
                form.selectedMinute
            )

            if (text.isBlank()) {
                _events.emit(
                    ReminderUiEvent.ShowMessage("No hay texto para guardar")
                )
                return@launch
            }

            if (!hasDate || !hasTime) {
                _events.emit(
                    ReminderUiEvent.ShowMessage("Selecciona una fecha y hora válidas")
                )
                return@launch
            }

            val triggerTimeMillis = DateTimeFormatter.buildTriggerTimeMillis(
                year = form.selectedYear,
                month = form.selectedMonth,
                day = form.selectedDay,
                hour = form.selectedHour,
                minute = form.selectedMinute
            )

            if (triggerTimeMillis <= System.currentTimeMillis()) {
                _events.emit(
                    ReminderUiEvent.ShowMessage("Selecciona una fecha y hora futuras")
                )
                return@launch
            }

            val reminder = Reminder(
                text = text,
                date = DateTimeFormatter.formatDate(
                    form.selectedDay,
                    form.selectedMonth,
                    form.selectedYear
                ),
                time = DateTimeFormatter.formatTime(
                    form.selectedHour,
                    form.selectedMinute
                ),
                isCompleted = false
            )

            try {
                addReminderUseCase(reminder)
                loadReminders()

                _events.emit(
                    ReminderUiEvent.ScheduleReminder(
                        reminderText = reminder.text,
                        reminderDate = reminder.date,
                        reminderTime = reminder.time,
                        triggerTimeMillis = triggerTimeMillis
                    )
                )

                _events.emit(ReminderUiEvent.ClearForm)

                _events.emit(
                    ReminderUiEvent.ShowMessage("Recordatorio guardado correctamente")
                )
            } catch (exception: Exception) {
                _events.emit(
                    ReminderUiEvent.ShowMessage(
                        exception.message ?: "Error al guardar el recordatorio"
                    )
                )
            }
        }
    }

    fun clearFormState() {
        _formState.value = ReminderFormState()
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            try {
                deleteReminderUseCase(reminder)
                loadReminders()
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = if (exception.message.isNullOrBlank()) {
                        UiText.StringResource(R.string.message_delete_reminder_failed)
                    } else {
                        UiText.DynamicString(exception.message!!)
                    }
                )
            }
        }
    }

    fun updateReminder(reminder: Reminder) {
        viewModelScope.launch {
            try {
                updateReminderUseCase(reminder)
                loadReminders()
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = if (exception.message.isNullOrBlank()) {
                        UiText.StringResource(R.string.message_update_reminder_failed)
                    } else {
                        UiText.DynamicString(exception.message!!)
                    }
                )
            }
        }
    }
    private fun guideVoiceConversation(state: VoiceReminderState) {
        val reminderText = state.reminderText.trim()
        val reminderDay = state.reminderDay
        val reminderMonth = state.reminderMonth
        val reminderYear = state.reminderYear
        val reminderHour = state.reminderHour
        val reminderMinute = state.reminderMinute

        when {
            reminderText.isBlank() -> {
                _voiceState.value = state.copy(
                    step = VoiceReminderStep.WAITING_FOR_REMINDER_TEXT,
                    isVoiceFlowActive = true,
                    lastAssistantMessage = "Entiendo. ¿Qué recordatorio quieres guardar?"
                )
            }

            reminderHour == null || reminderMinute == null -> {
                _voiceState.value = state.copy(
                    step = VoiceReminderStep.WAITING_FOR_TIME,
                    isVoiceFlowActive = true,
                    lastAssistantMessage = "Perfecto. ¿A qué hora quieres este recordatorio?"
                )
            }

            reminderDay == null || reminderMonth == null || reminderYear == null -> {
                _voiceState.value = state.copy(
                    step = VoiceReminderStep.WAITING_FOR_DAY,
                    isVoiceFlowActive = true,
                    lastAssistantMessage = "Muy bien. ¿Para qué día quieres programarlo?"
                )
            }

            else -> {
                val confirmationMessage = buildVoiceConfirmationMessage(state)

                _voiceState.value = state.copy(
                    step = VoiceReminderStep.WAITING_FOR_CONFIRMATION,
                    isVoiceFlowActive = true,
                    lastAssistantMessage = confirmationMessage
                )
            }
        }
    }

    private fun mergeVoiceStateWithInput(
        currentState: VoiceReminderState,
        input: String
    ): VoiceReminderState {
        val parsedInput = parseVoiceInput(input)

        return currentState.copy(
            reminderText = parsedInput.reminderText ?: currentState.reminderText,
            reminderHour = parsedInput.resolvedHour ?: currentState.reminderHour,
            reminderMinute = parsedInput.resolvedMinute ?: currentState.reminderMinute,
            reminderDay = parsedInput.resolvedDay ?: currentState.reminderDay,
            reminderMonth = parsedInput.resolvedMonth ?: currentState.reminderMonth,
            reminderYear = parsedInput.resolvedYear ?: currentState.reminderYear,
            isVoiceFlowActive = true
        )
    }

    private fun parseVoiceInput(input: String): ParsedVoiceInput {
        val normalizedInput = normalizeText(input)

        val parsedDate = parseNaturalDate(normalizedInput)
        val parsedTime = parseTime(normalizedInput)

        val parsedReminderText = when {
            parsedDate != null || parsedTime != null -> {
                extractReminderText(normalizedInput)
            }

            else -> {
                normalizedInput.takeIf { it.isNotBlank() }
            }
        }

        return ParsedVoiceInput(
            reminderText = parsedReminderText,
            resolvedDay = parsedDate?.day,
            resolvedMonth = parsedDate?.month,
            resolvedYear = parsedDate?.year,
            resolvedHour = parsedTime?.first,
            resolvedMinute = parsedTime?.second
        )
    }

    private fun parseNaturalDate(input: String): ResolvedDate? {
        val normalizedInput = normalizeText(input)
        val calendar = Calendar.getInstance()

        when {
            normalizedInput.contains("pasado manana") -> {
                calendar.add(Calendar.DAY_OF_MONTH, 2)
                return ResolvedDate(
                    day = calendar.get(Calendar.DAY_OF_MONTH),
                    month = calendar.get(Calendar.MONTH) + 1,
                    year = calendar.get(Calendar.YEAR)
                )
            }

            normalizedInput.contains("manana") -> {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
                return ResolvedDate(
                    day = calendar.get(Calendar.DAY_OF_MONTH),
                    month = calendar.get(Calendar.MONTH) + 1,
                    year = calendar.get(Calendar.YEAR)
                )
            }

            Regex("\\bhoy\\b").containsMatchIn(normalizedInput) -> {
                return ResolvedDate(
                    day = calendar.get(Calendar.DAY_OF_MONTH),
                    month = calendar.get(Calendar.MONTH) + 1,
                    year = calendar.get(Calendar.YEAR)
                )
            }
        }

        val explicitDayMatch = Regex("\\b(?:el\\s+)?(\\d{1,2})\\b").find(normalizedInput)
        val explicitDay = explicitDayMatch?.groupValues?.getOrNull(1)?.toIntOrNull()

        if (explicitDay != null && explicitDay in 1..31) {
            return resolveDateFromDayNumber(explicitDay)
        }

        return null
    }

    private fun resolveDateFromDayNumber(day: Int): ResolvedDate? {
        val calendar = Calendar.getInstance()
        val candidate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.DAY_OF_MONTH, day)
        }

        if (candidate.get(Calendar.DAY_OF_MONTH) != day) {
            return null
        }

        if (candidate.before(calendar)) {
            candidate.add(Calendar.MONTH, 1)

            if (candidate.get(Calendar.DAY_OF_MONTH) != day) {
                return null
            }
        }

        return ResolvedDate(
            day = candidate.get(Calendar.DAY_OF_MONTH),
            month = candidate.get(Calendar.MONTH) + 1,
            year = candidate.get(Calendar.YEAR)
        )
    }

    private fun extractReminderText(input: String): String? {
        var text = normalizeText(input)

        text = text
            // Frases de intención comunes
            .replace(Regex("\\brecordarme\\b"), "")
            .replace(Regex("\\brecuerdame\\b"), "")
            .replace(Regex("\\brecordatorio\\b"), "")
            .replace(Regex("\\bagendar\\b"), "")
            .replace(Regex("\\bagenda\\b"), "")
            .replace(Regex("\\bguardar\\b"), "")
            .replace(Regex("\\bguarda\\b"), "")
            .replace(Regex("\\bcrear\\b"), "")
            .replace(Regex("\\bcrea\\b"), "")
            .replace(Regex("\\bprogramar\\b"), "")
            .replace(Regex("\\bprograma\\b"), "")

            // Expresiones de fecha natural
            .replace(Regex("\\bpara\\s+pasado\\s+manana\\b"), "")
            .replace(Regex("\\bpara\\s+manana\\b"), "")
            .replace(Regex("\\bpara\\s+hoy\\b"), "")
            .replace(Regex("\\bpasado\\s+manana\\b"), "")
            .replace(Regex("\\bmanana\\b"), "")
            .replace(Regex("\\bhoy\\b"), "")

            // Expresiones de hora
            .replace(Regex("\\ba\\s+las\\s+\\d{1,2}:\\d{1,2}\\b"), "")
            .replace(Regex("\\ba\\s+la\\s+\\d{1,2}:\\d{1,2}\\b"), "")
            .replace(Regex("\\ba\\s+las\\s+\\d{1,2}\\s+y\\s+\\d{1,2}\\b"), "")
            .replace(Regex("\\ba\\s+la\\s+\\d{1,2}\\s+y\\s+\\d{1,2}\\b"), "")
            .replace(Regex("\\ba\\s+las\\s+\\d{1,2}\\s*(am|pm)\\b"), "")
            .replace(Regex("\\ba\\s+la\\s+\\d{1,2}\\s*(am|pm)\\b"), "")
            .replace(Regex("\\ba\\s+las\\s+\\d{1,2}\\s+de\\s+la\\s+manana\\b"), "")
            .replace(Regex("\\ba\\s+las\\s+\\d{1,2}\\s+de\\s+la\\s+tarde\\b"), "")
            .replace(Regex("\\ba\\s+las\\s+\\d{1,2}\\s+de\\s+la\\s+noche\\b"), "")
            .replace(Regex("\\ba\\s+la\\s+\\d{1,2}\\s+de\\s+la\\s+manana\\b"), "")
            .replace(Regex("\\ba\\s+la\\s+\\d{1,2}\\s+de\\s+la\\s+tarde\\b"), "")
            .replace(Regex("\\ba\\s+la\\s+\\d{1,2}\\s+de\\s+la\\s+noche\\b"), "")
            .replace(Regex("\\ba\\s+las\\s+\\d{1,2}\\b"), "")
            .replace(Regex("\\ba\\s+la\\s+\\d{1,2}\\b"), "")

            // Día explícito
            .replace(Regex("\\bpara\\s+el\\s+\\d{1,2}\\b"), "")
            .replace(Regex("\\bel\\s+\\d{1,2}\\b"), "")

            // Limpieza general
            .replace(",", " ")
            .replace(".", " ")
            .replace(";", " ")
            .replace(":", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        text = text
            .replace(Regex("\\bpara\\b\\s*$"), "")
            .replace(Regex("\\ba\\b\\s*$"), "")
            .replace(Regex("\\bde\\b\\s*$"), "")
            .replace(Regex("^\\s+|\\s+$"), "")
            .trim()

        return text.takeIf { it.isNotBlank() }
    }

    private fun buildVoiceConfirmationMessage(state: VoiceReminderState): String {
        val reminderText = state.reminderText.trim()
        val reminderDay = state.reminderDay ?: 0
        val reminderMonth = state.reminderMonth ?: 0
        val reminderYear = state.reminderYear ?: 0
        val reminderHour = state.reminderHour ?: 0
        val reminderMinute = state.reminderMinute ?: 0

        val dateText = DateTimeFormatter.formatDate(
            reminderDay,
            reminderMonth,
            reminderYear
        )

        val timeText = DateTimeFormatter.formatTime(
            reminderHour,
            reminderMinute
        )

        return "Perfecto. Entendí que quieres guardar el recordatorio $reminderText para el $dateText a las $timeText. ¿Lo confirmas?"
    }

    private fun saveVoiceReminder() {
        val state = _voiceState.value

        val reminderDay = state.reminderDay ?: return
        val reminderMonth = state.reminderMonth ?: return
        val reminderYear = state.reminderYear ?: return
        val reminderHour = state.reminderHour ?: return
        val reminderMinute = state.reminderMinute ?: return
        val reminderText = state.reminderText.trim()

        if (reminderText.isBlank()) {
            emitVoiceMessage("Todavía no tengo claro el texto del recordatorio. Dímelo nuevamente.")
            _voiceState.value = _voiceState.value.copy(
                step = VoiceReminderStep.WAITING_FOR_REMINDER_TEXT,
                isVoiceFlowActive = true
            )
            return
        }

        val triggerTimeMillis = DateTimeFormatter.buildTriggerTimeMillis(
            year = reminderYear,
            month = reminderMonth,
            day = reminderDay,
            hour = reminderHour,
            minute = reminderMinute
        )

        if (triggerTimeMillis <= System.currentTimeMillis()) {
            emitVoiceMessage("La fecha y hora indicadas ya pasaron. Dime una nueva fecha u hora.")
            _voiceState.value = _voiceState.value.copy(
                step = VoiceReminderStep.WAITING_FOR_DAY,
                isVoiceFlowActive = true
            )
            return
        }

        val reminder = Reminder(
            text = reminderText,
            date = DateTimeFormatter.formatDate(
                reminderDay,
                reminderMonth,
                reminderYear
            ),
            time = DateTimeFormatter.formatTime(
                reminderHour,
                reminderMinute
            ),
            isCompleted = false
        )

        viewModelScope.launch {
            try {
                addReminderUseCase(reminder)
                loadReminders()

                _events.emit(
                    ReminderUiEvent.ScheduleReminder(
                        reminderText = reminder.text,
                        reminderDate = reminder.date,
                        reminderTime = reminder.time,
                        triggerTimeMillis = triggerTimeMillis
                    )
                )

                emitVoiceMessage("Listo, tu recordatorio fue guardado correctamente.")
                resetVoiceState()
            } catch (exception: Exception) {
                emitVoiceMessage(
                    exception.message ?: "Ocurrió un error al guardar el recordatorio."
                )
                resetVoiceState()
            }
        }
    }

    private fun emitVoiceMessage(message: String) {
        _voiceState.value = _voiceState.value.copy(
            isVoiceFlowActive = true,
            lastAssistantMessage = message
        )
    }

    private fun resetVoiceState() {
        _voiceState.value = VoiceReminderState()
    }

    private fun parseTime(input: String): Pair<Int, Int>? {
        val text = normalizeText(input)

        val patterns = listOf(
            Regex("\\ba\\s+las\\s+(\\d{1,2}):(\\d{1,2})\\b"),
            Regex("\\ba\\s+la\\s+(\\d{1,2}):(\\d{1,2})\\b"),
            Regex("\\ba\\s+las\\s+(\\d{1,2})\\s+y\\s+(\\d{1,2})\\b"),
            Regex("\\ba\\s+la\\s+(\\d{1,2})\\s+y\\s+(\\d{1,2})\\b"),
            Regex("\\ba\\s+las\\s+(\\d{1,2})\\s*(am|pm)\\b"),
            Regex("\\ba\\s+la\\s+(\\d{1,2})\\s*(am|pm)\\b"),
            Regex("\\ba\\s+las\\s+(\\d{1,2})\\s+de\\s+la\\s+(manana|tarde|noche)\\b"),
            Regex("\\ba\\s+la\\s+(\\d{1,2})\\s+de\\s+la\\s+(manana|tarde|noche)\\b"),
            Regex("\\ba\\s+las\\s+(\\d{1,2})\\b"),
            Regex("\\ba\\s+la\\s+(\\d{1,2})\\b")
        )

        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            val values = match.groupValues

            when {
                values.size >= 3 && match.value.contains(":") -> {
                    val rawHour = values[1].toIntOrNull() ?: return null
                    val minute = values[2].toIntOrNull() ?: return null
                    val hour = adjustHourByPeriod(match.value, rawHour) ?: return null

                    if (minute !in 0..59) return null
                    return Pair(hour, minute)
                }

                values.size >= 3 && match.value.contains(" y ") -> {
                    val rawHour = values[1].toIntOrNull() ?: return null
                    val minute = values[2].toIntOrNull() ?: return null
                    val hour = adjustHourByPeriod(match.value, rawHour) ?: return null

                    if (minute !in 0..59) return null
                    return Pair(hour, minute)
                }

                values.size >= 3 && (values[2] == "am" || values[2] == "pm") -> {
                    val rawHour = values[1].toIntOrNull() ?: return null
                    val hour = adjustHourByPeriod(match.value, rawHour) ?: return null
                    return Pair(hour, 0)
                }

                values.size >= 3 && (
                        values[2] == "manana" ||
                                values[2] == "tarde" ||
                                values[2] == "noche"
                        ) -> {
                    val rawHour = values[1].toIntOrNull() ?: return null
                    val hour = adjustHourByPeriod(match.value, rawHour) ?: return null
                    return Pair(hour, 0)
                }

                values.size >= 2 -> {
                    val rawHour = values[1].toIntOrNull() ?: return null
                    val hour = adjustHourByPeriod(match.value, rawHour) ?: return null
                    return Pair(hour, 0)
                }
            }
        }

        val exactTimeOnlyPatterns = listOf(
            Regex("^(\\d{1,2}):(\\d{1,2})$"),
            Regex("^(\\d{1,2})\\s+y\\s+(\\d{1,2})$"),
            Regex("^(\\d{1,2})\\s*(am|pm)$"),
            Regex("^(\\d{1,2})\\s+de\\s+la\\s+(manana|tarde|noche)$"),
            Regex("^(\\d{1,2})$")
        )

        for (pattern in exactTimeOnlyPatterns) {
            val match = pattern.find(text) ?: continue
            val values = match.groupValues

            when {
                values.size >= 3 && match.value.contains(":") -> {
                    val rawHour = values[1].toIntOrNull() ?: return null
                    val minute = values[2].toIntOrNull() ?: return null
                    val hour = adjustHourByPeriod(match.value, rawHour) ?: return null

                    if (minute !in 0..59) return null
                    return Pair(hour, minute)
                }

                values.size >= 3 && match.value.contains(" y ") -> {
                    val rawHour = values[1].toIntOrNull() ?: return null
                    val minute = values[2].toIntOrNull() ?: return null
                    val hour = adjustHourByPeriod(match.value, rawHour) ?: return null

                    if (minute !in 0..59) return null
                    return Pair(hour, minute)
                }

                values.size >= 3 && (values[2] == "am" || values[2] == "pm") -> {
                    val rawHour = values[1].toIntOrNull() ?: return null
                    val hour = adjustHourByPeriod(match.value, rawHour) ?: return null
                    return Pair(hour, 0)
                }

                values.size >= 3 && (
                        values[2] == "manana" ||
                                values[2] == "tarde" ||
                                values[2] == "noche"
                        ) -> {
                    val rawHour = values[1].toIntOrNull() ?: return null
                    val hour = adjustHourByPeriod(match.value, rawHour) ?: return null
                    return Pair(hour, 0)
                }

                values.size >= 2 -> {
                    val rawHour = values[1].toIntOrNull() ?: return null
                    val hour = adjustHourByPeriod(match.value, rawHour) ?: return null
                    return Pair(hour, 0)
                }
            }
        }

        return null
    }

    private fun adjustHourByPeriod(text: String, rawHour: Int): Int? {
        val normalizedText = normalizeText(text)

        if (normalizedText.contains("medianoche")) {
            return 0
        }

        if (normalizedText.contains("mediodia")) {
            return 12
        }

        if (normalizedText.contains("madrugada")) {
            return when (rawHour) {
                in 1..11 -> rawHour
                12 -> 0
                else -> null
            }
        }

        if (normalizedText.contains("manana") || normalizedText.contains("am")) {
            return when (rawHour) {
                in 1..11 -> rawHour
                12 -> 0
                else -> rawHour.takeIf { it in 0..23 }
            }
        }

        if (normalizedText.contains("tarde") || normalizedText.contains("pm")) {
            return when (rawHour) {
                in 1..11 -> rawHour + 12
                12 -> 12
                else -> rawHour.takeIf { it in 0..23 }
            }
        }

        if (normalizedText.contains("noche")) {
            return when (rawHour) {
                in 1..11 -> rawHour + 12
                12 -> 0
                else -> rawHour.takeIf { it in 0..23 }
            }
        }

        return rawHour.takeIf { it in 0..23 }
    }

    private fun isAffirmativeConfirmation(input: String): Boolean {
        val normalizedInput = normalizeText(input)

        return normalizedInput == "si" ||
                normalizedInput == "sí" ||
                normalizedInput == "confirmo" ||
                normalizedInput == "guardar" ||
                normalizedInput == "guarda" ||
                normalizedInput == "ok" ||
                normalizedInput == "okay" ||
                normalizedInput == "dale" ||
                normalizedInput == "correcto"
    }

    private fun isNegativeConfirmation(input: String): Boolean {
        val normalizedInput = normalizeText(input)

        return normalizedInput == "no" ||
                normalizedInput == "cancelar" ||
                normalizedInput == "cancela" ||
                normalizedInput == "detener" ||
                normalizedInput == "stop"
    }

    private fun isGeneralCancelCommand(input: String): Boolean {
        val normalizedInput = normalizeText(input)

        return normalizedInput == "cancelar" ||
                normalizedInput == "cancela" ||
                normalizedInput == "salir" ||
                normalizedInput == "detener"
    }

    private fun normalizeText(text: String): String {
        return text
            .lowercase()
            .replace("á", "a")
            .replace("é", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ú", "u")
            .replace(",", " ")
            .replace(";", " ")
            .replace(".", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}