package com.luistureo.voicereminderapp.presentation.manual

import android.app.DatePickerDialog
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarAuthManager
import com.luistureo.voicereminderapp.core.calendar.google.GoogleCalendarReminderSynchronizer
import com.luistureo.voicereminderapp.core.calendar.microsoft.MicrosoftCalendarAuthController
import com.luistureo.voicereminderapp.core.calendar.microsoft.MicrosoftCalendarAuthProvider
import com.luistureo.voicereminderapp.core.calendar.unified.UnifiedCalendarSynchronizer
import com.luistureo.voicereminderapp.core.ocr.CameraReminderDraftExtractor
import com.luistureo.voicereminderapp.core.ocr.CameraReminderScanResult
import com.luistureo.voicereminderapp.core.ocr.LocalImageTextRecognizer
import com.luistureo.voicereminderapp.core.utils.DateTimeFormatter
import com.luistureo.voicereminderapp.data.local.database.ReminderDatabase
import com.luistureo.voicereminderapp.data.repository.ReminderRepositoryImpl
import com.luistureo.voicereminderapp.domain.model.CalendarProvider
import com.luistureo.voicereminderapp.domain.model.ReminderDraft
import com.luistureo.voicereminderapp.domain.model.ReminderRecurrence
import com.luistureo.voicereminderapp.domain.model.ReminderRecurrenceUnit
import com.luistureo.voicereminderapp.domain.model.ReminderSource
import com.luistureo.voicereminderapp.domain.usecase.DeleteReminderUseCase
import com.luistureo.voicereminderapp.domain.usecase.GetReminderByIdUseCase
import com.luistureo.voicereminderapp.domain.usecase.GetRemindersUseCase
import com.luistureo.voicereminderapp.domain.usecase.SaveReminderDraftUseCase
import com.luistureo.voicereminderapp.domain.usecase.UpdateReminderUseCase
import com.luistureo.voicereminderapp.presentation.state.ReminderFormState
import com.luistureo.voicereminderapp.presentation.state.ReminderUiEvent
import com.luistureo.voicereminderapp.presentation.viewmodel.ReminderViewModel
import com.luistureo.voicereminderapp.presentation.viewmodel.ReminderViewModelFactory
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

class ManualReminderActivity : ComponentActivity() {

    private enum class RecurrenceOption(val labelResId: Int) {
        NONE(R.string.reminder_recurrence_none),
        DAILY(R.string.reminder_recurrence_daily),
        WEEKLY(R.string.reminder_recurrence_weekly),
        MONTHLY(R.string.reminder_recurrence_monthly),
        YEARLY(R.string.reminder_recurrence_yearly)
    }

    private lateinit var backButton: ImageButton
    private lateinit var saveButton: MaterialButton
    private lateinit var screenTitle: TextView
    private lateinit var screenSubtitle: TextView
    private lateinit var cameraInputCard: View
    private lateinit var cameraPreviewImage: ImageView
    private lateinit var cameraScanStatusText: TextView
    private lateinit var cameraScanProgress: LinearProgressIndicator
    private lateinit var takePhotoButton: MaterialButton
    private lateinit var selectImageButton: MaterialButton
    private lateinit var titleInput: TextInputEditText
    private lateinit var detailInput: TextInputEditText
    private lateinit var dateButton: MaterialButton
    private lateinit var timeButton: MaterialButton
    private lateinit var urgentSwitch: SwitchMaterial
    private lateinit var recurrenceButtons: Map<RecurrenceOption, MaterialButton>
    private lateinit var syncOptionsContainer: View
    private lateinit var googleSyncCheckBox: MaterialCheckBox
    private lateinit var microsoftSyncCheckBox: MaterialCheckBox

    private lateinit var reminderViewModel: ReminderViewModel
    private lateinit var cameraDraftExtractor: CameraReminderDraftExtractor
    private lateinit var googleCalendarAuthManager: GoogleCalendarAuthManager
    private lateinit var microsoftCalendarAuthController: MicrosoftCalendarAuthController

