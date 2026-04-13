package com.luistureo.voicereminderapp.presentation.manual

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.ocr.CameraReminderDraftExtractor
import com.luistureo.voicereminderapp.core.ocr.CameraReminderScanResult
import com.luistureo.voicereminderapp.core.ocr.LocalImageTextRecognizer
import com.luistureo.voicereminderapp.core.reminder.ReminderDraftFormStateResolver
import com.luistureo.voicereminderapp.core.utils.DateTimeFormatterCore
import com.luistureo.voicereminderapp.core.utils.DateTimeFormStateResolver
import com.luistureo.voicereminderapp.data.local.database.ReminderDatabase
import com.luistureo.voicereminderapp.data.repository.ReminderRepositoryImpl
import com.luistureo.voicereminderapp.domain.model.ReminderDraft
import com.luistureo.voicereminderapp.domain.model.ReminderRecurrence
import com.luistureo.voicereminderapp.domain.model.ReminderRecurrenceUnit
import com.luistureo.voicereminderapp.domain.model.ReminderSource
import com.luistureo.voicereminderapp.domain.model.ReminderWeekday
import com.luistureo.voicereminderapp.domain.usecase.DeleteReminderUseCase
import com.luistureo.voicereminderapp.domain.usecase.GetReminderByIdUseCase
import com.luistureo.voicereminderapp.domain.usecase.GetRemindersUseCase
import com.luistureo.voicereminderapp.domain.usecase.SaveReminderDraftUseCase
import com.luistureo.voicereminderapp.domain.usecase.UpdateReminderUseCase
import com.luistureo.voicereminderapp.presentation.camera.CameraReminderConfirmationActivity
import com.luistureo.voicereminderapp.presentation.camera.CameraReminderDraftContract
import com.luistureo.voicereminderapp.presentation.state.ReminderUiEvent
import com.luistureo.voicereminderapp.presentation.viewmodel.ReminderViewModel
import com.luistureo.voicereminderapp.presentation.viewmodel.ReminderViewModelFactory
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

class ManualReminderActivity : ComponentActivity() {

    private enum class RecurrenceOption(val labelResId: Int) {
        DAILY(R.string.reminder_recurrence_daily),
        WEEKLY(R.string.reminder_recurrence_weekly),
        MONTHLY(R.string.reminder_recurrence_monthly),
        YEARLY(R.string.reminder_recurrence_yearly),
        SPECIFIC_WEEKDAYS(R.string.reminder_recurrence_specific_weekdays),
        EVERY_X_DAYS(R.string.reminder_recurrence_every_x_days),
        EVERY_X_WEEKS(R.string.reminder_recurrence_every_x_weeks),
        EVERY_X_MONTHS(R.string.reminder_recurrence_every_x_months)
    }

    private lateinit var backButton: ImageButton
    private lateinit var saveButton: MaterialButton
    private lateinit var screenTitle: TextView
    private lateinit var screenSubtitle: TextView
    private lateinit var cameraInputCard: android.view.View
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
    private lateinit var recurringEnabledSwitch: SwitchMaterial
    private lateinit var recurrenceInput: MaterialAutoCompleteTextView
    private lateinit var recurrenceIntervalLayout: TextInputLayout
    private lateinit var recurrenceIntervalInput: TextInputEditText
    private lateinit var recurrenceActiveSwitch: SwitchMaterial
    private lateinit var recurringContent: android.view.View
    private lateinit var weekdayChecks: Map<ReminderWeekday, CheckBox>

    private lateinit var reminderViewModel: ReminderViewModel
    private lateinit var cameraDraftExtractor: CameraReminderDraftExtractor

    private var currentReminderId: Int = 0
    private var currentSource: ReminderSource = ReminderSource.MANUAL
    private var selectedDate: String = ""
    private var selectedTime: String = ""
    private var selectedRecurrenceOption: RecurrenceOption = RecurrenceOption.DAILY
    private var lastSelectedRecurrenceOption: RecurrenceOption = RecurrenceOption.DAILY
    private var isBindingForm: Boolean = false
    private var currentCameraImageUri: Uri? = null
    private var isProcessingCameraImage: Boolean = false

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

    private val cameraConfirmationLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult

            val action = result.data?.getStringExtra(CameraReminderDraftContract.EXTRA_ACTION)
            val payload = result.data?.let(CameraReminderDraftContract::readPayload) ?: return@registerForActivityResult
            val draft = payload.toDraft()

