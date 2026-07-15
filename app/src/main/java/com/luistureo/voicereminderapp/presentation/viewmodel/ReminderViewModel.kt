package com.luistureo.voicereminderapp.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.alarm.ReminderScheduler
import com.luistureo.voicereminderapp.core.calendar.unified.PerReminderCalendarSyncPolicy
import com.luistureo.voicereminderapp.core.calendar.unified.UnifiedCalendarSynchronizer
import com.luistureo.voicereminderapp.core.nlp.ReminderEntityExtractor
import com.luistureo.voicereminderapp.core.nlp.ReminderContentCleaner
import com.luistureo.voicereminderapp.core.nlp.ReminderTextCleaner
import com.luistureo.voicereminderapp.core.nlp.VoiceReminderLanguageHelper
import com.luistureo.voicereminderapp.core.nlp.VoiceReminderParser
import com.luistureo.voicereminderapp.core.reminder.ReminderOccurrenceCalculator
import com.luistureo.voicereminderapp.core.reminder.ReminderScheduleStateResolver
import com.luistureo.voicereminderapp.core.reminder.ReminderTemporalValidationPolicy
import com.luistureo.voicereminderapp.core.utils.DateTimeFormatter
import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import com.luistureo.voicereminderapp.domain.model.Reminder
import com.luistureo.voicereminderapp.domain.model.ReminderDraft
import com.luistureo.voicereminderapp.domain.model.ReminderSource
import com.luistureo.voicereminderapp.domain.usecase.DeleteReminderUseCase
import com.luistureo.voicereminderapp.domain.usecase.GetReminderByIdUseCase
import com.luistureo.voicereminderapp.domain.usecase.GetRemindersUseCase
import com.luistureo.voicereminderapp.domain.usecase.SaveReminderDraftUseCase
import com.luistureo.voicereminderapp.domain.usecase.UpdateReminderUseCase
import com.luistureo.voicereminderapp.presentation.assistant.AssistantConversationLogger
import com.luistureo.voicereminderapp.presentation.assistant.AssistantConversationPolicy
import com.luistureo.voicereminderapp.presentation.assistant.AssistantPhrases
import com.luistureo.voicereminderapp.presentation.common.UiText
import com.luistureo.voicereminderapp.presentation.state.AssistantUiState
import com.luistureo.voicereminderapp.presentation.state.ReminderFormState
import com.luistureo.voicereminderapp.presentation.state.ReminderFormDraftMergePolicy
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
import java.time.ZoneId
import java.util.Calendar

