package com.luistureo.voicereminderapp.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.alarm.ReminderScheduler
import com.luistureo.voicereminderapp.core.nlp.ReminderEntityExtractor
import com.luistureo.voicereminderapp.core.nlp.ReminderTextCleaner
import com.luistureo.voicereminderapp.core.nlp.VoiceReminderLanguageHelper
import com.luistureo.voicereminderapp.core.reminder.ReminderDraftFormStateResolver
import com.luistureo.voicereminderapp.core.reminder.ReminderDraftPromptIntent
import com.luistureo.voicereminderapp.core.reminder.ReminderDraftPromptIntentResolver
import com.luistureo.voicereminderapp.core.reminder.ReminderDraftValidationIssue
import com.luistureo.voicereminderapp.core.reminder.ReminderDraftValidator
import com.luistureo.voicereminderapp.core.reminder.ReminderOccurrenceCalculatorCore
import com.luistureo.voicereminderapp.core.reminder.ReminderScheduleStateResolver
import com.luistureo.voicereminderapp.core.utils.DateTimeFormatterCore
import com.luistureo.voicereminderapp.core.utils.ReminderDisplayFormatter
import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.model.ReminderDraft
import com.luistureo.voicereminderapp.domain.model.ReminderSource
import com.luistureo.voicereminderapp.domain.usecase.DeleteReminderUseCase
import com.luistureo.voicereminderapp.domain.usecase.GetReminderByIdUseCase
import com.luistureo.voicereminderapp.domain.usecase.GetRemindersUseCase
import com.luistureo.voicereminderapp.domain.usecase.SaveReminderDraftUseCase
import com.luistureo.voicereminderapp.domain.usecase.UpdateReminderUseCase
import com.luistureo.voicereminderapp.presentation.common.UiText
import com.luistureo.voicereminderapp.presentation.state.HomeReminderListItem
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
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

