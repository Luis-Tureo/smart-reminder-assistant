package com.luistureo.voicereminderapp

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
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
import com.luistureo.voicereminderapp.presentation.state.ReminderUiEvent
import com.luistureo.voicereminderapp.presentation.ui.adapter.ReminderAdapter
import com.luistureo.voicereminderapp.presentation.ui.swipe.SwipeToDeleteCallback
import com.luistureo.voicereminderapp.presentation.viewmodel.ReminderViewModel
import com.luistureo.voicereminderapp.presentation.viewmodel.ReminderViewModelFactory
import kotlinx.coroutines.launch
import java.util.Calendar

class MainActivity : ComponentActivity() {

    private lateinit var selectedDateTimeTextView: TextView
    private lateinit var resultTextView: TextView
    private lateinit var selectDateButton: Button
    private lateinit var selectTimeButton: Button
    private lateinit var speakButton: Button
    private lateinit var saveButton: Button
    private lateinit var voiceButton: MaterialButton
    private lateinit var remindersRecyclerView: RecyclerView

    private lateinit var reminderAdapter: ReminderAdapter
    private lateinit var reminderViewModel: ReminderViewModel
    private lateinit var reminderScheduler: ReminderScheduler
    private lateinit var speechManager: SpeechRecognizerManager
    private lateinit var voiceAssistantSpeaker: VoiceAssistantSpeaker

    private var lastVoiceAssistantMessage: String = ""

    private val speechRecognitionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val text = speechManager.extractResult(result.resultCode, result.data)

            if (text != null) {
                resultTextView.text = text

                // 🔥 clave: enviar al flujo conversacional
                reminderViewModel.processVoiceInput(text)
            } else {
                Toast.makeText(
                    this,
                    "No se entendió el audio, intenta nuevamente",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startSpeechRecognition()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.mic_permission_denied),
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
                reminderViewModel.startVoiceReminderFlow()
            } else {
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
        setupViewModel()
        setupCore()
        setupRecyclerView()
        setupSwipeToDelete()
        //setupListeners()
        setupVoiceButton()
        observeState()
        observeEvents()
        requestNotificationPermissionIfNeeded()
    }

    private fun initViews() {
        selectedDateTimeTextView = findViewById(R.id.tvSelectedDateTime)
        resultTextView = findViewById(R.id.tvResult)
        /* selectDateButton = findViewById(R.id.btnSelectDate)
        selectTimeButton = findViewById(R.id.btnSelectTime)
        speakButton = findViewById(R.id.btnSpeak)
        saveButton = findViewById(R.id.btnSave) */
        voiceButton = findViewById(R.id.btnVoiceReminder)
        remindersRecyclerView = findViewById(R.id.recyclerReminders)
    }

    private fun setupViewModel() {
        val database = ReminderDatabase.getDatabase(this)
        val repository = ReminderRepositoryImpl(database.reminderDao())

        val factory = ReminderViewModelFactory(
            AddReminderUseCase(repository),
            GetRemindersUseCase(repository),
            DeleteReminderUseCase(repository),
            UpdateReminderUseCase(repository)
        )

        reminderViewModel = ViewModelProvider(this, factory)[ReminderViewModel::class.java]
    }

    private fun setupCore() {
        reminderScheduler = ReminderScheduler(this)
        speechManager = SpeechRecognizerManager()
        voiceAssistantSpeaker = VoiceAssistantSpeaker(this)
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
            val reminder = reminderViewModel.uiState.value.reminders[position]
            reminderViewModel.deleteReminder(reminder)
        }

