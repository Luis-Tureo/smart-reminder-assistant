package com.luistureo.voicereminderapp.presentation.loan

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.loan.ClpFormatter
import com.luistureo.voicereminderapp.core.loan.LoanShareMessageBuilder
import com.luistureo.voicereminderapp.core.utils.DateTimeFormatter
import com.luistureo.voicereminderapp.domain.loan.model.Loan
import com.luistureo.voicereminderapp.domain.loan.model.LoanPayment
import com.luistureo.voicereminderapp.presentation.loan.adapter.LoanInstallmentAdapter
import com.luistureo.voicereminderapp.presentation.loan.adapter.LoanPaymentAdapter
import com.luistureo.voicereminderapp.presentation.loan.viewmodel.LoanViewModel
import com.luistureo.voicereminderapp.presentation.loan.viewmodel.LoanViewModelFactory
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneId

class LoanDetailActivity : ComponentActivity() {

    private lateinit var viewModel: LoanViewModel
    private lateinit var paymentAdapter: LoanPaymentAdapter
    private lateinit var installmentAdapter: LoanInstallmentAdapter

    private lateinit var personText: TextView
    private lateinit var typeText: TextView
    private lateinit var statusText: TextView
    private lateinit var principalText: TextView
    private lateinit var interestText: TextView
    private lateinit var totalText: TextView
    private lateinit var paidText: TextView
    private lateinit var remainingText: TextView
    private lateinit var datesText: TextView
    private lateinit var reasonText: TextView
    private lateinit var notesText: TextView
    private lateinit var attachmentPreview: ImageView
    private lateinit var paymentsEmptyText: TextView
    private lateinit var installmentsTitle: TextView
    private lateinit var installmentsRecycler: RecyclerView

    private var loanId: Int = 0
    private var selectedPaymentDate: LocalDate = LocalDate.now()
    private var paymentAttachmentUri: Uri? = null
    private var paymentAttachmentLabel: TextView? = null
    private val zoneId = ZoneId.systemDefault()

