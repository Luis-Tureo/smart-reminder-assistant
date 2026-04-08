package com.luistureo.voicereminderapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.luistureo.voicereminderapp.core.alarm.ReminderScheduler
import com.luistureo.voicereminderapp.core.speech.SpeechRecognizerManager
import com.luistureo.voicereminderapp.core.speech.VoiceAssistantSpeaker
import com.luistureo.voicereminderapp.data.local.database.ReminderDatabase
import com.luistureo.voicereminderapp.data.repository.ReminderRepositoryImpl
import com.luistureo.voicereminderapp.domain.usecase.AddReminderUseCase
import com.luistureo.voicereminderapp.domain.usecase.DeleteReminderUseCase
import com.luistureo.voicereminderapp.domain.usecase.GetRemindersUseCase
import com.luistureo.voicereminderapp.domain.usecase.UpdateReminderUseCase
import com.luistureo.voicereminderapp.presentation.assistant.AssistantAnimator
import com.luistureo.voicereminderapp.presentation.assistant.AssistantDialogueBubbleView
import com.luistureo.voicereminderapp.presentation.assistant.AssistantFaceState
import com.luistureo.voicereminderapp.presentation.assistant.AssistantView
import com.luistureo.voicereminderapp.presentation.assistant.AssistantVisualState
import com.luistureo.voicereminderapp.presentation.state.ReminderUiEvent
import com.luistureo.voicereminderapp.presentation.ui.adapter.ReminderAdapter
import com.luistureo.voicereminderapp.presentation.ui.swipe.SwipeToDeleteCallback
import com.luistureo.voicereminderapp.presentation.viewmodel.ReminderViewModel
import com.luistureo.voicereminderapp.presentation.viewmodel.ReminderViewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var assistantSceneContent: View
    private lateinit var assistantView: AssistantView
    private lateinit var assistantDialogueBubble: AssistantDialogueBubbleView
    private lateinit var assistantTapHint: TextView
    private lateinit var remindersRecyclerView: RecyclerView

    private lateinit var assistantAnimator: AssistantAnimator
    private lateinit var reminderAdapter: ReminderAdapter
    private lateinit var reminderViewModel: ReminderViewModel
    private lateinit var reminderScheduler: ReminderScheduler
    private lateinit var speechManager: SpeechRecognizerManager
    private lateinit var voiceAssistantSpeaker: VoiceAssistantSpeaker

    private var lastVoiceAssistantMessage: String = ""
    private var isAssistantConversationMode: Boolean = false
    private var currentAssistantVisualState: AssistantVisualState = AssistantVisualState.IDLE
    private var hasPendingSuccessState: Boolean = false
    private var assistantResetJob: Job? = null
    private var lastDialogueText: String = AssistantVisualState.IDLE.label

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(
                    this,
                    getString(R.string.notification_permission_denied),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val voiceAudioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                if (hasRecoverableVoiceFlow()) {
                    startSpeechRecognition()
                } else {
                    startAssistantConversation()
                }
            } else {
                updateAssistantVisualState(AssistantVisualState.IDLE)
                Toast.makeText(
                    this,
                    getString(R.string.mic_permission_denied),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupAssistant()
        setupViewModel()
        setupCore()
        setupRecyclerView()
        setupSwipeToDelete()
        setupAssistantInteraction()
        observeState()
        observeEvents()
        requestNotificationPermissionIfNeeded()
    }

    override fun onStart() {
        super.onStart()
        assistantAnimator.resume()
    }

    override fun onStop() {
        assistantAnimator.stop()
        assistantDialogueBubble.stopAllEffects()
        assistantTapHint.animate().cancel()
        speechManager.cancelRecognition()
        super.onStop()
    }

    private fun initViews() {
        assistantSceneContent = findViewById(R.id.assistantSceneContent)
        assistantView = findViewById(R.id.assistantView)
        assistantDialogueBubble = findViewById(R.id.assistantDialogueBubble)
        assistantTapHint = findViewById(R.id.tvAssistantTapHint)
        remindersRecyclerView = findViewById(R.id.recyclerReminders)
    }

    private fun setupAssistant() {
        assistantAnimator = AssistantAnimator(assistantView)
        renderAssistantState(AssistantVisualState.IDLE)
    }

    private fun setupViewModel() {
        val database = ReminderDatabase.getDatabase(this)
        val repository = ReminderRepositoryImpl(database.reminderDao())

        val factory = ReminderViewModelFactory(
            context = applicationContext,
            addReminderUseCase = AddReminderUseCase(repository),
            getRemindersUseCase = GetRemindersUseCase(repository),
            deleteReminderUseCase = DeleteReminderUseCase(repository),
            updateReminderUseCase = UpdateReminderUseCase(repository)
        )

        reminderViewModel = ViewModelProvider(this, factory)[ReminderViewModel::class.java]
    }

    private fun setupCore() {
        reminderScheduler = ReminderScheduler(this)
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
    }

    private fun setupRecyclerView() {
        reminderAdapter = ReminderAdapter(
            reminders = emptyList(),
            onDelete = { reminder ->
                reminderViewModel.deleteReminder(reminder)
            },
            onUpdate = { reminder ->
                reminderViewModel.updateReminder(reminder)
            }
        )

        remindersRecyclerView.layoutManager = LinearLayoutManager(this)
        remindersRecyclerView.adapter = reminderAdapter
    }

    private fun setupSwipeToDelete() {
        val swipeHandler = SwipeToDeleteCallback { position ->
            val reminders = reminderViewModel.uiState.value.reminders
            if (position in reminders.indices) {
                reminderViewModel.deleteReminder(reminders[position])
            }
        }

        ItemTouchHelper(swipeHandler).attachToRecyclerView(remindersRecyclerView)
    }

    private fun setupAssistantInteraction() {
        assistantSceneContent.setOnClickListener {
            handleAssistantSceneTap()
        }
    }

    private fun startAssistantConversation() {
        hasPendingSuccessState = false
        updateTapHintVisibility(false)
        isAssistantConversationMode = true
        reminderViewModel.startAssistantSession()
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    reminderViewModel.uiState.collect { state ->
                        reminderAdapter.updateData(state.reminders)

                        state.error?.let { error ->
                            Toast.makeText(
                                this@MainActivity,
                                error.asString(this@MainActivity),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                launch {
                    reminderViewModel.assistantState.collect { state ->
                        state.error?.let { error ->
                            renderAssistantState(AssistantVisualState.IDLE)
                            Toast.makeText(
                                this@MainActivity,
                                error,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                launch {
                    reminderViewModel.voiceState.collect { state ->
                        if (isAssistantConversationMode) return@collect

                        val message = state.lastAssistantMessage

                        if (
                            state.isVoiceFlowActive &&
                            message.isNotBlank() &&
                            message != lastVoiceAssistantMessage
                        ) {
                            lastVoiceAssistantMessage = message
                            renderAssistantSpeech(message)

                            voiceAssistantSpeaker.speakText(message) {
                                runOnUiThread {
                                    if (hasPendingSuccessState) {
                                        showSuccessAndReturnToIdle()
                                        return@runOnUiThread
                                    }

                                    if (
                                        reminderViewModel.voiceState.value.isVoiceFlowActive &&
                                        !isAssistantConversationMode
                                    ) {
                                        startSpeechRecognition()
                                    } else {
                                        renderAssistantState(AssistantVisualState.IDLE)
                                    }
                                }
                            }
                        }

                        if (!state.isVoiceFlowActive) {
                            lastVoiceAssistantMessage = ""

                            if (!hasPendingSuccessState && !isAssistantConversationMode) {
                                renderAssistantState(AssistantVisualState.IDLE)
                            }
                        }
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
                                this@MainActivity,
                                event.message,
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        is ReminderUiEvent.ScheduleReminder -> {
                            reminderScheduler.scheduleReminder(
                                reminderTitle = event.reminderTitle,
                                reminderDetail = event.reminderDetail,
                                reminderDate = event.reminderDate,
                                reminderTime = event.reminderTime,
                                triggerTimeMillis = event.triggerTimeMillis
                            )

                            hasPendingSuccessState = true

                            if (!isAssistantConversationMode) {
                                showSuccessAndReturnToIdle()
                            }
                        }

                        is ReminderUiEvent.SpeakAssistantReply -> {
                            if (event.message.isNotBlank()) {
                                val shouldContinueListening = isAssistantConversationMode
                                renderAssistantSpeech(event.message)

                                voiceAssistantSpeaker.speakText(event.message) {
                                    runOnUiThread {
                                        if (hasPendingSuccessState) {
                                            showSuccessAndReturnToIdle()
                                            return@runOnUiThread
                                        }

                                        if (
                                            shouldContinueListening &&
                                            isAssistantConversationMode &&
                                            reminderViewModel.assistantState.value.isConversationActive
                                        ) {
                                            startSpeechRecognition()
                                        } else {
                                            renderAssistantState(AssistantVisualState.IDLE)
                                        }
                                    }
                                }
                            }
                        }

                        ReminderUiEvent.StopAssistantConversation -> {
                            speechManager.cancelRecognition()
                            isAssistantConversationMode = false

                            if (!hasPendingSuccessState) {
                                renderAssistantState(AssistantVisualState.IDLE)
                            }
                        }

                        ReminderUiEvent.ClearForm -> {
                            reminderViewModel.clearFormState()
                        }
                    }
                }
            }
        }
    }

    private fun startSpeechRecognition() {
        try {
            if (!speechManager.isRecognitionAvailable()) {
                throw IllegalStateException("Speech recognition is not available")
            }

            speechManager.cancelRecognition()

            renderAssistantState(AssistantVisualState.LISTENING)
            speechManager.startRecognition()
        } catch (exception: Exception) {
            exception.printStackTrace()
            renderAssistantState(AssistantVisualState.IDLE)

            Toast.makeText(
                this,
                getString(R.string.speech_not_available),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun checkAudioPermissionForVoiceFlow() {
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

    private fun renderAssistantState(
        state: AssistantVisualState,
        dialogueText: String? = null,
        faceState: AssistantFaceState = state.defaultFaceState
    ) {
        assistantResetJob?.cancel()
        currentAssistantVisualState = state

        val resolvedDialogueText = when {
            !dialogueText.isNullOrBlank() -> dialogueText
            state.label.isNotBlank() -> state.label
            lastDialogueText.isNotBlank() -> lastDialogueText
            else -> AssistantVisualState.IDLE.label
        }

        lastDialogueText = resolvedDialogueText
        assistantAnimator.render(state, faceState)
        renderDialogueBubble(state, resolvedDialogueText)
    }

    private fun updateAssistantVisualState(state: AssistantVisualState) {
        renderAssistantState(state)
    }

    private fun renderAssistantSpeech(message: String) {
        val visualState = message.toVisualState()
        renderAssistantState(
            state = visualState,
            dialogueText = message,
            faceState = message.toFaceState(visualState)
        )
    }

    private fun renderDialogueBubble(
        state: AssistantVisualState,
        resolvedDialogueText: String
    ) {
        when (state) {
            AssistantVisualState.IDLE -> {
                assistantDialogueBubble.hideBubble()
                updateTapHintVisibility(true)
            }

            AssistantVisualState.THINKING -> {
                assistantDialogueBubble.showMessage(
                    text = "...",
                    animateText = false,
                    playTypingSound = false
                )
                updateTapHintVisibility(false)
            }

            AssistantVisualState.LISTENING -> {
                assistantDialogueBubble.showMessage(
                    text = resolvedDialogueText,
                    animateText = false,
                    playTypingSound = false
                )
                updateTapHintVisibility(false)
            }

            AssistantVisualState.SUCCESS -> {
                assistantDialogueBubble.showMessage(
                    text = resolvedDialogueText,
                    animateText = false,
                    playTypingSound = false
                )
                updateTapHintVisibility(false)
            }

            AssistantVisualState.ASKING_TIME,
            AssistantVisualState.SPEAKING -> {
                assistantDialogueBubble.showMessage(
                    text = resolvedDialogueText,
                    animateText = true,
                    playTypingSound = true
                )
                updateTapHintVisibility(false)
            }
        }
    }

    private fun handleRecognizedText(text: String) {
        val recognizedText = text.trim()

        if (recognizedText.isBlank()) {
            handleRecognitionFailure(showNoMatchMessage = true)
            return
        }

        updateAssistantVisualState(AssistantVisualState.THINKING)

        if (isAssistantConversationMode) {
            reminderViewModel.processAssistantMessage(recognizedText)
        } else {
            reminderViewModel.processVoiceInput(recognizedText)
        }
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

    private fun handleAssistantSceneTap() {
        when {
            speechManager.isListeningOrStarting() -> {
                if (hasRecoverableVoiceFlow()) {
                    startSpeechRecognition()
                } else {
                    speechManager.cancelRecognition()
                    renderAssistantState(AssistantVisualState.IDLE)
                }
            }

            assistantDialogueBubble.isTypewriterRunning() -> {
                assistantDialogueBubble.completeTypingImmediately()
            }

            currentAssistantVisualState == AssistantVisualState.IDLE &&
                !hasPendingSuccessState -> {
                restartVoiceFlowFromIdle()
            }
        }
    }

    private fun restartVoiceFlowFromIdle() {
        if (hasRecoverableVoiceFlow()) {
            checkAudioPermissionAndStartListening()
        } else {
            checkAudioPermissionForVoiceFlow()
        }
    }

    private fun checkAudioPermissionAndStartListening() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startSpeechRecognition()
        } else {
            voiceAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun hasRecoverableVoiceFlow(): Boolean {
        val isAssistantConversationActive =
            isAssistantConversationMode && reminderViewModel.assistantState.value.isConversationActive

        val isVoiceReminderActive = reminderViewModel.voiceState.value.isVoiceFlowActive

        return isAssistantConversationActive || isVoiceReminderActive
    }

    private fun renderLiveTranscription(text: String) {
        val partialText = text.trim()

        if (partialText.isBlank()) return

        if (currentAssistantVisualState != AssistantVisualState.LISTENING) {
            renderAssistantState(
                state = AssistantVisualState.LISTENING,
                dialogueText = partialText,
                faceState = AssistantFaceState.ATTENTIVE
            )
            return
        }

        lastDialogueText = partialText
        assistantDialogueBubble.showMessage(
            text = partialText,
            animateText = false,
            playTypingSound = false
        )
        updateTapHintVisibility(false)
    }

    private fun updateTapHintVisibility(isVisible: Boolean) {
        assistantTapHint.animate().cancel()

        if (isVisible) {
            if (assistantTapHint.visibility != View.VISIBLE) {
                assistantTapHint.alpha = 0f
                assistantTapHint.visibility = View.VISIBLE
            }

            assistantTapHint.animate()
                .alpha(1f)
                .setDuration(140L)
                .start()
        } else {
            if (assistantTapHint.visibility != View.VISIBLE) return

            assistantTapHint.animate()
                .alpha(0f)
                .setDuration(110L)
                .withEndAction {
                    assistantTapHint.visibility = View.INVISIBLE
                }
                .start()
        }
    }

    private fun showSuccessAndReturnToIdle() {
        hasPendingSuccessState = false
        renderAssistantState(AssistantVisualState.SUCCESS)

        assistantResetJob = lifecycleScope.launch {
            delay(1200L)
            renderAssistantState(AssistantVisualState.IDLE)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onDestroy() {
        assistantResetJob?.cancel()
        assistantAnimator.stop()
        assistantDialogueBubble.stopAllEffects()
        speechManager.destroy()
        voiceAssistantSpeaker.setPlaybackListener(null)
        voiceAssistantSpeaker.shutdown()
        super.onDestroy()
    }

    private fun String.toVisualState(): AssistantVisualState {
        val normalized = lowercase()

        return when {
            "hora" in normalized && "?" in this -> AssistantVisualState.ASKING_TIME
            else -> AssistantVisualState.SPEAKING
        }
    }

    private fun String.toFaceState(visualState: AssistantVisualState): AssistantFaceState {
        val normalized = lowercase()

        return when (visualState) {
            AssistantVisualState.IDLE -> AssistantFaceState.RELAXED
            AssistantVisualState.LISTENING -> AssistantFaceState.ATTENTIVE
            AssistantVisualState.THINKING -> AssistantFaceState.NEUTRAL_PAUSE
            AssistantVisualState.ASKING_TIME -> AssistantFaceState.SURPRISE
            AssistantVisualState.SUCCESS -> AssistantFaceState.SATISFIED
            AssistantVisualState.SPEAKING -> when {
                listOf("listo", "guard", "hecho", "perfect", "claro", "confirm").any {
                    normalized.contains(it)
                } -> AssistantFaceState.CONFIRMATION

                contains("?") || listOf("que", "como", "cuando").any {
                    normalized.contains(it)
                } -> AssistantFaceState.SURPRISE

                else -> AssistantFaceState.NATURAL_SPEAKING
            }
        }
    }

    private val AssistantVisualState.defaultFaceState: AssistantFaceState
        get() = when (this) {
            AssistantVisualState.IDLE -> AssistantFaceState.RELAXED
            AssistantVisualState.LISTENING -> AssistantFaceState.ATTENTIVE
            AssistantVisualState.THINKING -> AssistantFaceState.NEUTRAL_PAUSE
            AssistantVisualState.ASKING_TIME -> AssistantFaceState.SURPRISE
            AssistantVisualState.SPEAKING -> AssistantFaceState.NATURAL_SPEAKING
            AssistantVisualState.SUCCESS -> AssistantFaceState.SATISFIED
        }

    private val AssistantVisualState.label: String
        get() = when (this) {
            AssistantVisualState.IDLE -> "Estoy lista"
            AssistantVisualState.LISTENING -> "Te escucho"
            AssistantVisualState.THINKING -> "Un momento..."
            AssistantVisualState.ASKING_TIME -> "\u00BFA qu\u00E9 hora?"
            AssistantVisualState.SPEAKING -> "Hablando..."
            AssistantVisualState.SUCCESS -> "Listo, ya lo guard\u00E9"
        }
}