        val itemTouchHelper = ItemTouchHelper(swipeHandler)
        itemTouchHelper.attachToRecyclerView(remindersRecyclerView)
    }

    /* private fun setupListeners() {
        selectDateButton.setOnClickListener {
            openDatePicker()
        }

        selectTimeButton.setOnClickListener {
            openTimePicker()
        }

        speakButton.setOnClickListener {
            checkAudioPermissionForManualRecognition()
        }

        saveButton.setOnClickListener {
            reminderViewModel.saveReminder(
                resultTextView.text.toString().trim()
            )
        }
    }*/

    private fun setupVoiceButton() {
        voiceButton.setOnClickListener {
            checkAudioPermissionForVoiceFlow()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    reminderViewModel.uiState.collect { state ->
                        reminderAdapter.updateData(state.reminders)

                        if (state.error != null) {
                            Toast.makeText(
                                this@MainActivity,
                                state.error.asString(this@MainActivity),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                launch {
                    reminderViewModel.formState.collect { form ->
                        val hasDate =
                            form.selectedDay != -1 &&
                                    form.selectedMonth != -1 &&
                                    form.selectedYear != -1

                        val hasTime =
                            form.selectedHour != -1 &&
                                    form.selectedMinute != -1

                        selectedDateTimeTextView.text = when {
                            hasDate && hasTime -> {
                                val dateText = String.format(
                                    "%02d/%02d/%04d",
                                    form.selectedDay,
                                    form.selectedMonth,
                                    form.selectedYear
                                )

                                val timeText = String.format(
                                    "%02d:%02d",
                                    form.selectedHour,
                                    form.selectedMinute
                                )

                                "$dateText $timeText"
                            }

                            hasDate -> {
                                String.format(
                                    "%02d/%02d/%04d",
                                    form.selectedDay,
                                    form.selectedMonth,
                                    form.selectedYear
                                )
                            }

                            hasTime -> {
                                String.format(
                                    "%02d:%02d",
                                    form.selectedHour,
                                    form.selectedMinute
                                )
                            }

                            else -> "Fecha y hora no seleccionadas"
                        }
                    }
                }

                launch {
                    reminderViewModel.voiceState.collect { state ->
                        val message = state.lastAssistantMessage

                        if (
                            state.isVoiceFlowActive &&
                            message.isNotBlank() &&
                            message != lastVoiceAssistantMessage
                        ) {
                            lastVoiceAssistantMessage = message

                            voiceAssistantSpeaker.speakText(message) {
                                runOnUiThread {
                                    if (reminderViewModel.voiceState.value.isVoiceFlowActive) {
                                        startVoiceRecognition()
                                    }
                                }
                            }
                        }

                        if (!state.isVoiceFlowActive) {
                            lastVoiceAssistantMessage = ""
                        }
                    }
                }
            }
        }
    }

    private fun observeEvents() {
        lifecycleScope.launch {
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
                            reminderText = event.reminderText,
                            reminderDate = event.reminderDate,
                            reminderTime = event.reminderTime,
                            triggerTimeMillis = event.triggerTimeMillis
                        )
                    }

                    ReminderUiEvent.ClearForm -> {
                        resultTextView.text = getString(R.string.recognized_text_placeholder)
                        reminderViewModel.clearFormState()
                    }
                }
            }
        }
    }

    private fun startVoiceRecognition() {
        try {
            speechManager.startRecognition(speechRecognitionLauncher)
        } catch (exception: Exception) {
            exception.printStackTrace()

            Toast.makeText(
                this,
                getString(R.string.speech_not_available),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun startSpeechRecognition() {
        try {
            speechManager.startRecognition(speechRecognitionLauncher)
        } catch (exception: Exception) {
            exception.printStackTrace()
            Toast.makeText(
                this,
                getString(R.string.speech_not_available),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun checkAudioPermissionForManualRecognition() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startSpeechRecognition()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun checkAudioPermissionForVoiceFlow() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            reminderViewModel.startVoiceReminderFlow()
        } else {
            voiceAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
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

    private fun openDatePicker() {
        val form = reminderViewModel.formState.value
        val calendar = Calendar.getInstance()

        val initialYear =
            if (form.selectedYear != -1) form.selectedYear else calendar.get(Calendar.YEAR)

        val initialMonth =
            if (form.selectedMonth != -1) form.selectedMonth - 1 else calendar.get(Calendar.MONTH)

        val initialDay =
            if (form.selectedDay != -1) form.selectedDay else calendar.get(Calendar.DAY_OF_MONTH)

        val datePicker = DatePickerDialog(
            this,
            { _, year, month, day ->
                reminderViewModel.onDateSelected(year, month + 1, day)
            },
            initialYear,
            initialMonth,
            initialDay
        )

        datePicker.show()
    }

    private fun openTimePicker() {
        val form = reminderViewModel.formState.value
        val calendar = Calendar.getInstance()

        val timePicker = TimePickerDialog(
            this,
            { _, hour, minute ->
                reminderViewModel.onTimeSelected(hour, minute)
            },
            if (form.selectedHour != -1) form.selectedHour else calendar.get(Calendar.HOUR_OF_DAY),
            if (form.selectedMinute != -1) form.selectedMinute else calendar.get(Calendar.MINUTE),
            true
        )

        timePicker.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceAssistantSpeaker.shutdown()
    }
}