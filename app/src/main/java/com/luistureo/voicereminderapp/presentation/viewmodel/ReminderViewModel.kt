package com.luistureo.voicereminderapp.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.ai.GeminiAssistantService
import com.luistureo.voicereminderapp.core.utils.DateTimeFormatter
import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.model.ReminderDraft
import com.luistureo.voicereminderapp.domain.service.ChatAssistantService
import com.luistureo.voicereminderapp.domain.usecase.AddReminderUseCase
import com.luistureo.voicereminderapp.domain.usecase.DeleteReminderUseCase
import com.luistureo.voicereminderapp.domain.usecase.GetRemindersUseCase
import com.luistureo.voicereminderapp.domain.usecase.UpdateReminderUseCase
import com.luistureo.voicereminderapp.presentation.common.UiText
import com.luistureo.voicereminderapp.presentation.state.AssistantUiState
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

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

    private val _assistantState = MutableStateFlow(AssistantUiState())
    val assistantState: StateFlow<AssistantUiState> = _assistantState.asStateFlow()

    private val _events = MutableSharedFlow<ReminderUiEvent>()
    val events: SharedFlow<ReminderUiEvent> = _events.asSharedFlow()

    private var pendingDraft: ReminderDraft? = null
    private var hasSavedInCurrentSession: Boolean = false

    private val chatAssistantService: ChatAssistantService =
        GeminiAssistantService(
            apiKey = "AIzaSyAWVTNwCRqa_aFDS9yTJPS_ZzWx0ujOyGY"
        )

    init {
        loadReminders()
    }

    fun startAssistantSession() {
        pendingDraft = null
        hasSavedInCurrentSession = false

        _assistantState.update {
            it.copy(
                recognizedText = "",
                assistantReply = "¿Qué deseas recordar?",
                pendingDraft = null,
                error = null,
                isLoading = false,
                isConversationActive = true
            )
        }

        viewModelScope.launch {
            _events.emit(ReminderUiEvent.SpeakAssistantReply("¿Qué deseas recordar?"))
        }
    }

    fun processAssistantMessage(message: String) {
        if (message.isBlank()) return
        if (hasSavedInCurrentSession) return

        viewModelScope.launch {
            _assistantState.update {
                it.copy(
                    isLoading = true,
                    recognizedText = message,
                    error = null,
                    isConversationActive = true
                )
            }

            Log.d("AI_DEBUG", "Mensaje usuario: $message")
            Log.d("AI_DEBUG", "Draft antes de procesar: $pendingDraft")

            val localDraft = mergeDraftFromRawMessage(
                message = message,
                currentDraft = pendingDraft
            )
            Log.d("AI_DEBUG", "Draft local interpretado: $localDraft")

            val draftAfterLocalMerge = mergeDraftSources(
                message = message,
                aiDraft = null,
                localDraft = localDraft,
                currentDraft = pendingDraft
            )

            Log.d("AI_DEBUG", "Draft luego del merge local: $draftAfterLocalMerge")

            pendingDraft = draftAfterLocalMerge.takeIf { hasAnyDraftData(it) }
            Log.d("AI_DEBUG", "PendingDraft tras merge local: $pendingDraft")

            if (!hasSavedInCurrentSession && pendingDraft?.isReadyToSave() == true) {
                Log.d("AI_DEBUG", "El draft quedó completo con parseo local. Se guardará automáticamente.")
                saveDraftReminder(pendingDraft!!)
                return@launch
            }

            val aiContextualMessage = buildAiContextualMessage(message)
            Log.d("AI_DEBUG", "Mensaje enviado a IA con contexto: $aiContextualMessage")

            val aiResponse = runCatching {
                chatAssistantService.processMessage(
                    userMessage = aiContextualMessage,
                    currentDraft = pendingDraft
                )
            }.onSuccess {
                Log.d("AI_DEBUG", "Gemini respondió: $it")
            }.onFailure {
                Log.e("AI_DEBUG", "Gemini falló", it)
            }.getOrNull()

            Log.d("AI_DEBUG", "¿Se usó IA con respuesta válida?: ${aiResponse != null}")

            val aiDraft = aiResponse?.let {
                ReminderDraft(
                    text = sanitizeAiReminderText(it.reminderText),
                    date = sanitizeAiDate(it.reminderDate, message),
                    time = sanitizeAiTime(it.reminderTime)
                )
            }

            val mergedDraft = mergeDraftSources(
                message = message,
                aiDraft = aiDraft,
                localDraft = localDraft,
                currentDraft = pendingDraft
            )

            Log.d("AI_DEBUG", "Draft fusionado final: $mergedDraft")

            pendingDraft = mergedDraft.takeIf { hasAnyDraftData(it) }
            Log.d("AI_DEBUG", "PendingDraft actualizado final: $pendingDraft")

            if (!hasSavedInCurrentSession && pendingDraft?.isReadyToSave() == true) {
                Log.d("AI_DEBUG", "El draft está completo después de IA. Se guardará automáticamente.")
                saveDraftReminder(pendingDraft!!)
                return@launch
            }

            val assistantReply = buildQuestionForMissingData(
                draft = pendingDraft,
                aiReply = aiResponse?.reply
            )

            Log.d("AI_DEBUG", "Respuesta final al usuario: $assistantReply")

            _assistantState.update {
                it.copy(
                    isLoading = false,
                    assistantReply = assistantReply,
                    pendingDraft = pendingDraft,
                    error = null,
                    isConversationActive = true
                )
            }

            _events.emit(ReminderUiEvent.SpeakAssistantReply(assistantReply))
        }
    }

    fun confirmPendingReminder() {
        // Ya no se usa confirmación manual en este flujo.
    }

    fun clearAssistantConversation() {
        pendingDraft = null
        hasSavedInCurrentSession = false

        _assistantState.value = AssistantUiState(
            reminders = _assistantState.value.reminders,
            isConversationActive = false
        )
    }

    private suspend fun saveDraftReminder(draft: ReminderDraft) {
        val reminderText = draft.text.orEmpty()
        val reminderDate = draft.date.orEmpty()
        val reminderTime = draft.time.orEmpty()

        if (reminderText.isBlank() || reminderDate.isBlank() || reminderTime.isBlank()) {
            val missingDataReply = buildQuestionForMissingData(draft)

            _assistantState.update {
                it.copy(
                    isLoading = false,
                    assistantReply = missingDataReply,
                    pendingDraft = draft,
                    error = null,
                    isConversationActive = true
                )
            }

            _events.emit(ReminderUiEvent.SpeakAssistantReply(missingDataReply))
            return
        }

        val reminder = Reminder(
            text = reminderText,
            date = reminderDate,
            time = reminderTime,
            isCompleted = false
        )

        addReminderUseCase(reminder)
        loadReminders()

        hasSavedInCurrentSession = true

        _events.emit(
            ReminderUiEvent.ShowMessage("Recordatorio guardado correctamente.")
        )

        _events.emit(
            ReminderUiEvent.SpeakAssistantReply(
                "Perfecto, tu recordatorio fue guardado con éxito."
            )
        )

        pendingDraft = null

        _assistantState.update {
            it.copy(
                isLoading = false,
                pendingDraft = null,
                assistantReply = "Perfecto, tu recordatorio fue guardado con éxito.",
                error = null,
                isConversationActive = false
            )
        }

        _events.emit(ReminderUiEvent.StopAssistantConversation)
    }

    fun startVoiceReminderFlow() {
        val calendar = Calendar.getInstance()

        _voiceState.value = VoiceReminderState(
            step = VoiceReminderStep.WAITING_FOR_REMINDER_TEXT,
            reminderMonth = calendar.get(Calendar.MONTH) + 1,
            reminderYear = calendar.get(Calendar.YEAR),
            isVoiceFlowActive = true,
            lastAssistantMessage = "Hola. Dime qué recordatorio deseas programar."
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
                    emitVoiceMessage("De acuerdo. No guardaré ese recordatorio.")
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
            emitVoiceMessage("De acuerdo. Cancelé este recordatorio.")
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

                _assistantState.update {
                    it.copy(reminders = reminders)
                }
            } catch (exception: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = if (exception.message.isNullOrBlank()) {
                        UiText.StringResource(R.string.message_unknown_error)
                    } else {
                        UiText.DynamicString(exception.message!!)
                    }
                )

                _assistantState.update {
                    it.copy(
                        isLoading = false,
                        error = exception.message ?: "No fue posible cargar los recordatorios."
                    )
                }
            }
        }
    }

    fun onDateSelected(year: Int, month: Int, day: Int) {
        val current = _formState.value
        _formState.value = current.copy(
            selectedYear = year,
            selectedMonth = month,
            selectedDay = day
        )
    }

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
                    lastAssistantMessage = "Entiendo. ¿Qué recordatorio deseas programar?"
                )
            }

            reminderHour == null || reminderMinute == null -> {
                _voiceState.value = state.copy(
                    step = VoiceReminderStep.WAITING_FOR_TIME,
                    isVoiceFlowActive = true,
                    lastAssistantMessage = "Perfecto. ¿A qué hora deseas este recordatorio?"
                )
            }

            reminderDay == null || reminderMonth == null || reminderYear == null -> {
                _voiceState.value = state.copy(
                    step = VoiceReminderStep.WAITING_FOR_DAY,
                    isVoiceFlowActive = true,
                    lastAssistantMessage = "Muy bien. ¿Para qué día deseas programarlo?"
                )
            }

            else -> {
                saveVoiceReminder()
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
            containsWholeWord(normalizedInput, "pasado manana") -> {
                calendar.add(Calendar.DAY_OF_MONTH, 2)
                return ResolvedDate(
                    day = calendar.get(Calendar.DAY_OF_MONTH),
                    month = calendar.get(Calendar.MONTH) + 1,
                    year = calendar.get(Calendar.YEAR)
                )
            }

            containsWholeWord(normalizedInput, "manana") -> {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
                return ResolvedDate(
                    day = calendar.get(Calendar.DAY_OF_MONTH),
                    month = calendar.get(Calendar.MONTH) + 1,
                    year = calendar.get(Calendar.YEAR)
                )
            }

            containsWholeWord(normalizedInput, "hoy") -> {
                return ResolvedDate(
                    day = calendar.get(Calendar.DAY_OF_MONTH),
                    month = calendar.get(Calendar.MONTH) + 1,
                    year = calendar.get(Calendar.YEAR)
                )
            }
        }

        val explicitDayMatch = Regex("\\b(?:el|dia)\\s+(\\d{1,2})\\b").find(normalizedInput)
        val explicitDay = explicitDayMatch?.groupValues?.getOrNull(1)?.toIntOrNull()

        if (explicitDay != null && explicitDay in 1..31) {
            return resolveDateFromDayNumber(explicitDay)
        }

        return null
    }

    private fun resolveDateFromDayNumber(day: Int): ResolvedDate? {
        val calendarNow = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

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

        if (candidate.before(calendarNow)) {
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

            .replace(Regex("\\bpara\\s+pasado\\s+manana\\b"), "")
            .replace(Regex("\\bpara\\s+manana\\b"), "")
            .replace(Regex("\\bpara\\s+hoy\\b"), "")
            .replace(Regex("\\bpasado\\s+manana\\b"), "")
            .replace(Regex("\\bmanana\\b"), "")
            .replace(Regex("\\bhoy\\b"), "")

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

            .replace(Regex("\\bpara\\s+el\\s+\\d{1,2}\\b"), "")
            .replace(Regex("\\bel\\s+\\d{1,2}\\b"), "")

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

                emitVoiceMessage("Perfecto, tu recordatorio fue guardado con éxito.")
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

    private fun mergeDraftSources(
        message: String,
        aiDraft: ReminderDraft?,
        localDraft: ReminderDraft?,
        currentDraft: ReminderDraft?
    ): ReminderDraft {
        return ReminderDraft(
            text = localDraft?.text
                ?: currentDraft?.text
                ?: aiDraft?.text,

            date = localDraft?.date
                ?: resolveReliableDate(
                    message = message,
                    aiDate = aiDraft?.date,
                    currentDraft = currentDraft
                ),

            time = localDraft?.time
                ?: currentDraft?.time
                ?: aiDraft?.time
        )
    }

    private fun buildQuestionForMissingData(
        draft: ReminderDraft?,
        aiReply: String? = null
    ): String {
        if (draft == null) {
            return aiReply
                ?.trim()
                ?.takeIf { isUsefulAssistantReply(it) }
                ?: "¿Qué deseas recordar?"
        }

        return when {
            draft.text.isNullOrBlank() -> "¿Qué deseas recordar?"
            draft.date.isNullOrBlank() -> "¿Para qué día deseas este recordatorio?"
            draft.time.isNullOrBlank() -> "¿A qué hora deseas este recordatorio?"
            else -> "Perfecto."
        }
    }

    private fun isUsefulAssistantReply(reply: String): Boolean {
        val normalizedReply = normalizeText(reply)

        if (normalizedReply.isBlank()) return false

        val blockedMessages = listOf(
            "no fue posible",
            "intenta nuevamente",
            "no logre interpretar",
            "no logre procesar",
            "solo puedo ayudarte",
            "no fue posible registrar"
        )

        return blockedMessages.none { normalizedReply.contains(it) }
    }

    private fun mergeDraftFromRawMessage(
        message: String,
        currentDraft: ReminderDraft?
    ): ReminderDraft? {
        val normalizedMessage = normalizeText(message)
        val parsedDate = parseNaturalDate(normalizedMessage)
        val parsedTime = parseTime(normalizedMessage)

        val parsedText = when {
            parsedDate != null || parsedTime != null -> {
                extractReminderText(normalizedMessage)
            }

            else -> {
                normalizedMessage.takeIf { it.isNotBlank() }
            }
        }

        val mergedDraft = ReminderDraft(
            text = parsedText?.takeIf { it.isNotBlank() } ?: currentDraft?.text,
            date = parsedDate?.let {
                DateTimeFormatter.formatDate(it.day, it.month, it.year)
            } ?: currentDraft?.date,
            time = parsedTime?.let {
                DateTimeFormatter.formatTime(it.first, it.second)
            } ?: currentDraft?.time
        )

        return mergedDraft.takeIf { hasAnyDraftData(it) }
    }

    private fun hasAnyDraftData(draft: ReminderDraft?): Boolean {
        return draft != null && (
                !draft.text.isNullOrBlank() ||
                        !draft.date.isNullOrBlank() ||
                        !draft.time.isNullOrBlank()
                )
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
            .replace("ñ", "n")
            .replace(",", " ")
            .replace(";", " ")
            .replace(".", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun resolveReliableDate(
        message: String,
        aiDate: String?,
        currentDraft: ReminderDraft?
    ): String? {
        val normalizedMessage = normalizeText(message)

        val localRelativeDate = when {
            containsWholeWord(normalizedMessage, "pasado manana") -> {
                val calendar = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_MONTH, 2)
                }
                DateTimeFormatter.formatDate(
                    calendar.get(Calendar.DAY_OF_MONTH),
                    calendar.get(Calendar.MONTH) + 1,
                    calendar.get(Calendar.YEAR)
                )
            }

            containsWholeWord(normalizedMessage, "manana") -> {
                val calendar = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
                DateTimeFormatter.formatDate(
                    calendar.get(Calendar.DAY_OF_MONTH),
                    calendar.get(Calendar.MONTH) + 1,
                    calendar.get(Calendar.YEAR)
                )
            }

            containsWholeWord(normalizedMessage, "hoy") -> {
                val calendar = Calendar.getInstance()
                DateTimeFormatter.formatDate(
                    calendar.get(Calendar.DAY_OF_MONTH),
                    calendar.get(Calendar.MONTH) + 1,
                    calendar.get(Calendar.YEAR)
                )
            }

            else -> null
        }

        return localRelativeDate
            ?: currentDraft?.date
            ?: aiDate
    }

    private fun sanitizeAiReminderText(value: String?): String? {
        val safeValue = value?.trim().orEmpty()
        if (safeValue.isBlank()) return null

        val normalizedValue = normalizeText(safeValue)

        return when {
            containsWholeWord(normalizedValue, "hoy") -> null
            containsWholeWord(normalizedValue, "manana") -> null
            containsWholeWord(normalizedValue, "pasado manana") -> null
            parseTime(normalizedValue) != null -> null
            else -> safeValue
        }
    }

    private fun sanitizeAiDate(value: String?, userMessage: String): String? {
        val safeValue = value?.trim().orEmpty()
        if (safeValue.isBlank()) return null

        val normalizedUserMessage = normalizeText(userMessage)

        if (
            containsWholeWord(normalizedUserMessage, "hoy") ||
            containsWholeWord(normalizedUserMessage, "manana") ||
            containsWholeWord(normalizedUserMessage, "pasado manana")
        ) {
            return null
        }

        return if (Regex("^\\d{2}/\\d{2}/\\d{4}$").matches(safeValue)) {
            safeValue
        } else {
            null
        }
    }

    private fun sanitizeAiTime(value: String?): String? {
        val safeValue = value?.trim().orEmpty()
        if (safeValue.isBlank()) return null

        return if (Regex("^\\d{2}:\\d{2}$").matches(safeValue)) {
            safeValue
        } else {
            null
        }
    }

    private fun containsWholeWord(text: String, value: String): Boolean {
        val pattern = "\\b${Regex.escape(value)}\\b"
        return Regex(pattern).containsMatchIn(text)
    }

    private fun buildAiContextualMessage(userMessage: String): String {
        val now = Calendar.getInstance()
        val currentDate = DateTimeFormatter.formatDate(
            now.get(Calendar.DAY_OF_MONTH),
            now.get(Calendar.MONTH) + 1,
            now.get(Calendar.YEAR)
        )
        val currentTime = DateTimeFormatter.formatTime(
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE)
        )

        return """
            Contexto actual del sistema:
            - Fecha actual: $currentDate
            - Hora actual: $currentTime
            - Zona horaria local del dispositivo: ${TimeZone.getDefault().id}

            Regla importante:
            - Usa este contexto solo para interpretar mejor el lenguaje natural del usuario.
            - No intentes calcular por tu cuenta la fecha final del recordatorio si el usuario dice hoy, mañana o pasado mañana.
            - La fecha y hora finales serán resueltas por la lógica local de la aplicación.

            Mensaje real del usuario:
            $userMessage
        """.trimIndent()
    }
}