    private val paymentAttachmentLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                paymentAttachmentUri = it
                paymentAttachmentLabel?.text = getString(R.string.loan_attachment_selected)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loan_detail)

        loanId = intent.getIntExtra(EXTRA_LOAN_ID, 0)
        if (loanId <= 0) {
            finish()
            return
        }

        viewModel = ViewModelProvider(
            this,
            LoanViewModelFactory(applicationContext)
        )[LoanViewModel::class.java]

        initViews()
        setupRecycler()
        setupActions()
        observeState()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadLoan(loanId)
    }

    private fun initViews() {
        findViewById<ImageButton>(R.id.btnBackLoanDetail).setOnClickListener { finish() }
        personText = findViewById(R.id.tvLoanDetailPerson)
        typeText = findViewById(R.id.tvLoanDetailType)
        statusText = findViewById(R.id.tvLoanDetailStatus)
        principalText = findViewById(R.id.tvLoanDetailPrincipal)
        interestText = findViewById(R.id.tvLoanDetailInterest)
        totalText = findViewById(R.id.tvLoanDetailTotal)
        paidText = findViewById(R.id.tvLoanDetailPaid)
        remainingText = findViewById(R.id.tvLoanDetailRemaining)
        datesText = findViewById(R.id.tvLoanDetailDates)
        reasonText = findViewById(R.id.tvLoanDetailReason)
        notesText = findViewById(R.id.tvLoanDetailNotes)
        attachmentPreview = findViewById(R.id.imageLoanDetailAttachment)
        paymentsEmptyText = findViewById(R.id.tvLoanPaymentsEmpty)
        installmentsTitle = findViewById(R.id.tvLoanInstallmentsTitle)
        installmentsRecycler = findViewById(R.id.recyclerLoanInstallments)
    }

    private fun setupRecycler() {
        paymentAdapter = LoanPaymentAdapter()
        installmentAdapter = LoanInstallmentAdapter()
        findViewById<RecyclerView>(R.id.recyclerLoanPayments).apply {
            layoutManager = LinearLayoutManager(this@LoanDetailActivity)
            adapter = paymentAdapter
        }
        installmentsRecycler.apply {
            layoutManager = LinearLayoutManager(this@LoanDetailActivity)
            adapter = installmentAdapter
        }
    }

    private fun setupActions() {
        findViewById<MaterialButton>(R.id.btnLoanAddPayment).setOnClickListener {
            viewModel.uiState.value.selectedLoan?.let(::showPaymentDialog)
        }
        findViewById<MaterialButton>(R.id.btnLoanEdit).setOnClickListener {
            startActivity(
                Intent(this, LoanEditorActivity::class.java)
                    .putExtra(LoanEditorActivity.EXTRA_LOAN_ID, loanId)
            )
        }
        findViewById<MaterialButton>(R.id.btnLoanMarkPaid).setOnClickListener {
            viewModel.markFullyPaid(loanId)
        }
        findViewById<MaterialButton>(R.id.btnLoanDelete).setOnClickListener {
            confirmDelete()
        }
        findViewById<MaterialButton>(R.id.btnLoanShare).setOnClickListener {
            viewModel.uiState.value.selectedLoan?.let(::showEditableShareMessage)
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    state.selectedLoan?.let(::bindLoan)
                    state.message?.let {
                        Toast.makeText(this@LoanDetailActivity, it, Toast.LENGTH_SHORT).show()
                        viewModel.consumeMessage()
                    }
                }
            }
        }
    }

    private fun bindLoan(loan: Loan) {
        personText.text = loan.personName
        typeText.text = loan.type.label
        statusText.text = loan.status.label
        principalText.text = ClpFormatter.format(loan.principalAmountClp)
        interestText.text = if (loan.interestEnabled) {
            getString(R.string.loan_detail_interest_value, loan.interestPercentage)
        } else {
            getString(R.string.loan_interest_disabled)
        }
        totalText.text = ClpFormatter.format(loan.totalExpectedAmountClp)
        paidText.text = ClpFormatter.format(loan.paidAmountClp)
        remainingText.text = ClpFormatter.format(loan.remainingAmountClp)
        datesText.text = getString(
            R.string.loan_detail_dates,
            DateTimeFormatter.formatDateFromEpoch(loan.loanDateEpochMillis),
            DateTimeFormatter.formatDateFromEpoch(loan.dueDateEpochMillis)
        )
        reasonText.text = loan.reason
        notesText.text = loan.notes.orEmpty()
        notesText.isVisible = !loan.notes.isNullOrBlank()
        attachmentPreview.isVisible = loan.attachmentUri != null
        loan.attachmentUri?.let { attachmentPreview.setImageURI(Uri.parse(it)) }
        paymentAdapter.submitList(loan.payments)
        paymentsEmptyText.isVisible = loan.payments.isEmpty()
        installmentAdapter.submitList(loan.installments)
        installmentsTitle.isVisible = loan.installments.isNotEmpty()
        installmentsRecycler.isVisible = loan.installments.isNotEmpty()
    }

    private fun showPaymentDialog(loan: Loan) {
        selectedPaymentDate = LocalDate.now()
        paymentAttachmentUri = null
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_loan_payment, null)
        val amountInput = view.findViewById<TextInputEditText>(R.id.inputPaymentAmount)
        val noteInput = view.findViewById<TextInputEditText>(R.id.inputPaymentNote)
        val dateButton = view.findViewById<MaterialButton>(R.id.btnPaymentDate)
        val attachmentButton = view.findViewById<MaterialButton>(R.id.btnPaymentAttachment)
        paymentAttachmentLabel = view.findViewById(R.id.tvPaymentAttachmentLabel)

        amountInput.setText(loan.remainingAmountClp.toString())
        fun updateDateButton() {
            dateButton.text = DateTimeFormatter.formatDate(
                selectedPaymentDate.dayOfMonth,
                selectedPaymentDate.monthValue,
                selectedPaymentDate.year
            )
        }
        updateDateButton()

        dateButton.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    selectedPaymentDate = LocalDate.of(year, month + 1, dayOfMonth)
                    updateDateButton()
                },
                selectedPaymentDate.year,
                selectedPaymentDate.monthValue - 1,
                selectedPaymentDate.dayOfMonth
            ).show()
        }
        attachmentButton.setOnClickListener {
            paymentAttachmentLauncher.launch(arrayOf("image/*"))
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.loan_add_payment)
            .setView(view)
            .setNegativeButton(R.string.reminder_cancel_action, null)
            .setPositiveButton(R.string.loan_save_payment, null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val amount = ClpFormatter.parse(amountInput.text?.toString().orEmpty())
                        if (amount == null || amount <= 0L) {
                            amountInput.error = getString(R.string.loan_error_amount_required)
                            return@setOnClickListener
                        }
                        viewModel.addPayment(
                            loan.id,
                            LoanPayment(
                                loanId = loan.id,
                                paidAmountClp = amount,
                                paymentDateEpochMillis = selectedPaymentDate
                                    .atStartOfDay(zoneId)
                                    .toInstant()
                                    .toEpochMilli(),
                                note = noteInput.text?.toString()?.trim()?.takeIf { it.isNotBlank() },
                                attachmentUri = paymentAttachmentUri?.toString()
                            )
                        )
                        dismiss()
                    }
                }
            }
            .show()
    }

    private fun showEditableShareMessage(loan: Loan) {
        val input = EditText(this).apply {
            setText(LoanShareMessageBuilder.build(loan))
            minLines = 4
            setSelectAllOnFocus(false)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.loan_share_message_title)
            .setView(input)
            .setNegativeButton(R.string.reminder_cancel_action, null)
            .setPositiveButton(R.string.loan_share_confirm) { _, _ ->
                shareMessage(loan, input.text?.toString().orEmpty())
            }
            .show()
    }

    private fun shareMessage(loan: Loan, message: String) {
        if (message.isBlank()) return

        val phoneDigits = loan.phoneOrContact
            ?.filter { it.isDigit() }
            ?.takeIf { it.isNotBlank() }

        if (phoneDigits != null) {
            val encodedMessage = URLEncoder.encode(message, "UTF-8")
            val whatsappIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/$phoneDigits?text=$encodedMessage")
            ).setPackage("com.whatsapp")

            runCatching { startActivity(whatsappIntent) }
                .onSuccess { return }
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.loan_share_chooser)))
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.loan_delete_title)
            .setMessage(R.string.loan_delete_message)
            .setNegativeButton(R.string.reminder_cancel_action, null)
            .setPositiveButton(R.string.loan_delete_confirm) { _, _ ->
                viewModel.deleteLoan(loanId) { finish() }
            }
            .show()
    }

    companion object {
        const val EXTRA_LOAN_ID = "extra_loan_id"
    }
}