class ReminderViewModel(
    context: Context,
    private val saveReminderDraftUseCase: SaveReminderDraftUseCase,
    private val getRemindersUseCase: GetRemindersUseCase,
    private val getReminderByIdUseCase: GetReminderByIdUseCase,
    private val deleteReminderUseCase: DeleteReminderUseCase,
    private val updateReminderUseCase: UpdateReminderUseCase,
    private val unifiedCalendarSynchronizer: UnifiedCalendarSynchronizer
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
    private val occurrenceCalculator = ReminderOccurrenceCalculator()
    private val scheduleStateResolver = ReminderScheduleStateResolver(occurrenceCalculator)
    private val reminderScheduler = ReminderScheduler(context.applicationContext, occurrenceCalculator)
    private val zoneId = ZoneId.systemDefault()
    private val singleReminderOnlyReply =
        "Por voz dejo recordatorios simples. Para repetirlo, usa el formulario."

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
    private var pendingVoiceAmbiguousTime: PendingAmbiguousTime? = null
    private var assistantSyncTargetProviders: Set<CalendarProvider> = emptySet()

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
        pendingVoiceAmbiguousTime = null

        _assistantState.update {
            it.copy(
                recognizedText = "",
                assistantReply = AssistantPhrases.START_LISTENING,
                pendingDraft = null,
                error = null,
                isLoading = false,
                isConversationActive = true
            )
        }

        viewModelScope.launch {
            _events.emit(
                ReminderUiEvent.SpeakAssistantReply(
                    AssistantPhrases.START_LISTENING
                )
            )
        }
    }

    fun setAssistantSyncTargetProviders(providers: Set<CalendarProvider>) {
        assistantSyncTargetProviders = PerReminderCalendarSyncPolicy.sanitizeTargets(providers)
    }

    fun processAssistantMessage(message: String) {
        if (message.isBlank() || hasSavedInCurrentSession) return

        val normalizedMessage = normalizeText(message)

        if (isGeneralCancelCommand(normalizedMessage)) {
            viewModelScope.launch {
                val reply = "Sin problema, seguimos cuando quieras."
                clearAssistantConversation()
                _assistantState.update {
                    it.copy(
                        assistantReply = reply,
                        recognizedText = message,
                        isLoading = false,
                        error = null,
                        isConversationActive = false
                    )
                }
                _events.emit(
                    ReminderUiEvent.SpeakAssistantReply(reply)
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
            val parsedMessage = VoiceReminderParser.parse(message)

            if (parsedMessage.invalidTimeMessage != null) {
                _assistantState.update {
                    it.copy(
                        isLoading = false,
                        recognizedText = message,
                        assistantReply = parsedMessage.invalidTimeMessage,
                        pendingDraft = pendingDraft,
                        error = null,
                        isConversationActive = true
                    )
                }
                _events.emit(ReminderUiEvent.SpeakAssistantReply(parsedMessage.invalidTimeMessage))
                return@launch
            }

            _assistantState.update {
                it.copy(
                    isLoading = true,
                    recognizedText = message,
                    error = null,
                    isConversationActive = true
                )
            }

            val parsedDate = parsedMessage.date?.let {
                ResolvedDate(day = it.dayOfMonth, month = it.monthValue, year = it.year)
            }
            val parsedTime = parsedMessage.time?.let {
                ParsedTime(hour = it.hour, minute = it.minute, isAmbiguous = it.isAmbiguous)
            }
            val isUrgent = containsUrgentSignal(normalizedMessage)
            AssistantConversationLogger.logParsedInput(
                parsedDate = parsedDate?.let {
                    DateTimeFormatter.formatDate(it.day, it.month, it.year)
                },
                parsedTime = parsedTime?.let {
                    DateTimeFormatter.formatTime(it.hour, it.minute)
                },
                isUrgent = isUrgent
            )
            val mlKitCleanedText = extractReminderTextWithMlKit(message)
                ?: parsedMessage.reminderText
            val resolvedPendingAmbiguousTime =
                resolvePendingAssistantAmbiguousTime(normalizedMessage)

            val localDraft = mergeDraftFromRawMessage(
                message = message,
                currentDraft = pendingDraft,
                mlKitReminderText = mlKitCleanedText,
                parsedDate = parsedDate,
                parsedTime = parsedTime,
                resolvedPendingAmbiguousTime = resolvedPendingAmbiguousTime,
                isUrgent = isUrgent
            )

            val mergedDraft = mergeDraftSources(
                message = message,
                localDraft = localDraft,
                currentDraft = pendingDraft
            )

            pendingDraft = mergedDraft.takeIf { hasAnyDraftData(it) }

            val draftToSave = pendingDraft
            val missingSlot = AssistantConversationPolicy.missingSlot(
                draft = draftToSave,
                hasPendingAmbiguousTime = pendingAssistantAmbiguousTime != null
            )
            AssistantConversationLogger.logSlotState(draftToSave, missingSlot)

            if (
                !hasSavedInCurrentSession &&
                draftToSave != null &&
                AssistantConversationPolicy.isReadyToSave(
                    draft = draftToSave,
                    hasPendingAmbiguousTime = pendingAssistantAmbiguousTime != null
                )
            ) {
                AssistantConversationLogger.log("Ready to save assistant draft")
                saveAssistantDraft(draftToSave)
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
        pendingVoiceAmbiguousTime = null
        val calendar = Calendar.getInstance()
        _voiceState.value = VoiceReminderState(
            step = VoiceReminderStep.WAITING_FOR_REMINDER_TEXT,
            reminderMonth = calendar.get(Calendar.MONTH) + 1,
            reminderYear = calendar.get(Calendar.YEAR),
            isVoiceFlowActive = true,
            lastAssistantMessage = "Perfecto, te escucho."
        )
    }

    fun processVoiceInput(input: String) {
        val cleanedInput = input.trim()

        if (cleanedInput.isBlank()) {
            emitVoiceMessage("No te escuche bien. Repitelo por favor.")
            return
        }

        val normalizedInput = normalizeText(cleanedInput)

        if (containsRecurringRequest(normalizedInput)) {
            emitVoiceMessage(singleReminderOnlyReply)
            return
        }

        VoiceReminderParser.parse(cleanedInput).invalidTimeMessage?.let { invalidTimeMessage ->
            emitVoiceMessage(invalidTimeMessage)
            return
        }

        if (isGeneralCancelCommand(normalizedInput)) {
            viewModelScope.launch {
                resetVoiceState()
                _events.emit(
                    ReminderUiEvent.SpeakAssistantReply(
                        "Sin problema, seguimos cuando quieras."
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

        pendingVoiceAmbiguousTime?.let { pendingTime ->
            val period = extractDayPeriod(normalizedInput)
            if (period != null) {
                val resolvedHour = resolveHourByConfiguredRanges(
                    rawHour = pendingTime.hour,
                    period = period
                )

                if (resolvedHour != null) {
                    pendingVoiceAmbiguousTime = null
                    guideVoiceConversation(
                        currentState.copy(
                            reminderHour = resolvedHour,
                            reminderMinute = pendingTime.minute,
                            isVoiceFlowActive = true
                        )
                    )
                    return
                }
            }
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
                                "Listo, no lo guardo."
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
                reminderScheduler.syncReminderSchedules(reminders)

                _uiState.update {
                    it.copy(
                        reminders = reminders,
                        isLoading = false,
                        error = null,
                        formState = _formState.value
                    )
                }

                _assistantState.update { state ->
                    state.copy(reminders = reminders)
                }
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = UiText.StringResource(R.string.message_unknown_error),
                        formState = _formState.value
                    )
                }

                _assistantState.update {
                    it.copy(
                        isLoading = false,
                        error = "No fue posible cargar los recordatorios."
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
                val syncedReminder = unifiedCalendarSynchronizer.syncSavedReminder(savedReminder)
                reminderScheduler.syncReminderSchedule(syncedReminder)
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
                        exception.message
                            ?.takeIf { it == ReminderTemporalValidationPolicy.PAST_SCHEDULE_MESSAGE }
                            ?: "No fue posible guardar el recordatorio."
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
            date = reminder.date,
            time = reminder.time,
            isUrgent = reminder.isUrgent,
            source = reminder.source,
            recurrence = reminder.recurrence,
            syncTargetProviders = PerReminderCalendarSyncPolicy.selectedTargets(reminder)
        )
        _formState.value = formState
        _uiState.update { it.copy(formState = formState) }
    }

    fun clearFormState() {
        _formState.value = ReminderFormState()
        _uiState.update { it.copy(formState = _formState.value) }
    }

    fun startManualReminderForm(
        defaultSource: ReminderSource = ReminderSource.MANUAL,
        prefilledDate: String = ""
    ) {
        val formState = ReminderFormState(
            source = defaultSource,
            date = prefilledDate
        )
        _formState.value = formState
        _uiState.update { it.copy(formState = formState) }
    }

    fun applyDraftToForm(
        draft: ReminderDraft,
        source: ReminderSource = draft.source
    ) {
        val formState = ReminderFormDraftMergePolicy.merge(
            currentFormState = _formState.value,
            draft = draft,
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

    fun deleteReminder(
        reminder: Reminder,
        deleteExternalCalendars: Boolean = true
    ) {
        viewModelScope.launch {
            try {
                val deletionResult = unifiedCalendarSynchronizer.deleteReminderEvent(
                    reminder = reminder,
                    deleteExternalCalendars = deleteExternalCalendars
                )
                if (deletionResult.pendingDeleteProviders.isEmpty()) {
                    deleteReminderUseCase(deletionResult)
                }
                reminderScheduler.cancelReminder(reminder.id)
                reminderScheduler.scheduleNextDaySummary()
                loadReminders()
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        error = UiText.StringResource(R.string.message_delete_reminder_failed)
                    )
                }
            }
        }
    }

    fun updateReminder(reminder: Reminder) {
        viewModelScope.launch {
            try {
                val resolvedBaseReminder = reminder.copy(
                    scheduleState = scheduleStateResolver.resolveOnSave(reminder)
                )
                val resolvedReminder = PerReminderCalendarSyncPolicy.applyTargets(
                    reminder = resolvedBaseReminder,
                    targetProviders = PerReminderCalendarSyncPolicy.selectedTargets(reminder),
                    markExistingForUpdate = true
                )

                updateReminderUseCase(resolvedReminder)
                val syncedReminder = unifiedCalendarSynchronizer.syncSavedReminder(resolvedReminder)
                reminderScheduler.syncReminderSchedule(syncedReminder)
                loadReminders()
            } catch (exception: Exception) {
                val message = exception.message
                    ?.takeIf { it == ReminderTemporalValidationPolicy.PAST_SCHEDULE_MESSAGE }
                if (message != null) {
                    _events.emit(ReminderUiEvent.ShowMessage(message))
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        error = UiText.StringResource(R.string.message_update_reminder_failed)
                    )
                }
            }
        }
    }

    private suspend fun saveAssistantDraft(draft: ReminderDraft) {
        AssistantConversationLogger.log("Assistant save flow started")
        val validationMessage = validateDraftForSaving(draft, allowRecurrence = false)

        if (validationMessage != null) {
            AssistantConversationLogger.log("Assistant save blocked: $validationMessage")
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

        runCatching {
            val savedReminder = saveReminderDraftUseCase(
                draft.copy(
                    source = ReminderSource.VOICE,
                    recurrence = null,
                    syncTargetProviders = assistantSyncTargetProviders
                )
            )
            val syncedReminder = unifiedCalendarSynchronizer.syncSavedReminder(savedReminder)
            reminderScheduler.syncReminderSchedule(syncedReminder)
            loadReminders()
        }.onSuccess {
            AssistantConversationLogger.log("Assistant save flow completed")
            hasSavedInCurrentSession = true
            pendingDraft = null
            pendingAssistantAmbiguousTime = null
            assistantSyncTargetProviders = emptySet()

            val successReply = AssistantPhrases.SAVE_SUCCESS

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
        }.onFailure {
            val errorMessage = "No pude guardar el recordatorio."
            AssistantConversationLogger.log("Assistant save flow failed: $errorMessage")
            _assistantState.update {
                it.copy(
                    isLoading = false,
                    assistantReply = errorMessage,
                    pendingDraft = draft,
                    error = null,
                    isConversationActive = true
                )
            }
            _events.emit(ReminderUiEvent.SpeakAssistantReply(errorMessage))
        }
    }

    private fun Reminder.toFormState(): ReminderFormState {
        return ReminderFormState(
            reminderId = id,
            title = title,
            detail = detail,
            date = date,
            time = time,
            isUrgent = isUrgent,
            source = source,
            recurrence = recurrence,
            syncTargetProviders = PerReminderCalendarSyncPolicy.selectedTargets(this)
        )
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
                    lastAssistantMessage = AssistantPhrases.LISTENING_SHORT
                )
            }

            reminderDay == null || reminderMonth == null || reminderYear == null -> {
                _voiceState.value = state.copy(
                    step = VoiceReminderStep.WAITING_FOR_DAY,
                    isVoiceFlowActive = true,
                    lastAssistantMessage = AssistantPhrases.ASK_DATE
                )
            }

            reminderHour == null || reminderMinute == null -> {
                _voiceState.value = state.copy(
                    step = VoiceReminderStep.WAITING_FOR_TIME,
                    isVoiceFlowActive = true,
                    lastAssistantMessage = AssistantPhrases.ASK_TIME
                )
            }

            pendingVoiceAmbiguousTime != null -> {
                val ambiguousTime = pendingVoiceAmbiguousTime ?: return
                _voiceState.value = state.copy(
                    step = VoiceReminderStep.WAITING_FOR_TIME,
                    isVoiceFlowActive = true,
                    lastAssistantMessage = buildAmbiguousTimeQuestion(ambiguousTime)
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
        val parsedMessage = VoiceReminderParser.parse(input)
        val parsedDate = parsedMessage.date?.let {
            ResolvedDate(day = it.dayOfMonth, month = it.monthValue, year = it.year)
        }
        val parsedTime = parsedMessage.time?.let {
            ParsedTime(hour = it.hour, minute = it.minute, isAmbiguous = it.isAmbiguous)
        }
        val parsedReminderText = parsedMessage.reminderText

        if (parsedTime?.isAmbiguous == true) {
            pendingVoiceAmbiguousTime = PendingAmbiguousTime(
                hour = parsedTime.hour,
                minute = parsedTime.minute
            )
        } else if (parsedTime != null) {
            pendingVoiceAmbiguousTime = null
        }

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
        val reminderDetail = ReminderContentCleaner.cleanDetail(state.reminderText)
            ?: state.reminderText.trim()

        val draft = ReminderDraft(
            title = ReminderContentCleaner.buildTitle(reminderDetail),
            text = reminderDetail,
            date = DateTimeFormatter.formatDate(reminderDay, reminderMonth, reminderYear),
            time = DateTimeFormatter.formatTime(reminderHour, reminderMinute),
            isUrgent = state.isUrgent,
            source = ReminderSource.VOICE,
            syncTargetProviders = assistantSyncTargetProviders
        )

        viewModelScope.launch {
            val validationMessage = validateDraftForSaving(draft, allowRecurrence = false)
            if (validationMessage != null) {
                emitVoiceMessage(validationMessage)
                return@launch
            }

            runCatching {
                val savedReminder = saveReminderDraftUseCase(draft)
                val syncedReminder = unifiedCalendarSynchronizer.syncSavedReminder(savedReminder)
                reminderScheduler.syncReminderSchedule(syncedReminder)
                loadReminders()
            }.onSuccess {
                resetVoiceState()
                _events.emit(ReminderUiEvent.ShowMessage("Recordatorio guardado correctamente."))
                _events.emit(
                    ReminderUiEvent.SpeakAssistantReply(
                        AssistantPhrases.SAVE_SUCCESS
                    )
                )
            }.onFailure {
                resetVoiceState()
                _events.emit(
                    ReminderUiEvent.SpeakAssistantReply(
                        AssistantPhrases.SAVE_ERROR
                    )
                )
            }
        }
    }

    private fun buildVoiceConfirmationMessage(state: VoiceReminderState): String {
        val date = DateTimeFormatter.formatDate(
            state.reminderDay ?: return "Confirma tu recordatorio.",
            state.reminderMonth ?: return "Confirma tu recordatorio.",
            state.reminderYear ?: return "Confirma tu recordatorio."
        )
        val time = DateTimeFormatter.formatTime(
            state.reminderHour ?: return "Confirma tu recordatorio.",
            state.reminderMinute ?: return "Confirma tu recordatorio."
        )
        return AssistantPhrases.confirmation(date, time, state.isUrgent)
    }

    private fun emitVoiceMessage(message: String) {
        _voiceState.value = _voiceState.value.copy(
            isVoiceFlowActive = true,
            lastAssistantMessage = message
        )
    }

    private fun resetVoiceState() {
        pendingVoiceAmbiguousTime = null
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
        return VoiceReminderParser.parse(input).date?.let {
            ResolvedDate(day = it.dayOfMonth, month = it.monthValue, year = it.year)
        }
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
        }?.let(ReminderContentCleaner::cleanDetail)

        return ReminderDraft(
            reminderId = currentDraft?.reminderId ?: 0,
            title = currentDraft?.title ?: ReminderContentCleaner.buildTitle(resolvedText),
            text = resolvedText,
            date = localDraft?.date ?: resolveReliableDate(message, currentDraft),
            time = localDraft?.time ?: currentDraft?.time,
            isUrgent = (localDraft?.isUrgent == true) || (currentDraft?.isUrgent == true),
            source = ReminderSource.VOICE,
            recurrence = null,
            syncTargetProviders = currentDraft?.syncTargetProviders.orEmpty()
        )
    }

    private fun buildQuestionForMissingData(draft: ReminderDraft?): String {
        val missingSlot = AssistantConversationPolicy.missingSlot(
            draft = draft,
            hasPendingAmbiguousTime = pendingAssistantAmbiguousTime != null
        )
        AssistantConversationLogger.logSlotState(draft, missingSlot)

        return AssistantConversationPolicy.questionFor(
            missingSlot = missingSlot,
            ambiguousHour = pendingAssistantAmbiguousTime?.hour
        )
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
                title = currentDraft?.title ?: ReminderContentCleaner.buildTitle(currentDraft?.text),
                text = currentDraft?.text,
                date = currentDraft?.date,
                time = resolvedPendingAmbiguousTime,
                isUrgent = isUrgent || (currentDraft?.isUrgent == true),
                source = ReminderSource.VOICE,
                syncTargetProviders = currentDraft?.syncTargetProviders.orEmpty()
            ).takeIf { hasAnyDraftData(it) }
        }

        val shouldKeepCurrentText = currentDraft?.text != null &&
                isLikelyOnlyTemporalMessage(normalizedMessage)

        val parsedText = when {
            shouldKeepCurrentText -> currentDraft.text
            !mlKitReminderText.isNullOrBlank() -> mlKitReminderText
            else -> VoiceReminderLanguageHelper.stripUrgencyPhrases(message)
                .takeIf { it.isNotBlank() }
        }?.let(ReminderContentCleaner::cleanDetail)

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
            title = currentDraft?.title ?: ReminderContentCleaner.buildTitle(parsedText),
            text = parsedText?.takeIf { it.isNotBlank() } ?: currentDraft?.text,
            date = parsedDate?.let {
                DateTimeFormatter.formatDate(it.day, it.month, it.year)
            } ?: currentDraft?.date,
            time = parsedTime?.let {
                DateTimeFormatter.formatTime(it.hour, it.minute)
            } ?: currentDraft?.time,
            isUrgent = isUrgent || (currentDraft?.isUrgent == true),
            source = ReminderSource.VOICE,
            syncTargetProviders = currentDraft?.syncTargetProviders.orEmpty()
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
        return VoiceReminderParser.parse(input).time?.let {
            ParsedTime(hour = it.hour, minute = it.minute, isAmbiguous = it.isAmbiguous)
        }
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
        return AssistantPhrases.ambiguousTimeQuestion(ambiguousTime.hour)
    }

    private fun resolvePendingAssistantAmbiguousTime(message: String): String? {
        val pendingTime = pendingAssistantAmbiguousTime ?: return null
        val period = extractDayPeriod(message) ?: return null
        val resolvedHour = resolveHourByConfiguredRanges(
            rawHour = pendingTime.hour,
            period = period
        ) ?: return null

        pendingAssistantAmbiguousTime = null

        return DateTimeFormatter.formatTime(resolvedHour, pendingTime.minute)
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

    private fun validateDraftForSaving(
        draft: ReminderDraft,
        allowRecurrence: Boolean
    ): String? {
        if (draft.text.isNullOrBlank()) {
            return "No hay texto para guardar."
        }

        if (draft.date.isNullOrBlank() || draft.time.isNullOrBlank()) {
            return buildQuestionForMissingData(draft)
        }

        if (!allowRecurrence && draft.recurrence != null) {
            return "Las repeticiones debes configurarlas manualmente."
        }

        val triggerTimeMillis = draft.buildScheduledAtEpochMillis()
            ?: return "La fecha u hora indicadas no son validas."

        ReminderTemporalValidationPolicy.validateNewSchedule(triggerTimeMillis)
            ?.let { return it }

        return null
    }
}
