package com.luistureo.voicereminderapp.presentation.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.nlp.ReminderEntityExtractor
import com.luistureo.voicereminderapp.core.nlp.ReminderTextCleaner
import com.luistureo.voicereminderapp.core.utils.DateTimeFormatter
import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.model.ReminderDraft
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

class ReminderViewModel(
    context: Context,
    private val addReminderUseCase: AddReminderUseCase,
    private val getRemindersUseCase: GetRemindersUseCase,
    private val deleteReminderUseCase: DeleteReminderUseCase,
    private val updateReminderUseCase: UpdateReminderUseCase
) : ViewModel() {

    companion object {
        private const val ASSISTANT_TAG = "AssistantDebug"
        private const val MLKIT_TAG = "MLKitDebug"
    }

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

    private data class ParsedTime(
        val hour: Int,
        val minute: Int,
        val isAmbiguous: Boolean
    )

    private data class PendingAmbiguousTime(
        val hour: Int,
        val minute: Int
    )

    private enum class DayPeriod {
        MORNING,
        AFTERNOON,
        NIGHT,
        DAWN
    }

    private val entityExtractor = ReminderEntityExtractor(context.applicationContext)
    private val textCleaner = ReminderTextCleaner()

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
    private var pendingAssistantAmbiguousTime: PendingAmbiguousTime? = null

    init {
        loadReminders()

        viewModelScope.launch {
            runCatching {
                entityExtractor.prepare()
                Log.d(MLKIT_TAG, "Modelo ML Kit preparado correctamente.")
            }.onFailure { exception ->
                Log.e(MLKIT_TAG, "No se pudo preparar ML Kit.", exception)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        entityExtractor.close()
    }

    fun startAssistantSession() {
        pendingDraft = null
        hasSavedInCurrentSession = false
        pendingAssistantAmbiguousTime = null

        Log.d(ASSISTANT_TAG, "Nueva sesión de asistente iniciada.")

        _assistantState.update {
            it.copy(
                recognizedText = "",
                assistantReply = "Dime tu recordatorio",
                pendingDraft = null,
                error = null,
                isLoading = false,
                isConversationActive = true
            )
        }

        viewModelScope.launch {
            _events.emit(ReminderUiEvent.SpeakAssistantReply("Dime tu recordatorio"))
        }
    }

    fun processAssistantMessage(message: String) {
        if (message.isBlank()) return
        if (hasSavedInCurrentSession) return

        Log.d(ASSISTANT_TAG, "Mensaje usuario: $message")

        val normalizedMessage = normalizeText(message)

        if (isGeneralCancelCommand(normalizedMessage)) {
            viewModelScope.launch {
                Log.d(ASSISTANT_TAG, "El usuario canceló la conversación.")
                clearAssistantConversation()
                _assistantState.update {
                    it.copy(
                        assistantReply = "De acuerdo. He cancelado la conversación.",
                        recognizedText = message,
                        isLoading = false,
                        error = null,
                        isConversationActive = false
                    )
                }
                _events.emit(
                    ReminderUiEvent.SpeakAssistantReply(
                        "De acuerdo. He cancelado la conversación."
                    )
                )
                _events.emit(ReminderUiEvent.StopAssistantConversation)
            }
            return
        }

        viewModelScope.launch {
            _assistantState.update {
                it.copy(
                    isLoading = true,
                    recognizedText = message,
                    error = null,
                    isConversationActive = true
                )
            }

            val parsedDate = parseNaturalDate(normalizedMessage)
            val parsedTime = parseTime(normalizedMessage)

            Log.d(ASSISTANT_TAG, "Fecha detectada localmente: $parsedDate")
            Log.d(ASSISTANT_TAG, "Hora detectada localmente: $parsedTime")

            val mlKitCleanedText = extractReminderTextWithMlKit(message)
            Log.d(ASSISTANT_TAG, "Texto útil detectado por ML Kit: $mlKitCleanedText")

            val resolvedPendingAmbiguousTime = resolvePendingAssistantAmbiguousTime(normalizedMessage)

            if (resolvedPendingAmbiguousTime != null) {
                Log.d(
                    ASSISTANT_TAG,
                    "Se resolvió una hora ambigua pendiente: $resolvedPendingAmbiguousTime"
                )
            }

            val localDraft = mergeDraftFromRawMessage(
                message = message,
                currentDraft = pendingDraft,
                mlKitReminderText = mlKitCleanedText,
                parsedDate = parsedDate,
                parsedTime = parsedTime,
                resolvedPendingAmbiguousTime = resolvedPendingAmbiguousTime
            )

            Log.d(ASSISTANT_TAG, "Draft local: $localDraft")

            val mergedDraft = mergeDraftSources(
                message = message,
                localDraft = localDraft,
                currentDraft = pendingDraft
            )

            Log.d(ASSISTANT_TAG, "Draft fusionado: $mergedDraft")

            pendingDraft = mergedDraft.takeIf { hasAnyDraftData(it) }

            Log.d(ASSISTANT_TAG, "Pending draft final: $pendingDraft")
            Log.d(
                ASSISTANT_TAG,
                "Hora ambigua pendiente actual: $pendingAssistantAmbiguousTime"
            )

            if (
                !hasSavedInCurrentSession &&
                pendingDraft?.isReadyToSave() == true &&
                pendingAssistantAmbiguousTime == null
            ) {
                Log.d(ASSISTANT_TAG, "El draft está completo. Se guardará automáticamente.")
                saveDraftReminder(pendingDraft!!)
                return@launch
            }

            val assistantReply = buildQuestionForMissingData(pendingDraft)
            Log.d(ASSISTANT_TAG, "Respuesta asistente: $assistantReply")

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

    fun clearAssistantConversation() {
        pendingDraft = null
        hasSavedInCurrentSession = false
        pendingAssistantAmbiguousTime = null

        Log.d(ASSISTANT_TAG, "Conversación limpiada.")

        _assistantState.value = AssistantUiState(
            reminders = _assistantState.value.reminders,
            isConversationActive = false
        )
    }

    private suspend fun saveDraftReminder(draft: ReminderDraft) {
        val reminderDetail = draft.text.orEmpty().trim()
        val reminderDate = draft.date.orEmpty()
        val reminderTime = draft.time.orEmpty()

        if (pendingAssistantAmbiguousTime != null) {
            val ambiguityReply = buildQuestionForMissingData(draft)

            _assistantState.update {
                it.copy(
                    isLoading = false,
                    assistantReply = ambiguityReply,
                    pendingDraft = draft,
                    error = null,
                    isConversationActive = true
                )
            }

            _events.emit(ReminderUiEvent.SpeakAssistantReply(ambiguityReply))
            return
        }

        if (reminderDetail.isBlank() || reminderDate.isBlank() || reminderTime.isBlank()) {
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

        val triggerTimeMillis = buildTriggerTimeMillisFromDraft(
            reminderDate = reminderDate,
            reminderTime = reminderTime
        )

        if (triggerTimeMillis == null || triggerTimeMillis <= System.currentTimeMillis()) {
            val invalidDateReply =
                "La fecha u hora indicadas ya pasaron o no son válidas. Indícame una fecha u hora futura."

            _assistantState.update {
                it.copy(
                    isLoading = false,
                    assistantReply = invalidDateReply,
                    pendingDraft = draft.copy(date = null, time = null),
                    error = null,
                    isConversationActive = true
                )
            }

            pendingDraft = draft.copy(date = null, time = null)
            pendingAssistantAmbiguousTime = null

            _events.emit(ReminderUiEvent.SpeakAssistantReply(invalidDateReply))
            return
        }

        val reminderTitle = extractReminderTitle(reminderDetail)

        val reminder = Reminder(
            title = reminderTitle,
            detail = reminderDetail,
            date = reminderDate,
            time = reminderTime,
            isCompleted = false
        )

        addReminderUseCase(reminder)
        loadReminders()

        _events.emit(
            ReminderUiEvent.ScheduleReminder(
                reminderTitle = reminder.title,
                reminderDetail = reminder.detail,
                reminderDate = reminder.date,
                reminderTime = reminder.time,
                triggerTimeMillis = triggerTimeMillis
            )
        )

        hasSavedInCurrentSession = true

        _events.emit(ReminderUiEvent.ShowMessage("Recordatorio guardado correctamente."))
        _events.emit(
            ReminderUiEvent.SpeakAssistantReply(
                "Perfecto, tu recordatorio fue guardado con éxito."
            )
        )

        pendingDraft = null
        pendingAssistantAmbiguousTime = null

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

        var currentState = _voiceState.value
        val normalizedInput = normalizeText(cleanedInput)

        if (!currentState.isVoiceFlowActive) {
            startVoiceReminderFlow()
            currentState = _voiceState.value
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

            val reminderDetail = text.trim()

            if (reminderDetail.isBlank()) {
                _events.emit(ReminderUiEvent.ShowMessage("No hay texto para guardar"))
                return@launch
            }

            if (!hasDate || !hasTime) {
                _events.emit(ReminderUiEvent.ShowMessage("Selecciona una fecha y hora válidas"))
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
                _events.emit(ReminderUiEvent.ShowMessage("Selecciona una fecha y hora futuras"))
                return@launch
            }

            val reminder = Reminder(
                title = extractReminderTitle(reminderDetail),
                detail = reminderDetail,
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
                        reminderTitle = reminder.title,
                        reminderDetail = reminder.detail,
                        reminderDate = reminder.date,
                        reminderTime = reminder.time,
                        triggerTimeMillis = triggerTimeMillis
                    )
                )

                _events.emit(ReminderUiEvent.ClearForm)
                _events.emit(ReminderUiEvent.ShowMessage("Recordatorio guardado correctamente"))
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

        val parsedReminderText = input.trim().takeIf { it.isNotBlank() }

        return ParsedVoiceInput(
            reminderText = parsedReminderText,
            resolvedDay = parsedDate?.day,
            resolvedMonth = parsedDate?.month,
            resolvedYear = parsedDate?.year,
            resolvedHour = parsedTime?.hour,
            resolvedMinute = parsedTime?.minute
        )
    }

    private suspend fun extractReminderTextWithMlKit(message: String): String? {
        return runCatching {
            val annotations = entityExtractor.extractDateTimeEntities(
                text = message,
                referenceTimeMillis = System.currentTimeMillis()
            )

            Log.d(MLKIT_TAG, "Texto original: $message")
            Log.d(MLKIT_TAG, "Entidades detectadas: $annotations")

            val cleanedText = textCleaner.removeDetectedSpans(
                originalText = message,
                annotations = annotations
            )

            Log.d(MLKIT_TAG, "Texto limpio ML Kit: $cleanedText")

            cleanedText
        }.onFailure { exception ->
            Log.e(MLKIT_TAG, "Error procesando texto con ML Kit.", exception)
        }.getOrNull()
            ?.let { normalizeText(it) }
            ?.takeIf { it.isNotBlank() }
    }

    private fun parseNaturalDate(input: String): ResolvedDate? {
        val normalizedInput = normalizeText(input)
        val now = Calendar.getInstance()

        when {
            containsWholeWord(normalizedInput, "pasado manana") -> {
                val calendar = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_MONTH, 2)
                }
                return ResolvedDate(
                    day = calendar.get(Calendar.DAY_OF_MONTH),
                    month = calendar.get(Calendar.MONTH) + 1,
                    year = calendar.get(Calendar.YEAR)
                )
            }

            containsWholeWord(normalizedInput, "manana") -> {
                val calendar = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
                return ResolvedDate(
                    day = calendar.get(Calendar.DAY_OF_MONTH),
                    month = calendar.get(Calendar.MONTH) + 1,
                    year = calendar.get(Calendar.YEAR)
                )
            }

            containsWholeWord(normalizedInput, "hoy") -> {
                return ResolvedDate(
                    day = now.get(Calendar.DAY_OF_MONTH),
                    month = now.get(Calendar.MONTH) + 1,
                    year = now.get(Calendar.YEAR)
                )
            }
        }

        val nextWeek = containsWholeWord(normalizedInput, "proxima semana") ||
                containsWholeWord(normalizedInput, "la proxima semana")

        val daysOfWeek = mapOf(
            "lunes" to Calendar.MONDAY,
            "martes" to Calendar.TUESDAY,
            "miercoles" to Calendar.WEDNESDAY,
            "jueves" to Calendar.THURSDAY,
            "viernes" to Calendar.FRIDAY,
            "sabado" to Calendar.SATURDAY,
            "domingo" to Calendar.SUNDAY
        )

        for ((dayName, calendarDay) in daysOfWeek) {
            if (containsWholeWord(normalizedInput, dayName)) {
                val candidate = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val currentDow = candidate.get(Calendar.DAY_OF_WEEK)
                var diff = (calendarDay - currentDow + 7) % 7

                if (diff == 0) {
                    diff = 7
                }

                if (nextWeek) {
                    diff += 7
                }

                candidate.add(Calendar.DAY_OF_MONTH, diff)

                return ResolvedDate(
                    day = candidate.get(Calendar.DAY_OF_MONTH),
                    month = candidate.get(Calendar.MONTH) + 1,
                    year = candidate.get(Calendar.YEAR)
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

    private fun saveVoiceReminder() {
        val state = _voiceState.value

        val reminderDay = state.reminderDay ?: return
        val reminderMonth = state.reminderMonth ?: return
        val reminderYear = state.reminderYear ?: return
        val reminderHour = state.reminderHour ?: return
        val reminderMinute = state.reminderMinute ?: return
        val reminderDetail = state.reminderText.trim()

        if (reminderDetail.isBlank()) {
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
            title = extractReminderTitle(reminderDetail),
            detail = reminderDetail,
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
                        reminderTitle = reminder.title,
                        reminderDetail = reminder.detail,
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
        localDraft: ReminderDraft?,
        currentDraft: ReminderDraft?
    ): ReminderDraft {

        val normalizedMessage = normalizeText(message)

        val hasDateInMessage = parseNaturalDate(normalizedMessage) != null
        val hasTimeInMessage = parseTime(normalizedMessage) != null

        val hasRealText = !localDraft?.text.isNullOrBlank()

        val shouldPreserveCurrentText =
            (hasDateInMessage || hasTimeInMessage || isLikelyOnlyTemporalMessage(normalizedMessage)) &&
                    isLikelyOnlyTemporalMessage(normalizedMessage) &&
                    !hasRealText

        val resolvedText = when {
            shouldPreserveCurrentText -> currentDraft?.text
            hasRealText -> localDraft?.text
            !currentDraft?.text.isNullOrBlank() -> currentDraft?.text
            else -> null
        }

        return ReminderDraft(
            text = resolvedText,
            date = localDraft?.date ?: resolveReliableDate(
                message = message,
                currentDraft = currentDraft
            ),
            time = localDraft?.time ?: currentDraft?.time
        )
    }

    private fun buildQuestionForMissingData(draft: ReminderDraft?): String {
        if (draft == null) {
            return "¿Qué deseas recordar?"
        }

        if (draft.text.isNullOrBlank()) {
            return "¿Qué deseas recordar?"
        }

        if (draft.date.isNullOrBlank()) {
            return "¿Para qué día deseas este recordatorio?"
        }

        if (draft.time.isNullOrBlank()) {
            return "¿A qué hora deseas este recordatorio?"
        }

        pendingAssistantAmbiguousTime?.let { ambiguousTime ->
            return buildAmbiguousTimeQuestion(ambiguousTime)
        }

        return "Perfecto."
    }

    private fun mergeDraftFromRawMessage(
        message: String,
        currentDraft: ReminderDraft?,
        mlKitReminderText: String?,
        parsedDate: ResolvedDate?,
        parsedTime: ParsedTime?,
        resolvedPendingAmbiguousTime: String?
    ): ReminderDraft? {
        val normalizedMessage = normalizeText(message)

        if (resolvedPendingAmbiguousTime != null) {
            return ReminderDraft(
                text = currentDraft?.text,
                date = currentDraft?.date,
                time = resolvedPendingAmbiguousTime
            ).takeIf { hasAnyDraftData(it) }
        }

        val shouldKeepCurrentText = currentDraft?.text != null &&
                isLikelyOnlyTemporalMessage(normalizedMessage)

        val parsedText = when {
            shouldKeepCurrentText -> currentDraft.text
            else -> message.trim().takeIf { it.isNotBlank() }
        }

        if (parsedTime != null) {
            if (parsedTime.isAmbiguous) {
                pendingAssistantAmbiguousTime = PendingAmbiguousTime(
                    hour = parsedTime.hour,
                    minute = parsedTime.minute
                )
            } else {
                pendingAssistantAmbiguousTime = null
            }
        }

        val mergedDraft = ReminderDraft(
            text = parsedText?.takeIf { it.isNotBlank() } ?: currentDraft?.text,
            date = parsedDate?.let {
                DateTimeFormatter.formatDate(it.day, it.month, it.year)
            } ?: currentDraft?.date,
            time = parsedTime?.let {
                DateTimeFormatter.formatTime(it.hour, it.minute)
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

    private fun parseTime(input: String): ParsedTime? {
        val text = normalizeText(input)

        val patterns = listOf(
            Regex("\\ba\\s+las\\s+(\\d{1,2}):(\\d{1,2})\\s*horas?\\b"),
            Regex("\\ba\\s+la\\s+(\\d{1,2}):(\\d{1,2})\\s*horas?\\b"),
            Regex("\\ba\\s+las\\s+(\\d{1,2}):(\\d{1,2})\\b"),
            Regex("\\ba\\s+la\\s+(\\d{1,2}):(\\d{1,2})\\b"),

            Regex("\\ba\\s+las\\s+(\\d{1,2})\\s+y\\s+(\\d{1,2})\\s*horas?\\b"),
            Regex("\\ba\\s+la\\s+(\\d{1,2})\\s+y\\s+(\\d{1,2})\\s*horas?\\b"),
            Regex("\\ba\\s+las\\s+(\\d{1,2})\\s+y\\s+(\\d{1,2})\\b"),
            Regex("\\ba\\s+la\\s+(\\d{1,2})\\s+y\\s+(\\d{1,2})\\b"),

            Regex("\\ba\\s+las\\s+(\\d{1,2})\\s*(am|pm)\\b"),
            Regex("\\ba\\s+la\\s+(\\d{1,2})\\s*(am|pm)\\b"),

            Regex("\\ba\\s+las\\s+(\\d{1,2})\\s+de\\s+la\\s+(manana|tarde|noche|madrugada)\\b"),
            Regex("\\ba\\s+la\\s+(\\d{1,2})\\s+de\\s+la\\s+(manana|tarde|noche|madrugada)\\b"),

            Regex("\\ba\\s+las\\s+(\\d{1,2})\\s*horas\\b"),
            Regex("\\ba\\s+la\\s+(\\d{1,2})\\s*horas\\b"),
            Regex("\\blas\\s+(\\d{1,2})\\s*horas\\b"),
            Regex("\\bla\\s+(\\d{1,2})\\s*hora\\b"),

            Regex("\\ba\\s+las\\s+(\\d{1,2})\\b"),
            Regex("\\ba\\s+la\\s+(\\d{1,2})\\b")
        )

        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            parseMatchedTime(match.value, match.groupValues)?.let { return it }
        }

        val exactTimeOnlyPatterns = listOf(
            Regex("^(\\d{1,2}):(\\d{1,2})\\s*horas?$"),
            Regex("^(\\d{1,2}):(\\d{1,2})$"),
            Regex("^(\\d{1,2})\\s+y\\s+(\\d{1,2})\\s*horas?$"),
            Regex("^(\\d{1,2})\\s+y\\s+(\\d{1,2})$"),
            Regex("^(\\d{1,2})\\s*(am|pm)$"),
            Regex("^(\\d{1,2})\\s+de\\s+la\\s+(manana|tarde|noche|madrugada)$"),
            Regex("^(\\d{1,2})\\s*horas?$"),
            Regex("^(\\d{1,2})$")
        )

        for (pattern in exactTimeOnlyPatterns) {
            val match = pattern.find(text) ?: continue
            parseMatchedTime(match.value, match.groupValues)?.let { return it }
        }

        return null
    }

    private fun parseMatchedTime(
        fullMatch: String,
        values: List<String>
    ): ParsedTime? {
        val normalizedMatch = normalizeText(fullMatch)

        return when {
            values.size >= 3 && normalizedMatch.contains(":") -> {
                val rawHour = values[1].toIntOrNull() ?: return null
                val minute = values[2].toIntOrNull() ?: return null
                if (minute !in 0..59) return null

                if (hasExplicitTimeContext(normalizedMatch)) {
                    val hour = adjustHourByPeriod(normalizedMatch, rawHour) ?: return null
                    ParsedTime(hour = hour, minute = minute, isAmbiguous = false)
                } else {
                    val hour = rawHour.takeIf { it in 0..23 } ?: return null
                    ParsedTime(
                        hour = hour,
                        minute = minute,
                        isAmbiguous = isAmbiguousHourWithoutContext(hour)
                    )
                }
            }

            values.size >= 3 && normalizedMatch.contains(" y ") -> {
                val rawHour = values[1].toIntOrNull() ?: return null
                val minute = values[2].toIntOrNull() ?: return null
                if (minute !in 0..59) return null

                if (hasExplicitTimeContext(normalizedMatch)) {
                    val hour = adjustHourByPeriod(normalizedMatch, rawHour) ?: return null
                    ParsedTime(hour = hour, minute = minute, isAmbiguous = false)
                } else {
                    val hour = rawHour.takeIf { it in 0..23 } ?: return null
                    ParsedTime(
                        hour = hour,
                        minute = minute,
                        isAmbiguous = isAmbiguousHourWithoutContext(hour)
                    )
                }
            }

            values.size >= 3 && (values[2] == "am" || values[2] == "pm") -> {
                val rawHour = values[1].toIntOrNull() ?: return null
                val hour = adjustHourByPeriod(normalizedMatch, rawHour) ?: return null
                ParsedTime(hour = hour, minute = 0, isAmbiguous = false)
            }

            values.size >= 3 && (
                    values[2] == "manana" ||
                            values[2] == "tarde" ||
                            values[2] == "noche" ||
                            values[2] == "madrugada"
                    ) -> {
                val rawHour = values[1].toIntOrNull() ?: return null
                val hour = adjustHourByPeriod(normalizedMatch, rawHour) ?: return null
                ParsedTime(hour = hour, minute = 0, isAmbiguous = false)
            }

            values.size >= 2 -> {
                val rawHour = values[1].toIntOrNull() ?: return null
                val hour = rawHour.takeIf { it in 0..23 } ?: return null
                ParsedTime(
                    hour = hour,
                    minute = 0,
                    isAmbiguous = isAmbiguousHourWithoutContext(hour)
                )
            }

            else -> null
        }
    }

    private fun hasExplicitTimeContext(text: String): Boolean {
        val normalizedText = normalizeText(text)

        return normalizedText.contains("am") ||
                normalizedText.contains("pm") ||
                normalizedText.contains("manana") ||
                normalizedText.contains("tarde") ||
                normalizedText.contains("noche") ||
                normalizedText.contains("madrugada") ||
                normalizedText.contains("medianoche") ||
                normalizedText.contains("mediodia")
    }

    private fun isAmbiguousHourWithoutContext(hour: Int): Boolean {
        return hour in 1..12
    }

    private fun buildAmbiguousTimeQuestion(ambiguousTime: PendingAmbiguousTime): String {
        return when (ambiguousTime.hour) {
            in 1..8 -> "¿Es en la mañana o en la tarde?"
            in 9..11 -> "¿Es en la mañana o en la noche?"
            12 -> "¿Es a las 12 de la mañana o de la tarde?"
            else -> "¿Ese horario es en la mañana, tarde o noche?"
        }
    }

    private fun resolvePendingAssistantAmbiguousTime(message: String): String? {
        val pendingTime = pendingAssistantAmbiguousTime ?: return null
        val period = extractDayPeriod(message) ?: return null
        val resolvedHour = resolveHourByConfiguredRanges(
            rawHour = pendingTime.hour,
            period = period
        ) ?: return null

        pendingAssistantAmbiguousTime = null

        return DateTimeFormatter.formatTime(
            resolvedHour,
            pendingTime.minute
        )
    }

    private fun extractDayPeriod(text: String): DayPeriod? {
        val normalizedText = normalizeText(text)

        return when {
            containsWholeWord(normalizedText, "madrugada") -> DayPeriod.DAWN
            containsWholeWord(normalizedText, "manana") ||
                    containsWholeWord(normalizedText, "am") -> DayPeriod.MORNING
            containsWholeWord(normalizedText, "tarde") -> DayPeriod.AFTERNOON
            containsWholeWord(normalizedText, "noche") ||
                    containsWholeWord(normalizedText, "pm") -> DayPeriod.NIGHT
            else -> null
        }
    }

    private fun resolveHourByConfiguredRanges(
        rawHour: Int,
        period: DayPeriod
    ): Int? {
        return when (period) {
            DayPeriod.DAWN,
            DayPeriod.MORNING -> {
                when (rawHour) {
                    in 1..11 -> rawHour
                    12 -> 0
                    0 -> 0
                    else -> null
                }
            }

            DayPeriod.AFTERNOON -> {
                when (rawHour) {
                    in 1..8 -> rawHour + 12
                    12 -> 12
                    in 13..20 -> rawHour
                    else -> null
                }
            }

            DayPeriod.NIGHT -> {
                when (rawHour) {
                    in 9..11 -> rawHour + 12
                    in 21..23 -> rawHour
                    else -> null
                }
            }
        }
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
                0 -> 0
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

        if (normalizedText.contains("tarde")) {
            return when (rawHour) {
                in 1..8 -> rawHour + 12
                12 -> 12
                in 13..20 -> rawHour
                else -> null
            }
        }

        if (normalizedText.contains("noche")) {
            return when (rawHour) {
                in 9..11 -> rawHour + 12
                in 21..23 -> rawHour
                else -> null
            }
        }

        if (normalizedText.contains("pm")) {
            return when (rawHour) {
                in 1..11 -> rawHour + 12
                12 -> 12
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
        currentDraft: ReminderDraft?
    ): String? {
        val normalizedMessage = normalizeText(message)

        val localParsedDate = parseNaturalDate(normalizedMessage)?.let {
            DateTimeFormatter.formatDate(it.day, it.month, it.year)
        }

        return localParsedDate ?: currentDraft?.date
    }

    private fun containsWholeWord(text: String, value: String): Boolean {
        val pattern = "\\b${Regex.escape(value)}\\b"
        return Regex(pattern).containsMatchIn(text)
    }

    private fun isLikelyOnlyTemporalMessage(text: String): Boolean {
        val cleaned = normalizeText(text)
            .replace(Regex("\\bpara\\b"), " ")
            .replace(Regex("\\bel\\b"), " ")
            .replace(Regex("\\bla\\b"), " ")
            .replace(Regex("\\bdia\\b"), " ")
            .replace(Regex("\\ba\\b"), " ")
            .replace(Regex("\\blas\\b"), " ")
            .replace(Regex("\\bde\\b"), " ")
            .replace(Regex("\\ben\\b"), " ")
            .replace(Regex("\\bhoras?\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleaned.isBlank()) return true

        val temporalKeywords = listOf(
            "hoy", "manana", "pasado manana",
            "lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo",
            "proxima semana", "am", "pm",
            "manana", "tarde", "noche", "madrugada",
            "medianoche", "mediodia"
        )

        val hasKeyword = temporalKeywords.any { cleaned.contains(it) }
        val hasOnlyNumbersAndTime = Regex("^[0-9:\\s]+$").matches(cleaned)

        return hasKeyword || hasOnlyNumbersAndTime
    }

    private fun buildTriggerTimeMillisFromDraft(
        reminderDate: String,
        reminderTime: String
    ): Long? {
        val dateParts = reminderDate.split("/")
        val timeParts = reminderTime.split(":")

        if (dateParts.size != 3 || timeParts.size != 2) {
            return null
        }

        val day = dateParts[0].toIntOrNull() ?: return null
        val month = dateParts[1].toIntOrNull() ?: return null
        val year = dateParts[2].toIntOrNull() ?: return null
        val hour = timeParts[0].toIntOrNull() ?: return null
        val minute = timeParts[1].toIntOrNull() ?: return null

        if (day !in 1..31 || month !in 1..12 || hour !in 0..23 || minute !in 0..59) {
            return null
        }

        return DateTimeFormatter.buildTriggerTimeMillis(
            year = year,
            month = month,
            day = day,
            hour = hour,
            minute = minute
        )
    }

    private fun extractReminderTitle(detail: String): String {
        var text = normalizeText(detail)

        val removalPatterns = listOf(
            Regex("\\b(hoy|manana|pasado manana|ayer)\\b"),
            Regex("\\b(para hoy|para manana|para pasado manana)\\b"),
            Regex("\\b(esta noche|esta tarde|esta manana|esta madrugada)\\b"),
            Regex("\\b(pronto|mas tarde)\\b"),

            Regex("\\b(esta semana|proxima semana|la proxima semana|este fin de semana|el fin de semana)\\b"),
            Regex("\\b(este mes|el proximo mes|proximo mes|este ano|el proximo ano|proximo ano)\\b"),

            Regex("\\b(para el|para este|el|este)?\\s*(lunes|martes|miercoles|jueves|viernes|sabado|domingo)\\b"),

            Regex("\\b(el dia|dia|el)\\s+\\d{1,2}\\b"),
            Regex("\\bpara el\\s+\\d{1,2}\\b"),
            Regex("\\b\\d{1,2}\\s+de\\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre)\\b"),
            Regex("\\b\\d{1,2}/\\d{1,2}(/\\d{2,4})?\\b"),
            Regex("\\b\\d{1,2}-\\d{1,2}(-\\d{2,4})?\\b"),

            Regex("\\ba\\s+las\\s+\\d{1,2}:\\d{1,2}\\s*(horas?|hrs?|am|pm)?\\b"),
            Regex("\\ba\\s+la\\s+\\d{1,2}:\\d{1,2}\\s*(horas?|hrs?|am|pm)?\\b"),
            Regex("\\ba\\s+las\\s+\\d{1,2}\\s+y\\s+\\d{1,2}\\s*(horas?|minutos?)?\\b"),
            Regex("\\ba\\s+la\\s+\\d{1,2}\\s+y\\s+\\d{1,2}\\s*(horas?|minutos?)?\\b"),
            Regex("\\ba\\s+las\\s+\\d{1,2}\\s*(horas?|hrs?|am|pm)\\b"),
            Regex("\\ba\\s+la\\s+\\d{1,2}\\s*(hora|horas|hr|hrs|am|pm)\\b"),
            Regex("\\ba\\s+las\\s+\\d{1,2}\\b"),
            Regex("\\ba\\s+la\\s+\\d{1,2}\\b"),

            Regex("\\b\\d{1,2}:\\d{1,2}\\s*(horas?|hrs?|am|pm)?\\b"),
            Regex("\\b\\d{1,2}\\s+y\\s+\\d{1,2}\\s*(horas?|minutos?)\\b"),
            Regex("\\b\\d{1,2}\\s*(am|pm)\\b"),
            Regex("\\b\\d{1,2}\\s*horas?\\b"),
            Regex("\\b\\d{1,2}\\s*hrs?\\b"),

            Regex("\\b(de la|en la|por la)\\s+(manana|tarde|noche|madrugada)\\b"),
            Regex("\\b(al mediodia|a medianoche|mediodia|medianoche)\\b"),

            Regex("\\b(para el dia|para la fecha|para las|para la hora de|a eso de las|como a las)\\b"),
            Regex("\\b\\d{1,2}\\b(?=\\s*(de la manana|de la tarde|de la noche|de la madrugada|am|pm|horas?|hrs?)\\b)")
        )

        removalPatterns.forEach { pattern ->
            text = text.replace(pattern, " ")
        }

        text = text
            .replace(",", " ")
            .replace(";", " ")
            .replace(":", " ")
            .replace(".", " ")
            .replace("(", " ")
            .replace(")", " ")
            .replace("-", " ")
            .replace("_", " ")

        text = text
            .replace(
                Regex("^\\b(recordar|recuerda|recordarme|recuerdame|anotar|anota|agendar|agenda|programar|programa|guardar|guarda|crear|crea)\\b\\s*"),
                ""
            )

        text = text
            .replace(Regex("\\b(el|la|los|las|de|del|para|por|a|al|en|dia|hora|horas)\\b"), " ")
            .replace(Regex("\\b\\d{1,2}\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (text.isBlank()) {
            return "Recordatorio"
        }

        return text.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
    }

    private fun buildReminderDetail(detail: String, date: String, time: String): String {
        return detail.trim().ifBlank { "Recordatorio" }
    }
}
