package com.luistureo.voicereminderapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
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
import com.google.android.material.button.MaterialButton
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

    private lateinit var assistantView: AssistantView
    private lateinit var voiceButton: MaterialButton
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
    private val dialoguePanelInterpolator = AccelerateDecelerateInterpolator()

    private val speechRecognitionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val text = speechManager.extractResult(result.resultCode, result.data)

            if (!text.isNullOrBlank()) {
                updateAssistantVisualState(AssistantVisualState.THINKING)

                if (isAssistantConversationMode) {
                    reminderViewModel.processAssistantMessage(text)
                } else {
                    reminderViewModel.processVoiceInput(text)
                }
            } else {
                updateAssistantVisualState(AssistantVisualState.IDLE)
                Toast.makeText(
                    this,
                    "No se entendio el audio, intenta nuevamente",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

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
                startAssistantConversation()
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
        setupVoiceButton()
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
        super.onStop()
    }

    private fun initViews() {
        assistantView = findViewById(R.id.assistantView)
        voiceButton = findViewById(R.id.btnVoiceReminder)
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
        speechManager = SpeechRecognizerManager()
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

    private fun setupVoiceButton() {
        voiceButton.setOnClickListener {
            checkAudioPermissionForVoiceFlow()
        }
    }

    private fun startAssistantConversation() {
        hasPendingSuccessState = false
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
            renderAssistantState(AssistantVisualState.LISTENING)
            speechManager.startRecognition(speechRecognitionLauncher)
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
        animateDialoguePanel(resolvedDialogueText)
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

    private fun showSuccessAndReturnToIdle() {
        hasPendingSuccessState = false
        renderAssistantState(AssistantVisualState.SUCCESS)

        assistantResetJob = lifecycleScope.launch {
            delay(950L)
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

    private fun animateDialoguePanel(targetText: String) {
        if (
            voiceButton.text?.toString() == targetText &&
            voiceButton.alpha == 1f &&
            voiceButton.translationY == 0f
        ) {
            return
        }

        val offsetY = 10f * resources.displayMetrics.density
        voiceButton.animate().cancel()

        val showPanel = {
            voiceButton.text = targetText
            voiceButton.visibility = View.VISIBLE
            voiceButton.alpha = 0f
            voiceButton.translationY = offsetY
            voiceButton.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200L)
                .setInterpolator(dialoguePanelInterpolator)
                .start()
        }

        if (voiceButton.visibility == View.VISIBLE && voiceButton.alpha > 0.05f) {
            voiceButton.animate()
                .alpha(0f)
                .translationY(offsetY)
                .setDuration(120L)
                .setInterpolator(dialoguePanelInterpolator)
                .withEndAction(showPanel)
                .start()
        } else {
            showPanel()
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
            AssistantVisualState.ASKING_TIME -> "A que hora?"
            AssistantVisualState.SPEAKING -> ""
            AssistantVisualState.SUCCESS -> "Listo, ya lo guarde"
        }
}
