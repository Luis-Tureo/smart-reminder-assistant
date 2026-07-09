package com.luistureo.voicereminderapp.presentation.loan

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
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
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.loan.ClpFormatter
import com.luistureo.voicereminderapp.core.loan.LoanCalculator
import com.luistureo.voicereminderapp.core.utils.DateTimeFormatter
import com.luistureo.voicereminderapp.domain.loan.model.Loan
import com.luistureo.voicereminderapp.domain.loan.model.LoanDraft
import com.luistureo.voicereminderapp.domain.loan.model.LoanPaymentMode
import com.luistureo.voicereminderapp.domain.loan.model.LoanType
import com.luistureo.voicereminderapp.presentation.loan.viewmodel.LoanViewModel
import com.luistureo.voicereminderapp.presentation.loan.viewmodel.LoanViewModelFactory
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class LoanEditorActivity : ComponentActivity() {

    private lateinit var viewModel: LoanViewModel
    private lateinit var titleText: TextView
    private lateinit var saveButton: MaterialButton
    private lateinit var typeLentButton: MaterialButton
    private lateinit var typeOweButton: MaterialButton
    private lateinit var personLayout: TextInputLayout
    private lateinit var phoneLayout: TextInputLayout
    private lateinit var amountLayout: TextInputLayout
    private lateinit var reasonLayout: TextInputLayout
    private lateinit var installmentLayout: TextInputLayout
    private lateinit var interestLayout: TextInputLayout
    private lateinit var personInput: TextInputEditText
    private lateinit var phoneInput: TextInputEditText
    private lateinit var amountInput: TextInputEditText
    private lateinit var reasonInput: TextInputEditText
    private lateinit var notesInput: TextInputEditText
    private lateinit var installmentInput: TextInputEditText
    private lateinit var interestInput: TextInputEditText
    private lateinit var repeatInput: TextInputEditText
    private lateinit var loanDateButton: MaterialButton
    private lateinit var dueDateButton: MaterialButton
    private lateinit var singlePaymentButton: MaterialButton
    private lateinit var installmentsButton: MaterialButton
    private lateinit var installmentsContainer: LinearLayout
    private lateinit var interestSwitch: SwitchMaterial
    private lateinit var interestFields: LinearLayout
    private lateinit var reminderSameDay: CheckBox
    private lateinit var reminderOneDay: CheckBox
    private lateinit var reminderThreeDays: CheckBox
    private lateinit var reminderCustom: CheckBox
    private lateinit var customDateButton: MaterialButton
    private lateinit var customTimeButton: MaterialButton
    private lateinit var attachmentPreview: ImageView
    private lateinit var attachmentLabel: TextView
    private lateinit var removeAttachmentButton: MaterialButton

    private var loanId: Int = 0
    private var selectedType: LoanType = LoanType.MONEY_LENT_TO_ME
    private var selectedPaymentMode: LoanPaymentMode = LoanPaymentMode.SINGLE
    private var selectedLoanDate: LocalDate = LocalDate.now()
    private var selectedDueDate: LocalDate = LocalDate.now()
    private var selectedCustomDate: LocalDate? = null
    private var selectedCustomTime: LocalTime? = null
    private var attachmentUri: Uri? = null
    private var cameraUri: Uri? = null
    private var hasBoundExistingLoan = false

    private val zoneId = ZoneId.systemDefault()

    private val openAttachmentLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                setAttachment(it)
            }
        }

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = cameraUri
            if (success && uri != null) {
                setAttachment(uri)
            }
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) openCamera() else Toast.makeText(
                this,
                R.string.camera_reminder_camera_permission_denied,
                Toast.LENGTH_SHORT
            ).show()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loan_editor)

        viewModel = ViewModelProvider(
            this,
            LoanViewModelFactory(applicationContext)
        )[LoanViewModel::class.java]

        loanId = intent.getIntExtra(EXTRA_LOAN_ID, 0)
        initViews()
        setupListeners()
        updateAllToggleStates()
        updateDateButtons()
        observeState()

        if (loanId > 0) {
            viewModel.loadLoan(loanId)
        }
    }

    private fun initViews() {
        findViewById<ImageButton>(R.id.btnBackLoanEditor).setOnClickListener { finish() }
        titleText = findViewById(R.id.tvLoanEditorTitle)
        saveButton = findViewById(R.id.btnSaveLoan)
        typeLentButton = findViewById(R.id.btnLoanTypeLent)
        typeOweButton = findViewById(R.id.btnLoanTypeOwe)
        personLayout = findViewById(R.id.layoutLoanPerson)
        phoneLayout = findViewById(R.id.layoutLoanPhone)
        amountLayout = findViewById(R.id.layoutLoanAmount)
        reasonLayout = findViewById(R.id.layoutLoanReason)
        installmentLayout = findViewById(R.id.layoutLoanInstallments)
        interestLayout = findViewById(R.id.layoutLoanInterest)
        personInput = findViewById(R.id.inputLoanPerson)
        phoneInput = findViewById(R.id.inputLoanPhone)
        amountInput = findViewById(R.id.inputLoanAmount)
        reasonInput = findViewById(R.id.inputLoanReason)
        notesInput = findViewById(R.id.inputLoanNotes)
        installmentInput = findViewById(R.id.inputLoanInstallments)
        interestInput = findViewById(R.id.inputLoanInterest)
        repeatInput = findViewById(R.id.inputLoanRepeatDays)
        loanDateButton = findViewById(R.id.btnLoanDate)
        dueDateButton = findViewById(R.id.btnLoanDueDate)
        singlePaymentButton = findViewById(R.id.btnLoanSinglePayment)
        installmentsButton = findViewById(R.id.btnLoanInstallments)
        installmentsContainer = findViewById(R.id.containerInstallmentFields)
        interestSwitch = findViewById(R.id.switchLoanInterest)
        interestFields = findViewById(R.id.containerInterestFields)
        reminderSameDay = findViewById(R.id.checkLoanReminderSameDay)
        reminderOneDay = findViewById(R.id.checkLoanReminderOneDay)
        reminderThreeDays = findViewById(R.id.checkLoanReminderThreeDays)
        reminderCustom = findViewById(R.id.checkLoanReminderCustom)
        customDateButton = findViewById(R.id.btnLoanCustomReminderDate)
        customTimeButton = findViewById(R.id.btnLoanCustomReminderTime)
        attachmentPreview = findViewById(R.id.imageLoanAttachmentPreview)
        attachmentLabel = findViewById(R.id.tvLoanAttachmentLabel)
        removeAttachmentButton = findViewById(R.id.btnRemoveLoanAttachment)
    }

    private fun setupListeners() {
        typeLentButton.setOnClickListener {
            selectedType = LoanType.MONEY_LENT_TO_ME
            updateTypeButtons()
        }
        typeOweButton.setOnClickListener {
            selectedType = LoanType.MONEY_I_OWE
            updateTypeButtons()
        }
        singlePaymentButton.setOnClickListener {
            selectedPaymentMode = LoanPaymentMode.SINGLE
            updatePaymentModeButtons()
        }
        installmentsButton.setOnClickListener {
            selectedPaymentMode = LoanPaymentMode.INSTALLMENTS
            updatePaymentModeButtons()
        }
        loanDateButton.setOnClickListener {
            showDatePicker(selectedLoanDate) { selectedLoanDate = it; updateDateButtons() }
        }
        dueDateButton.setOnClickListener {
            showDatePicker(selectedDueDate) { selectedDueDate = it; updateDateButtons() }
        }
        interestSwitch.setOnCheckedChangeListener { _, _ -> updateInterestVisibility() }
        findViewById<MaterialButton>(R.id.btnInterestZero).setOnClickListener { interestInput.setText("0") }
        findViewById<MaterialButton>(R.id.btnInterestOne).setOnClickListener { interestInput.setText("1") }
        findViewById<MaterialButton>(R.id.btnInterestTwo).setOnClickListener { interestInput.setText("2") }
        reminderCustom.setOnCheckedChangeListener { _, isChecked ->
            customDateButton.isEnabled = isChecked
            customTimeButton.isEnabled = isChecked
        }
        customDateButton.setOnClickListener {
            showDatePicker(selectedCustomDate ?: selectedDueDate) {
                selectedCustomDate = it
                updateDateButtons()
            }
        }
        customTimeButton.setOnClickListener {
            showTimePicker(selectedCustomTime ?: LocalTime.of(9, 0)) {
                selectedCustomTime = it
                updateDateButtons()
            }
        }
        findViewById<MaterialButton>(R.id.btnSelectLoanAttachment).setOnClickListener {
            openAttachmentLauncher.launch(arrayOf("image/*"))
        }
        findViewById<MaterialButton>(R.id.btnCaptureLoanAttachment).setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        removeAttachmentButton.setOnClickListener { setAttachment(null) }
        saveButton.setOnClickListener { saveLoan() }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    state.message?.let {
                        Toast.makeText(this@LoanEditorActivity, it, Toast.LENGTH_SHORT).show()
                        viewModel.consumeMessage()
                    }

                    val loan = state.selectedLoan
                    if (loan != null && loan.id == loanId && !hasBoundExistingLoan) {
                        bindLoan(loan)
                    }
                }
            }
        }
    }

    private fun bindLoan(loan: Loan) {
        hasBoundExistingLoan = true
        titleText.text = getString(R.string.loan_editor_edit_title)
        selectedType = loan.type
        selectedPaymentMode = loan.paymentMode
        selectedLoanDate = DateTimeFormatter.toLocalDate(loan.loanDateEpochMillis)
        selectedDueDate = DateTimeFormatter.toLocalDate(loan.dueDateEpochMillis)
        selectedCustomDate = loan.customReminderAtEpochMillis?.let(DateTimeFormatter::toLocalDate)
        selectedCustomTime = loan.customReminderAtEpochMillis?.let(DateTimeFormatter::toLocalTime)
        personInput.setText(loan.personName)
        phoneInput.setText(loan.phoneOrContact.orEmpty())
        amountInput.setText(loan.principalAmountClp.toString())
        reasonInput.setText(loan.reason)
        notesInput.setText(loan.notes.orEmpty())
        installmentInput.setText(loan.installmentCount.takeIf { it > 0 }?.toString().orEmpty())
        interestSwitch.isChecked = loan.interestEnabled
        interestInput.setText(loan.interestPercentage.takeIf { loan.interestEnabled }?.toString().orEmpty())
        reminderSameDay.isChecked = loan.reminderSameDay
        reminderOneDay.isChecked = loan.reminderOneDayBefore
        reminderThreeDays.isChecked = loan.reminderThreeDaysBefore
        reminderCustom.isChecked = loan.customReminderAtEpochMillis != null
        repeatInput.setText(loan.repeatAfterDueEveryDays?.toString().orEmpty())
        setAttachment(loan.attachmentUri?.let(Uri::parse))
        updateAllToggleStates()
        updateDateButtons()
    }

    private fun saveLoan() {
        val draft = buildDraftOrShowErrors() ?: return
        viewModel.saveLoan(draft) {
            finish()
        }
    }

    private fun buildDraftOrShowErrors(): LoanDraft? {
        clearErrors()

        val person = personInput.text?.toString()?.trim().orEmpty()
        val amount = ClpFormatter.parse(amountInput.text?.toString().orEmpty())
        val reason = reasonInput.text?.toString()?.trim().orEmpty()
        val installmentCount = installmentInput.text?.toString()?.trim()?.toIntOrNull()
        val interestPercent = interestInput.text?.toString()
            ?.replace(",", ".")
            ?.trim()
            ?.toDoubleOrNull()
            ?: 0.0
        val repeatDays = repeatInput.text?.toString()?.trim()?.toIntOrNull()
        val customReminderAt = if (reminderCustom.isChecked) {
            val date = selectedCustomDate
            val time = selectedCustomTime
            if (date == null || time == null) null else date.atTime(time)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
        } else {
            null
        }

        var hasError = false
        if (person.isBlank()) {
            personLayout.error = getString(R.string.loan_error_person_required)
            hasError = true
        }
        if (amount == null || amount <= 0L) {
            amountLayout.error = getString(R.string.loan_error_amount_required)
            hasError = true
        }
        if (reason.isBlank()) {
            reasonLayout.error = getString(R.string.loan_error_reason_required)
            hasError = true
        }
        if (selectedDueDate.isBefore(selectedLoanDate)) {
            Toast.makeText(this, R.string.loan_error_due_before_loan, Toast.LENGTH_SHORT).show()
            hasError = true
        }
        if (selectedPaymentMode == LoanPaymentMode.INSTALLMENTS && (installmentCount == null || installmentCount <= 1)) {
            installmentLayout.error = getString(R.string.loan_error_installments_required)
            hasError = true
        }
        if (interestSwitch.isChecked && interestPercent < 0.0) {
            interestLayout.error = getString(R.string.loan_error_interest_invalid)
            hasError = true
        }
        if (reminderCustom.isChecked && customReminderAt == null) {
            Toast.makeText(this, R.string.loan_error_custom_reminder_required, Toast.LENGTH_SHORT).show()
            hasError = true
        }
        if (hasError || amount == null) return null

        return LoanDraft(
            id = loanId,
            type = selectedType,
            personName = person,
            phoneOrContact = phoneInput.text?.toString()?.trim()?.takeIf { it.isNotBlank() },
            principalAmountClp = amount,
            loanDateEpochMillis = selectedLoanDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            dueDateEpochMillis = selectedDueDate.atStartOfDay(zoneId).toInstant().toEpochMilli(),
            reason = reason,
            attachmentUri = attachmentUri?.toString(),
            paymentMode = selectedPaymentMode,
            installmentCount = if (selectedPaymentMode == LoanPaymentMode.INSTALLMENTS) {
                installmentCount ?: 0
            } else {
                0
            },
            interestEnabled = interestSwitch.isChecked,
            interestPercentage = if (interestSwitch.isChecked) interestPercent else 0.0,
            notes = notesInput.text?.toString()?.trim()?.takeIf { it.isNotBlank() },
            reminderSameDay = reminderSameDay.isChecked,
            reminderOneDayBefore = reminderOneDay.isChecked,
            reminderThreeDaysBefore = reminderThreeDays.isChecked,
            customReminderAtEpochMillis = customReminderAt,
            repeatAfterDueEveryDays = repeatDays?.takeIf { it > 0 }
        )
    }

    private fun clearErrors() {
        listOf(personLayout, phoneLayout, amountLayout, reasonLayout, installmentLayout, interestLayout)
            .forEach { it.error = null }
    }

    private fun updateAllToggleStates() {
        updateTypeButtons()
        updatePaymentModeButtons()
        updateInterestVisibility()
        customDateButton.isEnabled = reminderCustom.isChecked
        customTimeButton.isEnabled = reminderCustom.isChecked
    }

    private fun updateTypeButtons() {
        typeLentButton.isChecked = selectedType == LoanType.MONEY_LENT_TO_ME
        typeOweButton.isChecked = selectedType == LoanType.MONEY_I_OWE
    }

    private fun updatePaymentModeButtons() {
        singlePaymentButton.isChecked = selectedPaymentMode == LoanPaymentMode.SINGLE
        installmentsButton.isChecked = selectedPaymentMode == LoanPaymentMode.INSTALLMENTS
        installmentsContainer.isVisible = selectedPaymentMode == LoanPaymentMode.INSTALLMENTS
    }

    private fun updateInterestVisibility() {
        interestFields.isVisible = interestSwitch.isChecked
        if (interestSwitch.isChecked && interestInput.text.isNullOrBlank()) {
            interestInput.setText(LoanCalculator.interestPresets.first().toInt().toString())
        }
    }

    private fun updateDateButtons() {
        loanDateButton.text = DateTimeFormatter.formatDate(
            selectedLoanDate.dayOfMonth,
            selectedLoanDate.monthValue,
            selectedLoanDate.year
        )
        dueDateButton.text = DateTimeFormatter.formatDate(
            selectedDueDate.dayOfMonth,
            selectedDueDate.monthValue,
            selectedDueDate.year
        )
        customDateButton.text = selectedCustomDate?.let {
            DateTimeFormatter.formatDate(it.dayOfMonth, it.monthValue, it.year)
        } ?: getString(R.string.loan_custom_reminder_date)
        customTimeButton.text = selectedCustomTime?.let {
            DateTimeFormatter.formatTime(it.hour, it.minute)
        } ?: getString(R.string.loan_custom_reminder_time)
    }

    private fun showDatePicker(initialDate: LocalDate, onSelected: (LocalDate) -> Unit) {
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth -> onSelected(LocalDate.of(year, month + 1, dayOfMonth)) },
            initialDate.year,
            initialDate.monthValue - 1,
            initialDate.dayOfMonth
        ).show()
    }

    private fun showTimePicker(initialTime: LocalTime, onSelected: (LocalTime) -> Unit) {
        TimePickerDialog(
            this,
            { _, hourOfDay, minute -> onSelected(LocalTime.of(hourOfDay, minute)) },
            initialTime.hour,
            initialTime.minute,
            true
        ).show()
    }

    private fun setAttachment(uri: Uri?) {
        attachmentUri = uri
        attachmentPreview.isVisible = uri != null
        removeAttachmentButton.isVisible = uri != null
        attachmentLabel.text = if (uri != null) {
            getString(R.string.loan_attachment_selected)
        } else {
            getString(R.string.loan_attachment_empty)
        }
        if (uri != null) attachmentPreview.setImageURI(uri)
    }

    private fun openCamera() {
        runCatching {
            val directory = File(cacheDir, "loan_attachments").apply { mkdirs() }
            val file = File.createTempFile("loan_attachment_", ".jpg", directory)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            cameraUri = uri
            takePictureLauncher.launch(uri)
        }.onFailure {
            Toast.makeText(this, R.string.camera_reminder_capture_failed, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val EXTRA_LOAN_ID = "extra_loan_id"
    }
}