class ReminderViewModel(
    context: Context,
    private val saveReminderDraftUseCase: SaveReminderDraftUseCase,
    private val getRemindersUseCase: GetRemindersUseCase,
    private val getReminderByIdUseCase: GetReminderByIdUseCase,
    private val deleteReminderUseCase: DeleteReminderUseCase,
    private val updateReminderUseCase: UpdateReminderUseCase
) : ViewModel() {

    private data class ParsedVoiceInput(
        val reminderText: String? = null,
        val resolvedDay: Int? = null,
        val resolvedMonth: Int? = null,
        val resolvedYear: Int? = null,
        val resolvedHour: Int? = null,
        val resolvedMinute: Int? = null,
        val isUrgent: Boolean = false
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
    private val scheduleStateResolver = ReminderScheduleStateResolver()
    private val reminderScheduler = ReminderScheduler(context.applicationContext)
    private val zoneId = ZoneId.systemDefault()
    private val singleReminderOnlyReply =
        "Las repeticiones debes configurarlas manualmente. Por voz solo puedo crear recordatorios de una sola vez."

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
        if (message.isBlank() || hasSavedInCurrentSession) return

        val normalizedMessage = normalizeText(message)

        if (isGeneralCancelCommand(normalizedMessage)) {
            viewModelScope.launch {
                clearAssistantConversation()
                _assistantState.update {
                    it.copy(
                        assistantReply = "De acuerdo. He cancelado la conversacion.",
                        recognizedText = message,
                        isLoading = false,
                        error = null,
                        isConversationActive = false
                    )
                }
                _events.emit(
                    ReminderUiEvent.SpeakAssistantReply(
                        "De acuerdo. He cancelado la conversacion."
                    )
                )
                _events.emit(ReminderUiEvent.StopAssistantConversation)
            }
            return
        }

        if (containsRecurringRequest(normalizedMessage)) {
            viewModelScope.launch {
                val reply = singleReminderOnlyReply
                _assistantState.update {
                    it.copy(
                        recognizedText = message,
                        assistantReply = reply,
                        isLoading = false,
                        error = null,
                        isConversationActive = true
                    )
                }
                _events.emit(ReminderUiEvent.SpeakAssistantReply(reply))
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
            val mlKitCleanedText = extractReminderTextWithMlKit(message)
            val resolvedPendingAmbiguousTime =
                resolvePendingAssistantAmbiguousTime(normalizedMessage)

            val localDraft = mergeDraftFromRawMessage(
                message = message,
                currentDraft = pendingDraft,
                mlKitReminderText = mlKitCleanedText,
                parsedDate = parsedDate,
                parsedTime = parsedTime,
                resolvedPendingAmbiguousTime = resolvedPendingAmbiguousTime,
                isUrgent = containsUrgentSignal(normalizedMessage)
            )

            val mergedDraft = mergeDraftSources(
                message = message,
                localDraft = localDraft,
                currentDraft = pendingDraft
            )

            pendingDraft = mergedDraft.takeIf { hasAnyDraftData(it) }
            val pendingDraftFormState = pendingDraft?.let(ReminderDraftFormStateResolver::resolve)

            if (
                !hasSavedInCurrentSession &&
                pendingDraftFormState?.isReadyToSave == true &&
                pendingAssistantAmbiguousTime == null
            ) {
                saveAssistantDraft(pendingDraft!!)
                return@launch
            }

            val assistantReply = buildQuestionForMissingData(pendingDraft)

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

        _assistantState.value = AssistantUiState(
            reminders = _assistantState.value.reminders,
            isConversationActive = false
        )
    }

    fun startVoiceReminderFlow() {
        val calendar = Calendar.getInstance()
        _voiceState.value = VoiceReminderState(
            step = VoiceReminderStep.WAITING_FOR_REMINDER_TEXT,
            reminderMonth = calendar.get(Calendar.MONTH) + 1,
            reminderYear = calendar.get(Calendar.YEAR),
            isVoiceFlowActive = true,
            lastAssistantMessage = "Hola. Dime que recordatorio deseas programar."
        )
    }

    fun processVoiceInput(input: String) {
        val cleanedInput = input.trim()

        if (cleanedInput.isBlank()) {
            emitVoiceMessage("No logre entenderte bien. Intenta nuevamente.")
            return
        }

        val normalizedInput = normalizeText(cleanedInput)

        if (containsRecurringRequest(normalizedInput)) {
            emitVoiceMessage(singleReminderOnlyReply)
            return
        }

        if (isGeneralCancelCommand(normalizedInput)) {
            viewModelScope.launch {
                resetVoiceState()
                _events.emit(
                    ReminderUiEvent.SpeakAssistantReply(
                        "De acuerdo. Cancele este recordatorio."
                    )
                )
            }
            return
        }

        var currentState = _voiceState.value
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
                    viewModelScope.launch {
                        resetVoiceState()
                        _events.emit(
                            ReminderUiEvent.SpeakAssistantReply(
                                "De acuerdo. No guardare ese recordatorio."
                            )
                        )
                    }
                    return
                }

                else -> {
                    val correctedState = mergeVoiceStateWithInput(currentState, cleanedInput)
                    guideVoiceConversation(correctedState)
                    return
                }
            }
        }

        val newState = mergeVoiceStateWithInput(currentState, cleanedInput)
        guideVoiceConversation(newState)
    }

    fun loadReminders() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    formState = _formState.value
                )
            }

            try {
                val reminders = getRemindersUseCase()
                val homeTimelineItems = buildHomeTimelineItems(reminders)
                reminderScheduler.syncReminderSchedules(reminders)

                _uiState.update {
                    it.copy(
                        reminders = reminders,
                        homeTimelineItems = homeTimelineItems,
                        isLoading = false,
                        error = null,
                        formState = _formState.value
                    )
                }

                _assistantState.update { state ->
                    state.copy(reminders = reminders)
                }
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = if (exception.message.isNullOrBlank()) {
                            UiText.StringResource(R.string.message_unknown_error)
                        } else {
                            UiText.DynamicString(exception.message!!)
                        },
                        formState = _formState.value
                    )
                }

                _assistantState.update {
                    it.copy(
                        isLoading = false,
                        error = exception.message ?: "No fue posible cargar los recordatorios."
                    )
                }
            }
        }
    }

    fun saveReminderDraft(draft: ReminderDraft) {
        viewModelScope.launch {
            val validationMessage = validateDraftForSaving(draft, allowRecurrence = true)
            if (validationMessage != null) {
                _events.emit(ReminderUiEvent.ShowMessage(validationMessage))
                return@launch
            }

            runCatching {
                val savedReminder = saveReminderDraftUseCase(draft)
                reminderScheduler.syncReminderSchedule(savedReminder)
                loadReminders()
            }.onSuccess {
                clearFormState()
                _events.emit(
                    ReminderUiEvent.ShowMessage(
                        if (draft.reminderId == 0) {
                            "Recordatorio guardado correctamente."
                        } else {
                            "Recordatorio actualizado correctamente."
                        }
                    )
                )
            }.onFailure { exception ->
                _events.emit(
                    ReminderUiEvent.ShowMessage(
                        exception.message ?: "No fue posible guardar el recordatorio."
                    )
                )
            }
        }
    }

    fun fillFormState(reminder: Reminder) {
        val formState = ReminderFormState(
            reminderId = reminder.id,
            title = reminder.title,
            detail = reminder.detail,
            date = ReminderDisplayFormatter.formatScheduledDate(reminder),
            time = ReminderDisplayFormatter.formatScheduledTime(reminder),
            isUrgent = reminder.isUrgent,
            source = reminder.source,
            recurrence = reminder.recurrence
        )
        _formState.value = formState
        _uiState.update { it.copy(formState = formState) }
    }

    fun clearFormState() {
        _formState.value = ReminderFormState()
        _uiState.update { it.copy(formState = _formState.value) }
    }

    fun startManualReminderForm(defaultSource: ReminderSource = ReminderSource.MANUAL) {
        val formState = ReminderFormState(source = defaultSource)
        _formState.value = formState
        _uiState.update { it.copy(formState = formState) }
    }

    fun applyDraftToForm(
        draft: ReminderDraft,
        source: ReminderSource = draft.source
    ) {
        val currentFormState = _formState.value
        val formState = currentFormState.copy(
            title = draft.title ?: currentFormState.title,
            detail = draft.text ?: currentFormState.detail,
            date = draft.date ?: currentFormState.date,
            time = draft.time ?: currentFormState.time,
            isUrgent = currentFormState.isUrgent || draft.isUrgent,
            source = source
        )
        _formState.value = formState
        _uiState.update { it.copy(formState = formState) }
    }

    fun loadReminderForEditing(
        reminderId: Int,
        defaultSource: ReminderSource = ReminderSource.MANUAL
    ) {
        viewModelScope.launch {
            val reminder = getReminderByIdUseCase(reminderId)
            val formState = reminder?.toFormState() ?: ReminderFormState(source = defaultSource)
            _formState.value = formState
            _uiState.update { it.copy(formState = formState) }
        }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch {
            try {
                deleteReminderUseCase(reminder)
                reminderScheduler.cancelReminder(reminder.id)
                reminderScheduler.scheduleNextDaySummary()
                loadReminders()
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        error = if (exception.message.isNullOrBlank()) {
                            UiText.StringResource(R.string.message_delete_reminder_failed)
                        } else {
                            UiText.DynamicString(exception.message!!)
                        }
                    )
                }
            }
        }
    }

    fun updateReminder(reminder: Reminder) {
        viewModelScope.launch {
            try {
                val resolvedReminder = reminder.copy(
                    scheduleState = scheduleStateResolver.resolveOnSave(reminder)
                )

                updateReminderUseCase(resolvedReminder)
                reminderScheduler.syncReminderSchedule(resolvedReminder)
                loadReminders()
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        error = if (exception.message.isNullOrBlank()) {
                            UiText.StringResource(R.string.message_update_reminder_failed)
                        } else {
                            UiText.DynamicString(exception.message!!)
                        }
                    )
                }
            }
        }
    }

    private suspend fun saveAssistantDraft(draft: ReminderDraft) {
        val validationMessage = validateDraftForSaving(draft, allowRecurrence = false)

        if (validationMessage != null) {
            _assistantState.update {
                it.copy(
                    isLoading = false,
                    assistantReply = validationMessage,
                    pendingDraft = draft,
                    error = null,
                    isConversationActive = true
                )
            }
            _events.emit(ReminderUiEvent.SpeakAssistantReply(validationMessage))
            return
        }

        val savedReminder = saveReminderDraftUseCase(
            draft.copy(
                source = ReminderSource.VOICE,
                recurrence = null
            )
        )
        reminderScheduler.syncReminderSchedule(savedReminder)
        loadReminders()

        hasSavedInCurrentSession = true
        pendingDraft = null
        pendingAssistantAmbiguousTime = null

        val successReply = "Perfecto, tu recordatorio fue guardado con exito."

        _assistantState.update {
            it.copy(
                isLoading = false,
                pendingDraft = null,
                assistantReply = successReply,
                error = null,
                isConversationActive = false
            )
        }

        _events.emit(ReminderUiEvent.ShowMessage("Recordatorio guardado correctamente."))
        _events.emit(ReminderUiEvent.SpeakAssistantReply(successReply))
        _events.emit(ReminderUiEvent.StopAssistantConversation)
    }

    private fun Reminder.toFormState(): ReminderFormState {
        return ReminderFormState(
            reminderId = id,
            title = title,
            detail = detail,
            date = ReminderDisplayFormatter.formatScheduledDate(this),
            time = ReminderDisplayFormatter.formatScheduledTime(this),
            isUrgent = isUrgent,
            source = source,
            recurrence = recurrence
        )
    }

    private fun buildHomeTimelineItems(reminders: List<Reminder>): List<HomeReminderListItem> {
        val today = LocalDate.now(zoneId)
        val tomorrow = today.plusDays(1)

        return listOf(
            today to "Hoy",
            tomorrow to "Mañana"
        ).flatMap { (date, label) ->
            val dayReminders = reminders.mapNotNull { reminder ->
                val occurrenceAtEpochMillis =
                    ReminderOccurrenceCalculatorCore.resolveOccurrenceAtEpochMillis(
                        reminder = reminder,
                        year = date.year,
                        monthNumber = date.monthValue,
                        dayOfMonth = date.dayOfMonth,
                        timeZoneId = zoneId.id
                    ) ?: return@mapNotNull null

                HomeReminderListItem.ReminderRow(
                    reminder = reminder,
                    occurrenceAtEpochMillis = occurrenceAtEpochMillis
                )
            }.sortedWith(
                compareByDescending<HomeReminderListItem.ReminderRow> { it.reminder.isUrgent }
                    .thenBy { it.occurrenceAtEpochMillis }
            )

            if (dayReminders.isEmpty()) {
                emptyList()
            } else {
                listOf(
                    HomeReminderListItem.DayHeader(
                        title = label,
                        subtitle = DateTimeFormatterCore.formatDateFromEpoch(
                            date.atStartOfDay(zoneId).toInstant().toEpochMilli()
                        )
                    )
                ) + dayReminders
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
                    lastAssistantMessage = "Entiendo. Que recordatorio deseas programar?"
                )
            }

            reminderHour == null || reminderMinute == null -> {
                _voiceState.value = state.copy(
                    step = VoiceReminderStep.WAITING_FOR_TIME,
                    isVoiceFlowActive = true,
                    lastAssistantMessage = "Perfecto. A que hora deseas este recordatorio?"
                )
            }

            reminderDay == null || reminderMonth == null || reminderYear == null -> {
                _voiceState.value = state.copy(
                    step = VoiceReminderStep.WAITING_FOR_DAY,
                    isVoiceFlowActive = true,
                    lastAssistantMessage = "Muy bien. Para que dia deseas programarlo?"
                )
            }

            else -> {
                _voiceState.value = state.copy(
                    step = VoiceReminderStep.WAITING_FOR_CONFIRMATION,
                    isVoiceFlowActive = true,
                    lastAssistantMessage = buildVoiceConfirmationMessage(state)
                )
            }
        }
    }

    private fun mergeVoiceStateWithInput(
        currentState: VoiceReminderState,
        input: String
    ): VoiceReminderState {
        val normalizedInput = normalizeText(input)
        val parsedInput = parseVoiceInput(input)
        val shouldKeepCurrentText = currentState.reminderText.isNotBlank() &&
                isLikelyOnlyTemporalMessage(normalizedInput)

        return currentState.copy(
            reminderText = when {
                shouldKeepCurrentText -> currentState.reminderText
                !parsedInput.reminderText.isNullOrBlank() -> parsedInput.reminderText
                else -> currentState.reminderText
            },
            reminderHour = parsedInput.resolvedHour ?: currentState.reminderHour,
            reminderMinute = parsedInput.resolvedMinute ?: currentState.reminderMinute,
            reminderDay = parsedInput.resolvedDay ?: currentState.reminderDay,
            reminderMonth = parsedInput.resolvedMonth ?: currentState.reminderMonth,
            reminderYear = parsedInput.resolvedYear ?: currentState.reminderYear,
            isUrgent = currentState.isUrgent || parsedInput.isUrgent,
            isVoiceFlowActive = true
        )
    }

    private fun parseVoiceInput(input: String): ParsedVoiceInput {
        val normalizedInput = normalizeText(input)
        val parsedDate = parseNaturalDate(normalizedInput)
        val parsedTime = parseTime(normalizedInput)
        val parsedReminderText = VoiceReminderLanguageHelper.stripUrgencyPhrases(input)
            .takeIf { it.isNotBlank() }

        return ParsedVoiceInput(
            reminderText = parsedReminderText,
            resolvedDay = parsedDate?.day,
            resolvedMonth = parsedDate?.month,
            resolvedYear = parsedDate?.year,
            resolvedHour = parsedTime?.hour,
            resolvedMinute = parsedTime?.minute,
            isUrgent = containsUrgentSignal(normalizedInput)
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

        val draft = ReminderDraft(
            text = reminderDetail,
            date = DateTimeFormatterCore.formatDate(reminderDay, reminderMonth, reminderYear),
            time = DateTimeFormatterCore.formatTime(reminderHour, reminderMinute),
            isUrgent = state.isUrgent,
            source = ReminderSource.VOICE
        )

        viewModelScope.launch {
            val validationMessage = validateDraftForSaving(draft, allowRecurrence = false)
            if (validationMessage != null) {
                emitVoiceMessage(validationMessage)
                return@launch
            }

            runCatching {
                val savedReminder = saveReminderDraftUseCase(draft)
                reminderScheduler.syncReminderSchedule(savedReminder)
                loadReminders()
            }.onSuccess {
                resetVoiceState()
                _events.emit(ReminderUiEvent.ShowMessage("Recordatorio guardado correctamente."))
                _events.emit(
                    ReminderUiEvent.SpeakAssistantReply(
                        "Perfecto, tu recordatorio fue guardado con exito."
                    )
                )
            }.onFailure { exception ->
                resetVoiceState()
                _events.emit(
                    ReminderUiEvent.SpeakAssistantReply(
                        exception.message ?: "Ocurrio un error al guardar el recordatorio."
                    )
                )
            }
        }
    }

    private fun buildVoiceConfirmationMessage(state: VoiceReminderState): String {
        val date = DateTimeFormatterCore.formatDate(
            state.reminderDay ?: return "Confirma tu recordatorio.",
            state.reminderMonth ?: return "Confirma tu recordatorio.",
            state.reminderYear ?: return "Confirma tu recordatorio."
        )
        val time = DateTimeFormatterCore.formatTime(
            state.reminderHour ?: return "Confirma tu recordatorio.",
            state.reminderMinute ?: return "Confirma tu recordatorio."
        )
        val urgencyLabel = if (state.isUrgent) " urgente" else ""

        return "Confirma guardar este recordatorio$urgencyLabel para el $date a las $time."
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

    private suspend fun extractReminderTextWithMlKit(message: String): String? {
        return runCatching {
            val annotations = entityExtractor.extractDateTimeEntities(
                text = message,
                referenceTimeMillis = System.currentTimeMillis()
            )

            textCleaner.removeDetectedSpans(
                originalText = message,
                annotations = annotations
            )
        }.getOrNull()
            ?.let { VoiceReminderLanguageHelper.stripUrgencyPhrases(it) }
            ?.let { normalizeText(it) }
            ?.takeIf { it.isNotBlank() }
    }

    private fun parseNaturalDate(input: String): ResolvedDate? {
        val normalizedInput = normalizeText(input)
        val now = Calendar.getInstance()

        when {
            containsWholeWord(normalizedInput, "pasado manana") -> {
                val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 2) }
                return ResolvedDate(
                    day = calendar.get(Calendar.DAY_OF_MONTH),
                    month = calendar.get(Calendar.MONTH) + 1,
                    year = calendar.get(Calendar.YEAR)
                )
            }

            containsWholeWord(normalizedInput, "manana") -> {
                val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }
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
                if (diff == 0) diff = 7
                if (nextWeek) diff += 7

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
            reminderId = currentDraft?.reminderId ?: 0,
            title = currentDraft?.title,
            text = resolvedText,
            date = localDraft?.date ?: resolveReliableDate(message, currentDraft),
            time = localDraft?.time ?: currentDraft?.time,
            isUrgent = (localDraft?.isUrgent == true) || (currentDraft?.isUrgent == true),
            source = ReminderSource.VOICE,
            recurrence = null
        )
    }

    private fun buildQuestionForMissingData(draft: ReminderDraft?): String {
        if (draft == null) return "Que deseas recordar?"

        return when (ReminderDraftPromptIntentResolver.resolve(draft).promptIntent) {
            ReminderDraftPromptIntent.ASK_REMINDER_TEXT,
            ReminderDraftPromptIntent.CORRECT_INCOMPLETE_TEXT,
            ReminderDraftPromptIntent.CORRECT_INVALID_TEXT -> "Que deseas recordar?"

            ReminderDraftPromptIntent.ASK_REMINDER_DATE,
            ReminderDraftPromptIntent.CORRECT_INCOMPLETE_DATE,
            ReminderDraftPromptIntent.CORRECT_INVALID_DATE -> "Para que dia deseas este recordatorio?"

            ReminderDraftPromptIntent.ASK_REMINDER_TIME,
            ReminderDraftPromptIntent.CORRECT_INCOMPLETE_TIME,
            ReminderDraftPromptIntent.CORRECT_INVALID_TIME -> "A que hora deseas este recordatorio?"

            ReminderDraftPromptIntent.SHOW_CONFIRMATION,
            ReminderDraftPromptIntent.ALLOW_SAVE_OR_CONTINUE -> {
                pendingAssistantAmbiguousTime?.let { ambiguousTime ->
                    return buildAmbiguousTimeQuestion(ambiguousTime)
                }

                "Perfecto."
            }
        }
    }

    private fun mergeDraftFromRawMessage(
        message: String,
        currentDraft: ReminderDraft?,
        mlKitReminderText: String?,
        parsedDate: ResolvedDate?,
        parsedTime: ParsedTime?,
        resolvedPendingAmbiguousTime: String?,
        isUrgent: Boolean
    ): ReminderDraft? {
        val normalizedMessage = normalizeText(message)

        if (resolvedPendingAmbiguousTime != null) {
            return ReminderDraft(
                text = currentDraft?.text,
                date = currentDraft?.date,
                time = resolvedPendingAmbiguousTime,
                isUrgent = isUrgent || (currentDraft?.isUrgent == true),
                source = ReminderSource.VOICE
            ).takeIf { hasAnyDraftData(it) }
        }

        val shouldKeepCurrentText = currentDraft?.text != null &&
                isLikelyOnlyTemporalMessage(normalizedMessage)

        val parsedText = when {
            shouldKeepCurrentText -> currentDraft.text
            !mlKitReminderText.isNullOrBlank() -> mlKitReminderText
            else -> VoiceReminderLanguageHelper.stripUrgencyPhrases(message)
                .takeIf { it.isNotBlank() }
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
                DateTimeFormatterCore.formatDate(it.day, it.month, it.year)
            } ?: currentDraft?.date,
            time = parsedTime?.let {
                DateTimeFormatterCore.formatTime(it.hour, it.minute)
            } ?: currentDraft?.time,
            isUrgent = isUrgent || (currentDraft?.isUrgent == true),
            source = ReminderSource.VOICE
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
                    ParsedTime(hour = hour, minute = minute, isAmbiguous = isAmbiguousHourWithoutContext(hour))
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
                    ParsedTime(hour = hour, minute = minute, isAmbiguous = isAmbiguousHourWithoutContext(hour))
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
                ParsedTime(hour = hour, minute = 0, isAmbiguous = isAmbiguousHourWithoutContext(hour))
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
            in 1..8 -> "Ese horario es en la manana o en la tarde?"
            in 9..11 -> "Ese horario es en la manana o en la noche?"
            12 -> "Ese horario es a las 12 de la manana o de la tarde?"
            else -> "Ese horario es en la manana, tarde o noche?"
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

        return DateTimeFormatterCore.formatTime(resolvedHour, pendingTime.minute)
    }

    private fun extractDayPeriod(text: String): DayPeriod? {
        val normalizedText = normalizeText(text)

        return when {
            containsWholeWord(normalizedText, "madrugada") -> DayPeriod.DAWN
            containsWholeWord(normalizedText, "manana") || containsWholeWord(normalizedText, "am") ->
                DayPeriod.MORNING
            containsWholeWord(normalizedText, "tarde") -> DayPeriod.AFTERNOON
            containsWholeWord(normalizedText, "noche") || containsWholeWord(normalizedText, "pm") ->
                DayPeriod.NIGHT
            else -> null
        }
    }

    private fun resolveHourByConfiguredRanges(
        rawHour: Int,
        period: DayPeriod
    ): Int? {
        return when (period) {
            DayPeriod.DAWN,
            DayPeriod.MORNING -> when (rawHour) {
                in 1..11 -> rawHour
                12, 0 -> 0
                else -> null
            }
            DayPeriod.AFTERNOON -> when (rawHour) {
                in 1..8 -> rawHour + 12
                12 -> 12
                in 13..20 -> rawHour
                else -> null
            }
            DayPeriod.NIGHT -> when (rawHour) {
                in 9..11 -> rawHour + 12
                in 21..23 -> rawHour
                else -> null
            }
        }
    }

    private fun adjustHourByPeriod(text: String, rawHour: Int): Int? {
        val normalizedText = normalizeText(text)

        if (normalizedText.contains("medianoche")) return 0
        if (normalizedText.contains("mediodia")) return 12

        if (normalizedText.contains("madrugada")) {
            return when (rawHour) {
                in 1..11 -> rawHour
                12, 0 -> 0
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

    private fun containsRecurringRequest(input: String): Boolean {
        return VoiceReminderLanguageHelper.containsRecurringRequest(input)
    }

    private fun containsUrgentSignal(input: String): Boolean {
        return VoiceReminderLanguageHelper.containsUrgentSignal(input)
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
            DateTimeFormatterCore.formatDate(it.day, it.month, it.year)
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

    private fun validateDraftForSaving(
        draft: ReminderDraft,
        allowRecurrence: Boolean
    ): String? {
        return when (
            ReminderDraftValidator.validate(
                draft = draft,
                allowRecurrence = allowRecurrence
            )
        ) {
            null -> null
            ReminderDraftValidationIssue.MISSING_TEXT -> "No hay texto para guardar."
            ReminderDraftValidationIssue.MISSING_DATE,
            ReminderDraftValidationIssue.MISSING_TIME -> buildQuestionForMissingData(draft)
            ReminderDraftValidationIssue.RECURRENCE_NOT_ALLOWED ->
                "Las repeticiones debes configurarlas manualmente."
            ReminderDraftValidationIssue.INVALID_DATE_TIME ->
                "La fecha u hora indicadas no son validas."
            ReminderDraftValidationIssue.PAST_DATE_TIME ->
                "La fecha y hora indicadas ya pasaron. Indica una fecha u hora futura."
        }
    }
}
