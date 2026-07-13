package com.luistureo.voicereminderapp.presentation.notes

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.domain.notes.model.QuickNoteColorTag
import kotlinx.coroutines.launch

class QuickNoteEditorActivity : ComponentActivity() {
    private lateinit var viewModel: QuickNoteEditorViewModel
    private lateinit var titleInput: TextInputEditText
    private lateinit var contentInput: TextInputEditText
    private lateinit var contentInputLayout: TextInputLayout
    private lateinit var pinnedCheckBox: CheckBox
    private lateinit var colorInput: MaterialAutoCompleteTextView
    private lateinit var optionsContainer: View
    private lateinit var saveStatus: TextView
    private lateinit var archiveButton: MaterialButton
    private var isBinding = false

    private val colorOptions = listOf(
        null,
        QuickNoteColorTag.YELLOW,
        QuickNoteColorTag.BLUE,
        QuickNoteColorTag.GREEN,
        QuickNoteColorTag.PINK
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quick_note_editor)

        val factory = QuickNoteEditorViewModelFactory.from(
            context = applicationContext,
            owner = this,
            defaultArgs = intent.extras
        )
        viewModel = ViewModelProvider(this, factory)[QuickNoteEditorViewModel::class.java]
        initViews()
        setupInputs()
        setupActions()
        observeState()
        observeEvents()
        observeFinishRequest()

        optionsContainer.isVisible = savedInstanceState?.getBoolean(STATE_OPTIONS_OPEN) == true
        installBackHandler()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_OPTIONS_OPEN, optionsContainer.isVisible)
        super.onSaveInstanceState(outState)
    }

    private fun initViews() {
        titleInput = findViewById(R.id.inputQuickNoteTitle)
        contentInput = findViewById(R.id.inputQuickNoteContent)
        contentInputLayout = findViewById(R.id.layoutQuickNoteContent)
        pinnedCheckBox = findViewById(R.id.checkQuickNotePinned)
        colorInput = findViewById(R.id.inputQuickNoteColor)
        optionsContainer = findViewById(R.id.quickNoteOptionsContainer)
        saveStatus = findViewById(R.id.tvQuickNoteSaveStatus)
        archiveButton = findViewById(R.id.btnQuickNoteArchiveEditor)

        val screenTitle = findViewById<TextView>(R.id.tvQuickNoteEditorTitle)
        screenTitle.setText(
            if (intent.getIntExtra(EXTRA_NOTE_ID, 0) > 0) {
                R.string.quick_note_editor_edit_title
            } else {
                R.string.quick_note_editor_new_title
            }
        )
        ViewCompat.setAccessibilityHeading(screenTitle, true)
    }

    private fun setupInputs() {
        titleInput.addTextChangedListener(draftWatcher { viewModel.updateTitle(it) })
        contentInput.addTextChangedListener(draftWatcher { viewModel.updateContent(it) })
        pinnedCheckBox.setOnCheckedChangeListener { _, checked ->
            if (!isBinding) viewModel.updatePinned(checked)
        }

        ArrayAdapter.createFromResource(
            this,
            R.array.quick_note_color_options,
            android.R.layout.simple_list_item_1
        ).also(colorInput::setAdapter)
        colorInput.setOnItemClickListener { _, _, position, _ ->
            if (!isBinding) viewModel.updateColor(colorOptions.getOrNull(position))
        }
    }

    private fun setupActions() {
        findViewById<MaterialButton>(R.id.btnQuickNoteBack).setOnClickListener {
            viewModel.requestBack()
        }
        findViewById<MaterialButton>(R.id.btnQuickNoteDone).setOnClickListener {
            viewModel.requestDone()
        }
        findViewById<MaterialButton>(R.id.btnQuickNoteOptions).setOnClickListener {
            optionsContainer.isVisible = !optionsContainer.isVisible
        }
        findViewById<MaterialButton>(R.id.btnQuickNoteShare).setOnClickListener {
            viewModel.requestShare()
        }
        archiveButton.setOnClickListener {
            viewModel.setArchivedAndFinish(!viewModel.uiState.value.isArchived)
        }
        findViewById<MaterialButton>(R.id.btnQuickNoteDeleteEditor).setOnClickListener {
            showDeleteConfirmation()
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::renderState)
            }
        }
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        QuickNoteEditorEvent.ShowValidation -> showValidation()
                        is QuickNoteEditorEvent.Share -> openShareSheet(
                            event.title,
                            event.content
                        )
                    }
                }
            }
        }
    }

    private fun observeFinishRequest() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.finishRequested.collect { requested ->
                    if (requested) finish()
                }
            }
        }
    }

    private fun renderState(state: QuickNoteEditorUiState) {
        isBinding = true
        if (titleInput.text?.toString() != state.title) {
            titleInput.setText(state.title)
            titleInput.setSelection(state.title.length)
        }
        if (contentInput.text?.toString() != state.content) {
            contentInput.setText(state.content)
            contentInput.setSelection(state.content.length)
        }
        pinnedCheckBox.isChecked = state.isPinned
        val colorPosition = colorOptions.indexOf(state.colorTag).coerceAtLeast(0)
        val colorLabel = resources.getStringArray(R.array.quick_note_color_options)[colorPosition]
        if (colorInput.text?.toString() != colorLabel) colorInput.setText(colorLabel, false)
        archiveButton.setText(
            if (state.isArchived) R.string.quick_notes_restore else R.string.quick_notes_archive
        )
        isBinding = false

        val saveStatusRes = when (state.saveState) {
            QuickNoteSaveState.IDLE -> null
            QuickNoteSaveState.SAVING -> R.string.quick_note_saving
            QuickNoteSaveState.SAVED -> R.string.quick_note_saved
            QuickNoteSaveState.ERROR -> R.string.quick_note_save_failed
        }
        saveStatus.isVisible = saveStatusRes != null
        if (saveStatusRes == null) saveStatus.text = null else saveStatus.setText(saveStatusRes)
        contentInputLayout.isEnabled = state.isLoaded
        titleInput.isEnabled = state.isLoaded
    }

    private fun draftWatcher(onChanged: (String) -> Unit): TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            if (!isBinding) {
                contentInputLayout.error = null
                onChanged(s?.toString().orEmpty())
            }
        }
        override fun afterTextChanged(s: Editable?) = Unit
    }

    private fun showValidation() {
        contentInputLayout.error = getString(R.string.quick_note_content_required)
        contentInput.requestFocus()
        Toast.makeText(this, R.string.quick_note_content_required, Toast.LENGTH_SHORT).show()
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.quick_note_delete_title)
            .setMessage(R.string.quick_note_delete_confirmation)
            .setNegativeButton(R.string.quick_note_cancel, null)
            .setPositiveButton(R.string.quick_notes_delete) { _, _ ->
                viewModel.deleteAndFinish()
            }
            .show()
    }

    private fun openShareSheet(title: String?, content: String) {
        val sharedText = listOfNotNull(
            title?.takeIf(String::isNotBlank),
            content.takeIf(String::isNotBlank)
        ).joinToString(separator = "\n\n")
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, sharedText)
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.quick_note_share_title)))
    }

    private fun installBackHandler() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    viewModel.requestBack()
                }
            }
        )
    }

    companion object {
        internal const val EXTRA_NOTE_ID = "extra_quick_note_id"
        private const val STATE_OPTIONS_OPEN = "quick_note_options_open"

        fun intent(context: Context, noteId: Int?): Intent =
            Intent(context, QuickNoteEditorActivity::class.java).apply {
                if (noteId != null && noteId > 0) putExtra(EXTRA_NOTE_ID, noteId)
            }
    }
}