            reminderViewModel.applyDraftToForm(draft, ReminderSource.CAMERA)
            currentSource = ReminderSource.CAMERA
            cameraInputCard.isVisible = true
            cameraScanStatusText.text = resolvePostConfirmationStatus(action, draft)
            guidePostConfirmationEditing(draft, action)
        }

    companion object {
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
        const val EXTRA_DEFAULT_SOURCE = "extra_default_source"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual_reminder)

        initViewModel()
        initViews()
        initCameraTools()
        setupRecurrenceOptions()
        setupListeners()
        observeState()
        observeEvents()
        loadInitialForm()
    }

    private fun initViewModel() {
        val database = ReminderDatabase.getDatabase(this)
        val repository = ReminderRepositoryImpl(database.reminderDao())

        val factory = ReminderViewModelFactory(
            context = applicationContext,
            saveReminderDraftUseCase = SaveReminderDraftUseCase(repository),
            getRemindersUseCase = GetRemindersUseCase(repository),
            getReminderByIdUseCase = GetReminderByIdUseCase(repository),
            deleteReminderUseCase = DeleteReminderUseCase(repository),
            updateReminderUseCase = UpdateReminderUseCase(repository)
        )

        reminderViewModel = ViewModelProvider(this, factory)[ReminderViewModel::class.java]
    }

    private fun initCameraTools() {
        cameraDraftExtractor = CameraReminderDraftExtractor(
            LocalImageTextRecognizer(applicationContext)
        )
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
        recurringEnabledSwitch = findViewById(R.id.switchRecurringEnabled)
        recurrenceInput = findViewById(R.id.inputRecurrenceType)
        recurrenceIntervalLayout = findViewById(R.id.layoutRecurrenceInterval)
        recurrenceIntervalInput = findViewById(R.id.inputRecurrenceInterval)
        recurrenceActiveSwitch = findViewById(R.id.switchRecurrenceActive)
        recurringContent = findViewById(R.id.layoutRecurringContent)
        weekdayChecks = mapOf(
            ReminderWeekday.MONDAY to findViewById(R.id.checkMonday),
            ReminderWeekday.TUESDAY to findViewById(R.id.checkTuesday),
            ReminderWeekday.WEDNESDAY to findViewById(R.id.checkWednesday),
            ReminderWeekday.THURSDAY to findViewById(R.id.checkThursday),
            ReminderWeekday.FRIDAY to findViewById(R.id.checkFriday),
            ReminderWeekday.SATURDAY to findViewById(R.id.checkSaturday),
            ReminderWeekday.SUNDAY to findViewById(R.id.checkSunday)
        )
    }

    private fun setupRecurrenceOptions() {
        val recurrenceLabels = RecurrenceOption.entries.map { getString(it.labelResId) }
        recurrenceInput.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_list_item_1,
                recurrenceLabels
            )
        )
        recurrenceInput.setText(getString(RecurrenceOption.DAILY.labelResId), false)
    }

    private fun setupListeners() {
        backButton.setOnClickListener {
            finish()
        }

        saveButton.setOnClickListener {
            saveReminder()
        }

        dateButton.setOnClickListener {
            showDatePicker()
        }

        timeButton.setOnClickListener {
            showTimePicker()
        }

        takePhotoButton.setOnClickListener {
            openCameraForReminder()
        }

        selectImageButton.setOnClickListener {
            selectImageLauncher.launch("image/*")
        }

        recurringEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isBindingForm) return@setOnCheckedChangeListener

            recurringContent.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
            recurrenceActiveSwitch.visibility = if (isChecked) android.view.View.VISIBLE else android.view.View.GONE
            recurrenceIntervalLayout.visibility = if (isChecked &&
                (selectedRecurrenceOption == RecurrenceOption.EVERY_X_DAYS ||
                        selectedRecurrenceOption == RecurrenceOption.EVERY_X_WEEKS ||
                        selectedRecurrenceOption == RecurrenceOption.EVERY_X_MONTHS)
            ) android.view.View.VISIBLE else android.view.View.GONE

            if (isChecked) {
                selectedRecurrenceOption = lastSelectedRecurrenceOption
                recurrenceInput.setText(
                    getString(selectedRecurrenceOption.labelResId),
                    false
                )
                if (recurrenceIntervalLayout.visibility == android.view.View.VISIBLE &&
                    recurrenceIntervalInput.text.isNullOrBlank()
                ) {
                    recurrenceIntervalInput.setText("1")
                }
            }
        }

        recurrenceInput.setOnItemClickListener { _, _, position, _ ->
            val option = RecurrenceOption.entries[position]
            selectedRecurrenceOption = option
            lastSelectedRecurrenceOption = option
            updateRecurrenceControls()
        }

        recurrenceActiveSwitch.setOnCheckedChangeListener { _, _ ->
            if (isBindingForm) return@setOnCheckedChangeListener
        }
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
                        Toast.makeText(this@ManualReminderActivity, event.message, Toast.LENGTH_SHORT).show()

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

        currentSource = defaultSource
        currentReminderId = reminderId

        if (reminderId > 0) {
            reminderViewModel.loadReminderForEditing(reminderId, defaultSource)
        } else {
            reminderViewModel.startManualReminderForm(defaultSource)
        }
    }

    private fun bindFormState(formState: com.luistureo.voicereminderapp.presentation.state.ReminderFormState) {
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
            currentReminderId != 0 -> getString(R.string.manual_reminder_screen_subtitle)
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

        val recurrence = formState.recurrence
        val hasRecurrence = recurrence != null
        recurringEnabledSwitch.isChecked = hasRecurrence
        recurringContent.visibility = if (hasRecurrence) android.view.View.VISIBLE else android.view.View.GONE
        recurrenceActiveSwitch.visibility = if (hasRecurrence) android.view.View.VISIBLE else android.view.View.GONE

        if (hasRecurrence) {
            selectedRecurrenceOption = recurrence.toOption()
            lastSelectedRecurrenceOption = selectedRecurrenceOption
            recurrenceInput.setText(getString(selectedRecurrenceOption.labelResId), false)
            recurrenceIntervalInput.setText(
                recurrence.interval.takeIf { it > 1 }?.toString().orEmpty()
            )
            recurrenceActiveSwitch.isChecked = recurrence.isActive
            bindWeekdays(recurrence.weekdays)
        } else {
            selectedRecurrenceOption = lastSelectedRecurrenceOption
            recurrenceInput.setText(getString(lastSelectedRecurrenceOption.labelResId), false)
            recurrenceIntervalInput.setText("")
            recurrenceActiveSwitch.isChecked = true
            bindWeekdays(emptySet())
        }

        updateRecurrenceControls()
        isBindingForm = false
    }

    private fun updateDateTimeButtons() {
        dateButton.text = if (selectedDate.isBlank()) {
            getString(R.string.reminder_select_date)
        } else {
            selectedDate
        }

        timeButton.text = if (selectedTime.isBlank()) {
            getString(R.string.reminder_select_time)
        } else {
            selectedTime
        }
    }

    private fun updateRecurrenceControls() {
        val isRecurring = recurringEnabledSwitch.isChecked
        recurringContent.visibility = if (isRecurring) android.view.View.VISIBLE else android.view.View.GONE
        recurrenceActiveSwitch.visibility = if (isRecurring) android.view.View.VISIBLE else android.view.View.GONE
        recurrenceIntervalLayout.visibility = if (isRecurring &&
            (selectedRecurrenceOption == RecurrenceOption.EVERY_X_DAYS ||
                    selectedRecurrenceOption == RecurrenceOption.EVERY_X_WEEKS ||
                    selectedRecurrenceOption == RecurrenceOption.EVERY_X_MONTHS)
        ) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun bindWeekdays(weekdays: Set<ReminderWeekday>) {
        weekdayChecks.forEach { (weekday, checkBox) ->
            checkBox.isChecked = weekday in weekdays
        }
    }

    private fun showDatePicker() {
        val dateFieldState = DateTimeFormStateResolver.resolveDateField(selectedDate)
        val parsedDate = dateFieldState.parts.takeIf { dateFieldState.canUsePrefill }
        val calendar = Calendar.getInstance().apply {
            if (parsedDate != null) {
                set(Calendar.YEAR, parsedDate.year)
                set(Calendar.MONTH, parsedDate.month - 1)
                set(Calendar.DAY_OF_MONTH, parsedDate.day)
            }
        }

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                selectedDate = DateTimeFormatterCore.formatDate(dayOfMonth, month + 1, year)
                updateDateTimeButtons()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePicker() {
        val timeFieldState = DateTimeFormStateResolver.resolveTimeField(selectedTime)
        val parsedTime = timeFieldState.parts.takeIf { timeFieldState.canUsePrefill }
        val calendar = Calendar.getInstance().apply {
            if (parsedTime != null) {
                set(Calendar.HOUR_OF_DAY, parsedTime.hour)
                set(Calendar.MINUTE, parsedTime.minute)
            }
        }

        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                selectedTime = DateTimeFormatterCore.formatTime(hourOfDay, minute)
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
        val draft = ReminderDraft(
            reminderId = currentReminderId,
            title = title,
            text = detail,
            date = selectedDateValue,
            time = selectedTimeValue,
            isUrgent = urgentSwitch.isChecked,
            source = currentSource,
            recurrence = null
        )
        val formState = ReminderDraftFormStateResolver.resolve(draft)

        if (formState.hasMissingText) {
            Toast.makeText(this, R.string.reminder_error_detail_required, Toast.LENGTH_SHORT).show()
            return
        }

        if (formState.hasMissingDate) {
            Toast.makeText(this, R.string.reminder_error_date_required, Toast.LENGTH_SHORT).show()
            return
        }

        if (formState.hasMissingTime) {
            Toast.makeText(this, R.string.reminder_error_time_required, Toast.LENGTH_SHORT).show()
            return
        }

        if (recurringEnabledSwitch.isChecked &&
            selectedRecurrenceOption == RecurrenceOption.SPECIFIC_WEEKDAYS &&
            weekdayChecks.filterValues { it.isChecked }.isEmpty()
        ) {
            Toast.makeText(this, R.string.reminder_error_weekdays_required, Toast.LENGTH_SHORT).show()
            return
        }

        val recurrence = if (recurringEnabledSwitch.isChecked) {
            buildRecurrence()
        } else {
            null
        }

        reminderViewModel.saveReminderDraft(draft.copy(recurrence = recurrence))
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
        val tempFile = createCameraImageFile()
        val authority = "${applicationContext.packageName}.fileprovider"
        val imageUri = FileProvider.getUriForFile(this, authority, tempFile)
        currentCameraImageUri = imageUri
        takePictureLauncher.launch(imageUri)
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
                launchCameraConfirmation(scanResult, imageUri)
            }.onFailure {
                isProcessingCameraImage = false
                cameraScanStatusText.text = getString(R.string.camera_reminder_scan_error)
                updateCameraScanUiState()
            }
        }
    }

    private fun launchCameraConfirmation(
        scanResult: CameraReminderScanResult,
        imageUri: Uri
    ) {
        isProcessingCameraImage = false

        val confirmationIntent = Intent(this, CameraReminderConfirmationActivity::class.java).apply {
            putExtras(
                CameraReminderDraftContract.createIntent(
                    title = scanResult.draft.title,
                    text = scanResult.draft.text,
                    date = scanResult.draft.date,
                    time = scanResult.draft.time,
                    recognizedText = scanResult.recognizedText,
                    imageUri = imageUri,
                    hasDetectedDate = scanResult.hasDetectedDate,
                    hasDetectedTime = scanResult.hasDetectedTime
                ).extras ?: Bundle.EMPTY
            )
        }

        cameraConfirmationLauncher.launch(confirmationIntent)
        cameraScanStatusText.text = buildCameraScanStatusMessage(scanResult)
        updateCameraScanUiState()
    }

    private fun buildCameraScanStatusMessage(scanResult: CameraReminderScanResult): String {
        return when {
            scanResult.hasDetectedReminderText && scanResult.hasDetectedDate && scanResult.hasDetectedTime ->
                getString(R.string.camera_reminder_scan_success_full)

            scanResult.hasDetectedReminderText && (scanResult.hasDetectedDate || scanResult.hasDetectedTime) ->
                getString(R.string.camera_reminder_scan_success_partial_schedule)

            scanResult.hasDetectedReminderText ->
                getString(R.string.camera_reminder_scan_success_text_only)

            scanResult.hasDetectedDate || scanResult.hasDetectedTime ->
                getString(R.string.camera_reminder_scan_success_schedule_only)

            else -> getString(R.string.camera_reminder_scan_error)
        }
    }

    private fun resolvePostConfirmationStatus(
        action: String?,
        draft: ReminderDraft
    ): String {
        val formState = ReminderDraftFormStateResolver.resolve(draft)

        return when {
            formState.hasMissingDate && formState.hasMissingTime ->
                getString(R.string.camera_post_edit_missing_date_time)

            formState.hasMissingDate ->
                getString(R.string.camera_post_edit_missing_date)

            formState.hasMissingTime ->
                getString(R.string.camera_post_edit_missing_time)

            action == CameraReminderDraftContract.ACTION_EDIT ->
                getString(R.string.camera_confirmation_result_editing)

            else ->
                getString(R.string.camera_post_edit_ready_to_save)
        }
    }

    private fun guidePostConfirmationEditing(
        draft: ReminderDraft,
        action: String?
    ) {
        val formState = ReminderDraftFormStateResolver.resolve(draft)

        // Prioriza el primer dato faltante para reducir errores antes de guardar.
        when {
            formState.hasMissingText -> {
                detailInput.requestFocus()
            }

            formState.hasMissingDate -> {
                dateButton.requestFocus()
            }

            formState.hasMissingTime -> {
                timeButton.requestFocus()
            }

            action == CameraReminderDraftContract.ACTION_EDIT -> {
                detailInput.requestFocus()
            }

            else -> {
                saveButton.requestFocus()
            }
        }
    }

    private fun updateCameraScanUiState() {
        cameraScanProgress.isVisible = isProcessingCameraImage
        takePhotoButton.isEnabled = !isProcessingCameraImage
        selectImageButton.isEnabled = !isProcessingCameraImage

        if (isProcessingCameraImage) {
            cameraScanStatusText.text = getString(R.string.camera_reminder_scan_processing)
        } else if (cameraScanStatusText.text.isNullOrBlank()) {
            cameraScanStatusText.text = getString(R.string.camera_reminder_scan_idle)
        }
    }

    override fun onDestroy() {
        cameraDraftExtractor.close()
        super.onDestroy()
    }

    private fun buildRecurrence(): ReminderRecurrence? {
        val interval = recurrenceIntervalInput.text?.toString()?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val weekdays = weekdayChecks.filterValues { it.isChecked }.keys.toSet()

        return when (selectedRecurrenceOption) {
            RecurrenceOption.DAILY -> ReminderRecurrence(ReminderRecurrenceUnit.DAY, 1, isActive = recurrenceActiveSwitch.isChecked)
            RecurrenceOption.WEEKLY -> ReminderRecurrence(ReminderRecurrenceUnit.WEEK, 1, isActive = recurrenceActiveSwitch.isChecked)
            RecurrenceOption.MONTHLY -> ReminderRecurrence(ReminderRecurrenceUnit.MONTH, 1, isActive = recurrenceActiveSwitch.isChecked)
            RecurrenceOption.YEARLY -> ReminderRecurrence(ReminderRecurrenceUnit.YEAR, 1, isActive = recurrenceActiveSwitch.isChecked)
            RecurrenceOption.SPECIFIC_WEEKDAYS -> ReminderRecurrence(
                unit = ReminderRecurrenceUnit.WEEK,
                interval = 1,
                weekdays = weekdays,
                isActive = recurrenceActiveSwitch.isChecked
            )
            RecurrenceOption.EVERY_X_DAYS -> ReminderRecurrence(
                ReminderRecurrenceUnit.DAY,
                interval,
                isActive = recurrenceActiveSwitch.isChecked
            )
            RecurrenceOption.EVERY_X_WEEKS -> ReminderRecurrence(
                ReminderRecurrenceUnit.WEEK,
                interval,
                isActive = recurrenceActiveSwitch.isChecked
            )
            RecurrenceOption.EVERY_X_MONTHS -> ReminderRecurrence(
                ReminderRecurrenceUnit.MONTH,
                interval,
                isActive = recurrenceActiveSwitch.isChecked
            )
        }
    }

    private fun ReminderRecurrence.toOption(): RecurrenceOption {
        return when {
            unit == ReminderRecurrenceUnit.DAY && interval == 1 -> RecurrenceOption.DAILY
            unit == ReminderRecurrenceUnit.WEEK && interval == 1 && weekdays.isEmpty() -> RecurrenceOption.WEEKLY
            unit == ReminderRecurrenceUnit.MONTH && interval == 1 -> RecurrenceOption.MONTHLY
            unit == ReminderRecurrenceUnit.YEAR && interval == 1 -> RecurrenceOption.YEARLY
            unit == ReminderRecurrenceUnit.WEEK && weekdays.isNotEmpty() -> RecurrenceOption.SPECIFIC_WEEKDAYS
            unit == ReminderRecurrenceUnit.DAY -> RecurrenceOption.EVERY_X_DAYS
            unit == ReminderRecurrenceUnit.WEEK -> RecurrenceOption.EVERY_X_WEEKS
            unit == ReminderRecurrenceUnit.MONTH -> RecurrenceOption.EVERY_X_MONTHS
            else -> RecurrenceOption.DAILY
        }
    }
}
