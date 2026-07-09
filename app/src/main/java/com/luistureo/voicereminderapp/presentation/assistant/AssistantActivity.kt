package com.luistureo.voicereminderapp.presentation.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarAuthManager
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarReminderSynchronizer
import com.luistureo.voicereminderapp.core.calendar.microsoft.MicrosoftCalendarAuthController
import com.luistureo.voicereminderapp.core.calendar.microsoft.MicrosoftCalendarAuthProvider
import com.luistureo.voicereminderapp.core.calendar.unified.UnifiedCalendarSynchronizer
import com.luistureo.voicereminderapp.core.speech.SpeechRecognizerManager
import com.luistureo.voicereminderapp.core.speech.VoiceAssistantSpeaker
import com.luistureo.voicereminderapp.data.local.database.ReminderDatabase
import com.luistureo.voicereminderapp.data.repository.ReminderRepositoryImpl
import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import com.luistureo.voicereminderapp.domain.usecase.DeleteReminderUseCase
import com.luistureo.voicereminderapp.domain.usecase.GetReminderByIdUseCase
import com.luistureo.voicereminderapp.domain.usecase.GetRemindersUseCase
import com.luistureo.voicereminderapp.domain.usecase.SaveReminderDraftUseCase
import com.luistureo.voicereminderapp.domain.usecase.UpdateReminderUseCase
import com.luistureo.voicereminderapp.presentation.state.ReminderUiEvent
import com.luistureo.voicereminderapp.presentation.viewmodel.ReminderViewModel
import com.luistureo.voicereminderapp.presentation.viewmodel.ReminderViewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class AssistantActivity : ComponentActivity() {

    private lateinit var backButton: ImageButton
    private lateinit var speakButton: MaterialButton
    private lateinit var recognizedTextView: TextView
    private lateinit var assistantReplyView: TextView
    private lateinit var assistantView: AssistantView
    private lateinit var assistantSceneContainer: View
    private lateinit var assistantDialogueBubble: AssistantDialogueBubbleView
    private lateinit var syncOptionsContainer: View
    private lateinit var googleSyncCheckBox: MaterialCheckBox
    private lateinit var microsoftSyncCheckBox: MaterialCheckBox

    private lateinit var assistantAnimator: AssistantAnimator
    private lateinit var reminderViewModel: ReminderViewModel
    private lateinit var speechManager: SpeechRecognizerManager
    private lateinit var voiceAssistantSpeaker: VoiceAssistantSpeaker
    private lateinit var googleCalendarAuthManager: GoogleCalendarAuthManager
    private lateinit var microsoftCalendarAuthController: MicrosoftCalendarAuthController

    private var currentAssistantVisualState: AssistantVisualState = AssistantVisualState.IDLE
    private var hasPendingSuccessState: Boolean = false
    private var assistantResetJob: Job? = null
    private val assistantSpeechQueue = ArrayDeque<String>()
    private var isAssistantSpeechQueueActive: Boolean = false
    private var availableSyncProviders: Set<CalendarProvider> = emptySet()

    private val voiceAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startAssistantConversation()
            } else {
                renderAssistantState(AssistantVisualState.IDLE)
                Toast.makeText(
                    this,
                    getString(R.string.mic_permission_denied),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assistant)

        initViews()
        setupAssistant()
        setupViewModel()
        setupSpeech()
        setupListeners()
        refreshCalendarSyncOptions()
        observeAssistantState()
        observeEvents()
    }

    override fun onStart() {
        super.onStart()
        assistantAnimator.resume()
    }

    override fun onStop() {
        assistantAnimator.stop()
        speechManager.cancelRecognition()
        super.onStop()
    }

    override fun onDestroy() {
        assistantResetJob?.cancel()
        assistantSpeechQueue.clear()
        isAssistantSpeechQueueActive = false
        assistantAnimator.stop()
        assistantDialogueBubble.stopAllEffects()
        speechManager.destroy()
        voiceAssistantSpeaker.setPlaybackListener(null)
        voiceAssistantSpeaker.setFallbackListener(null)
        voiceAssistantSpeaker.shutdown()
        super.onDestroy()
    }

    private fun initViews() {
        backButton = findViewById(R.id.btnBackAssistant)
        speakButton = findViewById(R.id.btnSpeakAssistant)
        recognizedTextView = findViewById(R.id.tvAssistantRecognizedText)
        assistantReplyView = findViewById(R.id.tvAssistantReply)
        assistantView = findViewById(R.id.assistantView)
        assistantSceneContainer = findViewById(R.id.assistantSceneContainer)
        assistantDialogueBubble = findViewById(R.id.assistantDialogueBubble)
        syncOptionsContainer = findViewById(R.id.containerAssistantCalendarSyncOptions)
        googleSyncCheckBox = findViewById(R.id.checkAssistantSyncGoogle)
        microsoftSyncCheckBox = findViewById(R.id.checkAssistantSyncMicrosoft)
    }

    private fun setupAssistant() {
        assistantAnimator = AssistantAnimator(assistantView)
        applyAdaptiveSceneHeight()
        renderAssistantState(AssistantVisualState.IDLE)
    }

    private fun setupViewModel() {
        val database = ReminderDatabase.getDatabase(this)
        val repository = ReminderRepositoryImpl(database.reminderDao())
        googleCalendarAuthManager = GoogleCalendarAuthManager(applicationContext)
        microsoftCalendarAuthController = MicrosoftCalendarAuthProvider.get(applicationContext)
        val googleCalendarSynchronizer = GoogleCalendarReminderSynchronizer(
            context = applicationContext,
            reminderRepository = repository
        )
        val unifiedCalendarSynchronizer = UnifiedCalendarSynchronizer(
            context = applicationContext,
            reminderRepository = repository,
            googleCalendarSynchronizer = googleCalendarSynchronizer
        )

        val factory = ReminderViewModelFactory(
            context = applicationContext,
            saveReminderDraftUseCase = SaveReminderDraftUseCase(repository),
            getRemindersUseCase = GetRemindersUseCase(repository),
            getReminderByIdUseCase = GetReminderByIdUseCase(repository),
            deleteReminderUseCase = DeleteReminderUseCase(repository),
            updateReminderUseCase = UpdateReminderUseCase(repository),
            unifiedCalendarSynchronizer = unifiedCalendarSynchronizer
        )

        reminderViewModel = ViewModelProvider(this, factory)[ReminderViewModel::class.java]
    }

    private fun setupSpeech() {
        speechManager = SpeechRecognizerManager(this)
        speechManager.setListener(object : SpeechRecognizerManager.Listener {
            override fun onPartialTranscription(text: String) {
                runOnUiThread {
                    renderLiveTranscription(text)
                }
            }

            override fun onFinalTranscription(text: String) {
                runOnUiThread {
                    handleRecognizedText(text)
                }
            }

            override fun onNoMatch() {
                runOnUiThread {
                    handleRecognitionFailure(showNoMatchMessage = true)
                }
            }

            override fun onRecognitionError() {
                runOnUiThread {
                    handleRecognitionFailure(showNoMatchMessage = false)
                }
            }
        })

        voiceAssistantSpeaker = VoiceAssistantSpeaker(this)
        voiceAssistantSpeaker.setPlaybackListener(object : VoiceAssistantSpeaker.PlaybackListener {
            override fun onPlaybackStarted() {
                assistantAnimator.setSpeechPlaybackActive(true)
            }

            override fun onPlaybackFinished() {
                assistantAnimator.setSpeechPlaybackActive(false)
            }
        })
        voiceAssistantSpeaker.setFallbackListener(object : VoiceAssistantSpeaker.FallbackListener {
            override fun onLocalFallback(message: String) {
                Toast.makeText(
                    this@AssistantActivity,
                    message,
                    Toast.LENGTH_LONG
                ).show()
            }
        })
    }

    private fun setupListeners() {
        backButton.setOnClickListener {
            finish()
        }

        speakButton.setOnClickListener {
            if (speechManager.isListeningOrStarting()) {
                speechManager.cancelRecognition()
                renderAssistantState(AssistantVisualState.IDLE)
            } else {
                checkAudioPermissionAndStart()
            }
        }
        googleSyncCheckBox.setOnCheckedChangeListener { _, _ ->
            reminderViewModel.setAssistantSyncTargetProviders(selectedSyncTargetProviders())
        }
        microsoftSyncCheckBox.setOnCheckedChangeListener { _, _ ->
            reminderViewModel.setAssistantSyncTargetProviders(selectedSyncTargetProviders())
        }

    }

    private fun refreshCalendarSyncOptions() {
        updateAvailableSyncProviders(
            buildSet {
                if (googleCalendarAuthManager.isConnected()) add(CalendarProvider.GOOGLE_CALENDAR)
                if (microsoftCalendarAuthController.isConnected) {
                    add(CalendarProvider.MICROSOFT_CALENDAR)
                }
            }
        )
        microsoftCalendarAuthController.refreshConnectionState { isConnected ->
            runOnUiThread {
                updateAvailableSyncProviders(
                    buildSet {
                        if (googleCalendarAuthManager.isConnected()) {
                            add(CalendarProvider.GOOGLE_CALENDAR)
                        }
                        if (isConnected) {
                            add(CalendarProvider.MICROSOFT_CALENDAR)
                        }
                    }
                )
            }
        }
    }

    private fun updateAvailableSyncProviders(providers: Set<CalendarProvider>) {
        availableSyncProviders = providers
        syncOptionsContainer.isVisible = providers.isNotEmpty()
        googleSyncCheckBox.isVisible = CalendarProvider.GOOGLE_CALENDAR in providers
        microsoftSyncCheckBox.isVisible = CalendarProvider.MICROSOFT_CALENDAR in providers
        reminderViewModel.setAssistantSyncTargetProviders(selectedSyncTargetProviders())
    }

    private fun selectedSyncTargetProviders(): Set<CalendarProvider> {
        return buildSet {
            if (
                googleSyncCheckBox.isVisible &&
                googleSyncCheckBox.isChecked &&
                CalendarProvider.GOOGLE_CALENDAR in availableSyncProviders
            ) {
                add(CalendarProvider.GOOGLE_CALENDAR)
            }
            if (
                microsoftSyncCheckBox.isVisible &&
                microsoftSyncCheckBox.isChecked &&
                CalendarProvider.MICROSOFT_CALENDAR in availableSyncProviders
            ) {
                add(CalendarProvider.MICROSOFT_CALENDAR)
            }
        }
    }

    private fun resetSyncTargetSelection() {
        googleSyncCheckBox.isChecked = false
        microsoftSyncCheckBox.isChecked = false
        reminderViewModel.setAssistantSyncTargetProviders(emptySet())
    }

    private fun observeAssistantState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                reminderViewModel.assistantState.collect { state ->
                    recognizedTextView.text = state.recognizedText.takeIf { it.isNotBlank() }
                        ?: getString(R.string.assistant_heard_placeholder)

                    assistantReplyView.text = state.assistantReply.takeIf { it.isNotBlank() }
                        ?: getString(R.string.assistant_response_placeholder)

                    if (state.isLoading) {
                        renderAssistantState(AssistantVisualState.THINKING)
                    }

                    state.error?.let { error ->
                        renderAssistantState(AssistantVisualState.IDLE)
                        Toast.makeText(this@AssistantActivity, error, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                reminderViewModel.events.collect { event ->
                    when (event) {
                        is ReminderUiEvent.ShowMessage -> {
                            Toast.makeText(
                                this@AssistantActivity,
                                event.message,
                                Toast.LENGTH_SHORT
                            ).show()

                            if (
                                event.message.contains("guardado", ignoreCase = true) ||
                                event.message.contains("actualizado", ignoreCase = true)
                            ) {
                                hasPendingSuccessState = true
                                resetSyncTargetSelection()
                            }
                        }

                        is ReminderUiEvent.SpeakAssistantReply -> {
                            if (event.message.isNotBlank()) {
                                enqueueAssistantReply(event.message)
                            }
                        }

                        ReminderUiEvent.StopAssistantConversation -> {
                            speechManager.cancelRecognition()

                            if (!hasPendingSuccessState) {
                                renderAssistantState(AssistantVisualState.IDLE)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkAudioPermissionAndStart() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startAssistantConversation()
        } else {
            voiceAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startAssistantConversation() {
        hasPendingSuccessState = false
        voiceAssistantSpeaker.resetFallbackNoticeForSession()
        recognizedTextView.text = getString(R.string.assistant_heard_placeholder)
        assistantDialogueBubble.showMessage(
            text = getString(R.string.assistant_thinking_dialogue),
            animateText = false,
            playTypingSound = false
        )
        renderAssistantState(AssistantVisualState.THINKING)
        reminderViewModel.startAssistantSession()
        reminderViewModel.setAssistantSyncTargetProviders(selectedSyncTargetProviders())
    }

    private fun startSpeechRecognition() {
        try {
            if (!speechManager.isRecognitionAvailable()) {
                throw IllegalStateException("Speech recognition is not available")
            }

            speechManager.cancelRecognition()
            renderAssistantState(AssistantVisualState.LISTENING)
            assistantDialogueBubble.showMessage(
                text = getString(R.string.assistant_listening_dialogue),
                animateText = false,
                playTypingSound = false
            )
            speechManager.startRecognition()
        } catch (exception: Exception) {
            renderAssistantState(AssistantVisualState.IDLE)

            Toast.makeText(
                this,
                getString(R.string.speech_not_available),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun handleRecognizedText(text: String) {
        val recognizedText = text.trim()

        if (recognizedText.isBlank()) {
            handleRecognitionFailure(showNoMatchMessage = true)
            return
        }

        recognizedTextView.text = recognizedText
        assistantDialogueBubble.showMessage(
            text = getString(R.string.assistant_thinking_dialogue),
            animateText = false,
            playTypingSound = false
        )
        renderAssistantState(AssistantVisualState.THINKING)
        reminderViewModel.processAssistantMessage(recognizedText)
    }

    private fun handleRecognitionFailure(showNoMatchMessage: Boolean) {
        speechManager.cancelRecognition()
        renderAssistantState(AssistantVisualState.IDLE)

        val messageResId = if (showNoMatchMessage) {
            R.string.speech_not_understood
        } else {
            R.string.speech_not_available
        }

        Toast.makeText(
            this,
            getString(messageResId),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun renderLiveTranscription(text: String) {
        val partialText = text.trim()
        if (partialText.isBlank()) return

        recognizedTextView.text = partialText
        assistantDialogueBubble.showMessage(
            text = getString(R.string.assistant_listening_dialogue),
            animateText = false,
            playTypingSound = false
        )
        renderAssistantState(AssistantVisualState.LISTENING)
    }

    private fun renderAssistantSpeech(message: String) {
        renderAssistantState(message.toVisualState())
    }

    private fun enqueueAssistantReply(message: String) {
        val chunks = message.toAssistantDialogueChunks()
        if (chunks.isEmpty()) return

        assistantSpeechQueue.addAll(chunks)
        assistantReplyView.text = chunks.joinToString(separator = " ")

        if (!isAssistantSpeechQueueActive) {
            playNextAssistantReply()
        }
    }

    private fun playNextAssistantReply() {
        val nextMessage = assistantSpeechQueue.removeFirstOrNull()

        if (nextMessage == null) {
            isAssistantSpeechQueueActive = false
            handleAssistantReplyQueueFinished()
            return
        }

        isAssistantSpeechQueueActive = true
        renderAssistantSpeech(nextMessage)
        assistantDialogueBubble.showMessage(
            text = nextMessage,
            animateText = true,
            playTypingSound = true
        )

        voiceAssistantSpeaker.speakText(nextMessage) {
            runOnUiThread {
                playNextAssistantReply()
            }
        }
    }

    private fun handleAssistantReplyQueueFinished() {
        if (hasPendingSuccessState) {
            showSuccessAndReturnToIdle()
            return
        }

        if (reminderViewModel.assistantState.value.isConversationActive) {
            startSpeechRecognition()
        } else {
            renderAssistantState(AssistantVisualState.IDLE)
        }
    }

    private fun renderAssistantState(state: AssistantVisualState) {
        assistantResetJob?.cancel()
        currentAssistantVisualState = state
        assistantAnimator.render(state)
    }

    private fun showSuccessAndReturnToIdle() {
        hasPendingSuccessState = false
        renderAssistantState(AssistantVisualState.SUCCESS)

        assistantResetJob = lifecycleScope.launch {
            delay(1200L)
            renderAssistantState(AssistantVisualState.IDLE)
        }
    }

    private fun String.toVisualState(): AssistantVisualState {
        val normalized = lowercase()

        return when {
            "hora" in normalized && "?" in this -> AssistantVisualState.ASKING_TIME
            else -> AssistantVisualState.SPEAKING
        }
    }

    private fun applyAdaptiveSceneHeight() {
        assistantSceneContainer.post {
            val availableWidth = assistantSceneContainer.width
            if (availableWidth <= 0) return@post

            val minHeight = resources.getDimensionPixelSize(R.dimen.assistant_scene_min_height)
            val maxHeight = resources.getDimensionPixelSize(R.dimen.assistant_scene_max_height)
            val targetHeight = (availableWidth * 0.88f).roundToInt()
                .coerceIn(minHeight, maxHeight)

            val layoutParams = assistantSceneContainer.layoutParams
            if (layoutParams.height != targetHeight) {
                layoutParams.height = targetHeight
                assistantSceneContainer.layoutParams = layoutParams
            }
        }
    }

    private fun String.toAssistantDialogueChunks(): List<String> {
        val normalizedMessage = trim()
            .replace(Regex("\\s+"), " ")
            .takeIf { it.isNotBlank() }
            ?: return emptyList()
        val sentenceParts = normalizedMessage
            .split(Regex("(?<=[.!?])\\s+"))
            .filter { it.isNotBlank() }
        val chunks = mutableListOf<String>()

        sentenceParts.forEach { sentence ->
            if (sentence.length <= MAX_DIALOGUE_CHUNK_LENGTH) {
                chunks += sentence
            } else {
                chunks += sentence.splitByWordLimit(MAX_DIALOGUE_CHUNK_LENGTH)
            }
        }

        return chunks.map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun String.splitByWordLimit(maxLength: Int): List<String> {
        val chunks = mutableListOf<String>()
        val currentChunk = StringBuilder()

        split(" ").forEach { word ->
            if (currentChunk.isEmpty()) {
                currentChunk.append(word)
            } else if (currentChunk.length + 1 + word.length <= maxLength) {
                currentChunk.append(' ').append(word)
            } else {
                chunks += currentChunk.toString()
                currentChunk.clear()
                currentChunk.append(word)
            }
        }

        if (currentChunk.isNotEmpty()) {
            chunks += currentChunk.toString()
        }

        return chunks
    }

    private companion object {
        const val MAX_DIALOGUE_CHUNK_LENGTH = 52
    }
}
