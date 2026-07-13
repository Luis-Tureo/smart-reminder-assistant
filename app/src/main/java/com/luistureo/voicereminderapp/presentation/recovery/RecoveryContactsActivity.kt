package com.luistureo.voicereminderapp.presentation.recovery

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.Spinner
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
import com.google.android.material.textfield.TextInputLayout
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryContactAction
import com.luistureo.voicereminderapp.domain.recovery.model.RecoverySupportContact
import com.luistureo.voicereminderapp.presentation.recovery.adapter.RecoveryContactAdapter
import kotlinx.coroutines.launch

class RecoveryContactsActivity : ComponentActivity() {
    private lateinit var viewModel: RecoveryViewModel
    private lateinit var adapter: RecoveryContactAdapter
    private lateinit var actionSpinner: Spinner
    private val goalId by lazy { intent.getIntExtra(RecoveryDashboardActivity.EXTRA_GOAL_ID, 0) }

    private val contactPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> result.data?.data?.let(::readSelectedContact) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (goalId <= 0) { finish(); return }
        setContentView(R.layout.activity_recovery_contacts)
        viewModel = ViewModelProvider(this, RecoveryViewModelFactory(applicationContext))[
            RecoveryViewModel::class.java
        ]
        setupViews()
        observe()
        viewModel.load(goalId)
    }

    private fun setupViews() {
        findViewById<ImageButton>(R.id.btnBackRecoveryContacts).setOnClickListener { finish() }
        actionSpinner = findViewById(R.id.spinnerRecoveryContactAction)
        actionSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf(
                getString(R.string.recovery_contacts_call),
                getString(R.string.recovery_contacts_sms),
                getString(R.string.recovery_contacts_view)
            )
        )
        adapter = RecoveryContactAdapter(
            onView = ::showContact,
            onCall = ::confirmCall,
            onSms = ::confirmSms,
            onDelete = ::confirmDelete
        )
        findViewById<RecyclerView>(R.id.recyclerRecoveryContacts).apply {
            layoutManager = LinearLayoutManager(this@RecoveryContactsActivity); adapter = this@RecoveryContactsActivity.adapter
        }
        findViewById<MaterialButton>(R.id.btnPickRecoveryContact).setOnClickListener {
            runCatching {
                contactPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
            }.onFailure { Toast.makeText(this, R.string.recovery_not_available, Toast.LENGTH_SHORT).show() }
        }
        findViewById<MaterialButton>(R.id.btnSaveRecoveryContact).setOnClickListener { saveContact() }
    }

    private fun observe() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(state.contacts)
                    findViewById<TextView>(R.id.tvRecoveryContactsEmpty).isVisible = state.contacts.isEmpty()
                }
            }
        }
    }

    private fun saveContact() {
        val name = findViewById<TextInputEditText>(R.id.inputRecoveryContactName).text?.toString().orEmpty().trim()
        val phone = findViewById<TextInputEditText>(R.id.inputRecoveryContactPhone).text?.toString().orEmpty().trim()
        findViewById<TextInputLayout>(R.id.layoutRecoveryContactName).error =
            if (name.isBlank()) getString(R.string.recovery_required_field) else null
        findViewById<TextInputLayout>(R.id.layoutRecoveryContactPhone).error =
            if (phone.isBlank()) getString(R.string.recovery_invalid_phone) else null
        if (name.isBlank() || phone.isBlank()) return
        viewModel.saveContact(
            RecoverySupportContact(
                goalId = goalId,
                name = name,
                description = findViewById<TextInputEditText>(R.id.inputRecoveryContactDescription).text?.toString(),
                phone = phone,
                preferredAction = RecoveryContactAction.entries[actionSpinner.selectedItemPosition]
            )
        )
        findViewById<TextInputEditText>(R.id.inputRecoveryContactName).setText("")
        findViewById<TextInputEditText>(R.id.inputRecoveryContactDescription).setText("")
        findViewById<TextInputEditText>(R.id.inputRecoveryContactPhone).setText("")
    }

    private fun readSelectedContact(uri: Uri) {
        runCatching {
            contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    findViewById<TextInputEditText>(R.id.inputRecoveryContactName).setText(cursor.getString(0))
                    findViewById<TextInputEditText>(R.id.inputRecoveryContactPhone).setText(cursor.getString(1))
                }
            }
        }.onFailure { Toast.makeText(this, R.string.recovery_not_available, Toast.LENGTH_SHORT).show() }
    }

    private fun showContact(contact: RecoverySupportContact) {
        AlertDialog.Builder(this).setTitle(contact.name)
            .setMessage(listOfNotNull(contact.description, contact.phone).joinToString("\n"))
            .setPositiveButton(android.R.string.ok, null).show()
    }

    private fun confirmCall(contact: RecoverySupportContact) {
        AlertDialog.Builder(this).setMessage(getString(R.string.recovery_contacts_call_confirm, contact.name))
            .setNegativeButton(R.string.recovery_cancel, null)
            .setPositiveButton(R.string.recovery_contacts_open_dialer) { _, _ ->
                runCatching { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(contact.phone)}"))) }
                    .onFailure { Toast.makeText(this, R.string.recovery_not_available, Toast.LENGTH_SHORT).show() }
            }.show()
    }

    private fun confirmSms(contact: RecoverySupportContact) {
        AlertDialog.Builder(this).setMessage(getString(R.string.recovery_contacts_sms_confirm, contact.name))
            .setNegativeButton(R.string.recovery_cancel, null)
            .setPositiveButton(R.string.recovery_contacts_open_messages) { _, _ ->
                runCatching { startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(contact.phone)}"))) }
                    .onFailure { Toast.makeText(this, R.string.recovery_not_available, Toast.LENGTH_SHORT).show() }
            }.show()
    }

    private fun confirmDelete(contact: RecoverySupportContact) {
        AlertDialog.Builder(this).setMessage(R.string.recovery_delete)
            .setNegativeButton(R.string.recovery_cancel, null)
            .setPositiveButton(R.string.recovery_delete) { _, _ -> viewModel.deleteContact(contact) }.show()
    }
}