    private var currentReminderId: Int = 0
    private var currentSource: ReminderSource = ReminderSource.MANUAL
    private var selectedDate: String = ""
    private var selectedTime: String = ""
    private var selectedRecurrenceOption: RecurrenceOption = RecurrenceOption.NONE
    private var isBindingForm: Boolean = false
    private var currentCameraImageUri: Uri? = null
    private var isProcessingCameraImage: Boolean = false
    private var isDateLocked: Boolean = false
    private var availableSyncProviders: Set<CalendarProvider> = emptySet()
    private var linkedSyncProviders: Set<CalendarProvider> = emptySet()

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                launchTakePicture()
            } else {
                Toast.makeText(
                    this,
                    R.string.camera_reminder_camera_permission_denied,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
            val imageUri = currentCameraImageUri

            if (isSuccess && imageUri != null) {
                processSelectedImage(imageUri)
            } else {
                Toast.makeText(
                    this,
                    R.string.camera_reminder_capture_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val selectImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let(::processSelectedImage)
        }

    companion object {
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_DEFAULT_SOURCE = "extra_default_source"
        const val EXTRA_PREFILLED_DATE = "extra_prefilled_date"
        const val EXTRA_LOCK_DATE = "extra_lock_date"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual_reminder)

        initViewModel()
        initViews()
        initCameraTools()
        setupRecurrenceOptions()
        setupListeners()
        refreshCalendarSyncOptions()
        observeState()
        observeEvents()
        loadInitialForm()
    }

    override fun onDestroy() {
        cameraDraftExtractor.close()
        super.onDestroy()
    }

    private fun initViewModel() {
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

    private fun initViews() {
        backButton = findViewById(R.id.btnBackManualReminder)
        saveButton = findViewById(R.id.btnSaveReminder)
        screenTitle = findViewById(R.id.tvManualReminderScreenTitle)
        screenSubtitle = findViewById(R.id.tvManualReminderScreenSubtitle)
        cameraInputCard = findViewById(R.id.cardCameraInput)
        cameraPreviewImage = findViewById(R.id.imageCameraPreview)
        cameraScanStatusText = findViewById(R.id.tvCameraScanStatus)
        cameraScanProgress = findViewById(R.id.progressCameraScan)
        takePhotoButton = findViewById(R.id.btnTakeCameraReminderPhoto)
        selectImageButton = findViewById(R.id.btnSelectCameraReminderImage)
        titleInput = findViewById(R.id.inputReminderTitle)
        detailInput = findViewById(R.id.inputReminderDetail)
        dateButton = findViewById(R.id.btnSelectDate)
        timeButton = findViewById(R.id.btnSelectTime)
        urgentSwitch = findViewById(R.id.switchUrgent)
        syncOptionsContainer = findViewById(R.id.containerManualCalendarSyncOptions)
        googleSyncCheckBox = findViewById(R.id.checkManualSyncGoogle)
        microsoftSyncCheckBox = findViewById(R.id.checkManualSyncMicrosoft)
        recurrenceButtons = mapOf(
            RecurrenceOption.NONE to findViewById(R.id.btnRecurrenceNone),
            RecurrenceOption.DAILY to findViewById(R.id.btnRecurrenceDaily),
            RecurrenceOption.WEEKLY to findViewById(R.id.btnRecurrenceWeekly),
            RecurrenceOption.MONTHLY to findViewById(R.id.btnRecurrenceMonthly),
            RecurrenceOption.YEARLY to findViewById(R.id.btnRecurrenceYearly)
        )
    }

    private fun initCameraTools() {
        cameraDraftExtractor = CameraReminderDraftExtractor(
            LocalImageTextRecognizer(applicationContext)
        )
    }

    private fun setupRecurrenceOptions() {
        updateRecurrenceButtons()
    }

    private fun setupListeners() {
        backButton.setOnClickListener { finish() }
        saveButton.setOnClickListener { saveReminder() }
        dateButton.setOnClickListener {
            if (!isDateLocked) {
                showDatePicker()
            }
        }
        timeButton.setOnClickListener { showTimePicker() }
        takePhotoButton.setOnClickListener { openCameraForReminder() }
        selectImageButton.setOnClickListener { selectImageLauncher.launch("image/*") }

        recurrenceButtons.forEach { (option, button) ->
            button.setOnClickListener {
                if (isBindingForm) return@setOnClickListener
                selectedRecurrenceOption = option
                updateRecurrenceButtons()
            }
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
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                reminderViewModel.formState.collect { formState ->
                    bindFormState(formState)
                }
            }
        }
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                reminderViewModel.events.collect { event ->
                    if (event is ReminderUiEvent.ShowMessage) {
                        Toast.makeText(
                            this@ManualReminderActivity,
                            event.message,
                            Toast.LENGTH_SHORT
                        ).show()

                        if (
                            event.message.contains("guardado", ignoreCase = true) ||
                            event.message.contains("actualizado", ignoreCase = true)
                        ) {
                            finish()
                        }
                    }
                }
            }
        }
    }

    private fun loadInitialForm() {
        val reminderId = intent.getIntExtra(EXTRA_REMINDER_ID, 0)
        val defaultSource = intent.getStringExtra(EXTRA_DEFAULT_SOURCE)
            ?.let { runCatching { ReminderSource.valueOf(it) }.getOrNull() }
            ?: ReminderSource.MANUAL
        val prefilledDate = intent.getStringExtra(EXTRA_PREFILLED_DATE).orEmpty()

        currentSource = defaultSource
        currentReminderId = reminderId
        isDateLocked = intent.getBooleanExtra(EXTRA_LOCK_DATE, false) && prefilledDate.isNotBlank()

        if (reminderId > 0) {
            reminderViewModel.loadReminderForEditing(reminderId, defaultSource)
        } else {
            reminderViewModel.startManualReminderForm(defaultSource, prefilledDate)
        }
    }

    private fun bindFormState(formState: ReminderFormState) {
        isBindingForm = true

        currentReminderId = formState.reminderId
        currentSource = formState.source
        cameraInputCard.isVisible = currentSource == ReminderSource.CAMERA
        updateCameraScanUiState()

        screenTitle.text = when {
            currentReminderId != 0 -> getString(R.string.reminder_editor_edit_title)
            currentSource == ReminderSource.CAMERA -> getString(R.string.reminder_editor_camera_title)
            else -> getString(R.string.manual_reminder_screen_title)
        }

        screenSubtitle.text = when {
            currentSource == ReminderSource.CAMERA -> getString(R.string.home_camera_reminder_subtitle)
            else -> getString(R.string.manual_reminder_screen_subtitle)
        }

        if (titleInput.text?.toString().orEmpty() != formState.title) {
            titleInput.setText(formState.title)
        }

        if (detailInput.text?.toString().orEmpty() != formState.detail) {
            detailInput.setText(formState.detail)
        }

        selectedDate = formState.date
        selectedTime = formState.time
        updateDateTimeButtons()

        urgentSwitch.isChecked = formState.isUrgent
        linkedSyncProviders = formState.syncTargetProviders
        googleSyncCheckBox.isChecked = CalendarProvider.GOOGLE_CALENDAR in
                formState.syncTargetProviders
        microsoftSyncCheckBox.isChecked = CalendarProvider.MICROSOFT_CALENDAR in
                formState.syncTargetProviders
        selectedRecurrenceOption = formState.recurrence?.toOption() ?: RecurrenceOption.NONE
        updateRecurrenceButtons()

        isBindingForm = false
    }

    private fun updateDateTimeButtons() {
        dateButton.text = selectedDate.takeIf { it.isNotBlank() }
            ?: getString(R.string.reminder_select_date)
        dateButton.isEnabled = !isDateLocked
        timeButton.text = selectedTime.takeIf { it.isNotBlank() }
            ?: getString(R.string.reminder_select_time)
    }

    private fun updateRecurrenceButtons() {
        val selectedBackground = ContextCompat.getColor(
            this,
            R.color.recurrence_chip_selected_background
        )
        val defaultBackground = ContextCompat.getColor(
            this,
            R.color.recurrence_chip_background
        )
        val stroke = ContextCompat.getColor(this, R.color.recurrence_chip_stroke)
        val text = ContextCompat.getColor(this, R.color.reminder_text_primary)

        recurrenceButtons.forEach { (option, button) ->
            val isSelected = option == selectedRecurrenceOption
            button.backgroundTintList = ColorStateList.valueOf(
                if (isSelected) selectedBackground else defaultBackground
            )
            button.strokeColor = ColorStateList.valueOf(stroke)
            button.setTextColor(text)
        }
    }

    private fun showDatePicker() {
        val parsedDate = DateTimeFormatter.parseDate(selectedDate)
        val calendar = Calendar.getInstance().apply {
            if (parsedDate != null) {
                set(Calendar.YEAR, parsedDate.year)
                set(Calendar.MONTH, parsedDate.monthValue - 1)
                set(Calendar.DAY_OF_MONTH, parsedDate.dayOfMonth)
            }
        }

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                selectedDate = DateTimeFormatter.formatDate(dayOfMonth, month + 1, year)
                updateDateTimeButtons()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePicker() {
        val parsedTime = DateTimeFormatter.parseTime(selectedTime)
        val calendar = Calendar.getInstance().apply {
            if (parsedTime != null) {
                set(Calendar.HOUR_OF_DAY, parsedTime.hour)
                set(Calendar.MINUTE, parsedTime.minute)
            }
        }

        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                selectedTime = DateTimeFormatter.formatTime(hourOfDay, minute)
                updateDateTimeButtons()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun saveReminder() {
        val detail = detailInput.text?.toString()?.trim().orEmpty()
        val title = titleInput.text?.toString()?.trim().takeIf { !it.isNullOrBlank() }
        val selectedDateValue = selectedDate.trim()
        val selectedTimeValue = selectedTime.trim()

        if (detail.isBlank()) {
            Toast.makeText(this, R.string.reminder_error_detail_required, Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedDateValue.isBlank()) {
            Toast.makeText(this, R.string.reminder_error_date_required, Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedTimeValue.isBlank()) {
            Toast.makeText(this, R.string.reminder_error_time_required, Toast.LENGTH_SHORT).show()
            return
        }

        val draft = ReminderDraft(
            reminderId = currentReminderId,
            title = title,
            text = detail,
            date = selectedDateValue,
            time = selectedTimeValue,
            isUrgent = urgentSwitch.isChecked,
            source = currentSource,
            recurrence = buildRecurrence(),
            syncTargetProviders = selectedSyncTargetProviders()
        )

        val removedProviders = linkedSyncProviders - draft.syncTargetProviders
        if (currentReminderId > 0 && removedProviders.isNotEmpty()) {
            showCalendarDetachConfirmation(draft, removedProviders)
        } else {
            reminderViewModel.saveReminderDraft(draft)
        }
    }

    private fun selectedSyncTargetProviders(): Set<CalendarProvider> {
        return buildSet {
            addAll(linkedSyncProviders - availableSyncProviders)
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

    private fun showCalendarDetachConfirmation(
        draft: ReminderDraft,
        removedProviders: Set<CalendarProvider>
    ) {
        val providerNames = removedProviders.joinToString(separator = " y ") { it.displayName }
        AlertDialog.Builder(this)
            .setTitle(R.string.calendar_sync_detach_title)
            .setMessage(getString(R.string.calendar_sync_detach_message, providerNames))
            .setNegativeButton(R.string.reminder_cancel_action, null)
            .setPositiveButton(R.string.reminder_save_action) { _, _ ->
                reminderViewModel.saveReminderDraft(draft)
            }
            .show()
    }

    private fun openCameraForReminder() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            launchTakePicture()
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private fun launchTakePicture() {
        runCatching {
            val tempFile = createCameraImageFile()
            val authority = "${applicationContext.packageName}.fileprovider"
            val imageUri = FileProvider.getUriForFile(this, authority, tempFile)
            currentCameraImageUri = imageUri
            takePictureLauncher.launch(imageUri)
        }.onFailure {
            currentCameraImageUri = null
            isProcessingCameraImage = false
            cameraScanStatusText.text = getString(R.string.camera_reminder_capture_failed)
            updateCameraScanUiState()
            Toast.makeText(
                this,
                R.string.camera_reminder_capture_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun createCameraImageFile(): File {
        val imageDirectory = File(cacheDir, "camera_reminder_images").apply {
            if (!exists()) {
                mkdirs()
            }
        }

        return File.createTempFile(
            "camera_reminder_",
            ".jpg",
            imageDirectory
        )
    }

    private fun processSelectedImage(imageUri: Uri) {
        currentCameraImageUri = imageUri
        cameraPreviewImage.setImageURI(imageUri)
        isProcessingCameraImage = true
        updateCameraScanUiState()

        lifecycleScope.launch {
            runCatching {
                cameraDraftExtractor.extractFromUri(imageUri)
            }.onSuccess { scanResult ->
                applyCameraScanResult(scanResult)
            }.onFailure {
                isProcessingCameraImage = false
                cameraScanStatusText.text = getString(R.string.camera_reminder_scan_error)
                updateCameraScanUiState()
            }
        }
    }

    private fun applyCameraScanResult(scanResult: CameraReminderScanResult) {
        isProcessingCameraImage = false

        val detectedText = scanResult.draft.text
            ?: scanResult.recognizedText.trim().takeIf { it.isNotBlank() }

        val draft = scanResult.draft.copy(
            text = detectedText,
            date = if (isDateLocked) selectedDate else scanResult.draft.date,
            source = ReminderSource.CAMERA
        )

        if (!detectedText.isNullOrBlank()) {
            reminderViewModel.applyDraftToForm(draft, ReminderSource.CAMERA)
        }

        cameraScanStatusText.text = buildCameraScanStatusMessage(scanResult)
        updateCameraScanUiState()

        when {
            detectedText.isNullOrBlank() -> detailInput.requestFocus()
            draft.date.isNullOrBlank() -> dateButton.requestFocus()
            draft.time.isNullOrBlank() -> timeButton.requestFocus()
            else -> saveButton.requestFocus()
        }
    }

    private fun buildCameraScanStatusMessage(scanResult: CameraReminderScanResult): String {
        return when {
            scanResult.hasDetectedReminderText && scanResult.hasDetectedDate && scanResult.hasDetectedTime ->
                getString(R.string.camera_reminder_scan_success_full)

            scanResult.hasDetectedReminderText && (scanResult.hasDetectedDate || scanResult.hasDetectedTime) ->
                getString(R.string.camera_reminder_scan_success_partial_schedule)

            scanResult.hasDetectedReminderText || scanResult.recognizedText.isNotBlank() ->
                getString(R.string.camera_reminder_scan_success_text_only)

            scanResult.hasDetectedDate || scanResult.hasDetectedTime ->
                getString(R.string.camera_reminder_scan_success_schedule_only)

            else -> getString(R.string.camera_reminder_scan_error)
        }
    }

    private fun updateCameraScanUiState() {
        cameraPreviewImage.isVisible = currentCameraImageUri != null
        cameraScanProgress.isVisible = isProcessingCameraImage
        takePhotoButton.isEnabled = !isProcessingCameraImage
        selectImageButton.isEnabled = !isProcessingCameraImage

        if (isProcessingCameraImage) {
            cameraScanStatusText.text = getString(R.string.camera_reminder_scan_processing)
        } else if (cameraScanStatusText.text.isNullOrBlank()) {
            cameraScanStatusText.text = getString(R.string.camera_reminder_scan_idle)
        }
    }

    private fun buildRecurrence(): ReminderRecurrence? {
        return when (selectedRecurrenceOption) {
            RecurrenceOption.NONE -> null
            RecurrenceOption.DAILY -> ReminderRecurrence(ReminderRecurrenceUnit.DAY)
            RecurrenceOption.WEEKLY -> ReminderRecurrence(ReminderRecurrenceUnit.WEEK)
            RecurrenceOption.MONTHLY -> ReminderRecurrence(ReminderRecurrenceUnit.MONTH)
            RecurrenceOption.YEARLY -> ReminderRecurrence(ReminderRecurrenceUnit.YEAR)
        }
    }

    private fun ReminderRecurrence.toOption(): RecurrenceOption {
        return when {
            unit == ReminderRecurrenceUnit.DAY && normalizedInterval == 1 -> RecurrenceOption.DAILY
            unit == ReminderRecurrenceUnit.WEEK && normalizedInterval == 1 -> RecurrenceOption.WEEKLY
            unit == ReminderRecurrenceUnit.MONTH && normalizedInterval == 1 -> RecurrenceOption.MONTHLY
            unit == ReminderRecurrenceUnit.YEAR && normalizedInterval == 1 -> RecurrenceOption.YEARLY
            else -> RecurrenceOption.NONE
        }
    }
}
