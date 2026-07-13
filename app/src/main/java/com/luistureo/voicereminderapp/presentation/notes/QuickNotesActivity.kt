package com.luistureo.voicereminderapp.presentation.notes

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.domain.notes.model.QuickNote
import com.luistureo.voicereminderapp.domain.notes.model.QuickNoteFilter
import kotlinx.coroutines.launch

class QuickNotesActivity : ComponentActivity() {
    private lateinit var viewModel: QuickNotesViewModel
    private lateinit var adapter: QuickNotesAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyContainer: View
    private lateinit var emptyTitle: TextView
    private lateinit var emptyMessage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quick_notes)

        viewModel = ViewModelProvider(
            this,
            QuickNotesViewModelFactory.from(applicationContext)
        )[QuickNotesViewModel::class.java]
        ViewCompat.setAccessibilityHeading(findViewById(R.id.tvQuickNotesTitle), true)
        setupList()
        setupActions()
        observeState()
        observeEvents()
        observePendingDeletion()
    }

    private fun setupList() {
        recyclerView = findViewById(R.id.recyclerQuickNotes)
        emptyContainer = findViewById(R.id.quickNotesEmptyContainer)
        emptyTitle = findViewById(R.id.tvQuickNotesEmptyTitle)
        emptyMessage = findViewById(R.id.tvQuickNotesEmptyMessage)

        adapter = QuickNotesAdapter(
            onOpen = ::openEditor,
            onPin = viewModel::togglePinned,
            onArchive = viewModel::toggleArchived,
            onDelete = viewModel::delete
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupActions() {
        findViewById<MaterialButton>(R.id.btnQuickNotesBack).setOnClickListener { finish() }
        findViewById<ExtendedFloatingActionButton>(R.id.fabNewQuickNote).setOnClickListener {
            openEditor(null)
        }

        findViewById<TextInputEditText>(R.id.inputQuickNotesSearch)
            .addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    viewModel.setQuery(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })

        findViewById<ChipGroup>(R.id.chipGroupQuickNotesFilters)
            .setOnCheckedStateChangeListener { _, checkedIds ->
                val filter = when (checkedIds.firstOrNull()) {
                    R.id.chipQuickNotesPinned -> QuickNoteFilter.PINNED
                    R.id.chipQuickNotesArchived -> QuickNoteFilter.ARCHIVED
                    else -> QuickNoteFilter.ALL
                }
                viewModel.setFilter(filter)
            }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    adapter.submitList(state.notes)
                    renderEmptyState(state)
                }
            }
        }
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        QuickNotesEvent.DeleteFailed,
                        QuickNotesEvent.RestoreFailed,
                        QuickNotesEvent.UpdateFailed -> Toast.makeText(
                            this@QuickNotesActivity,
                            R.string.quick_notes_update_failed,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun observePendingDeletion() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pendingDeleted.collect { note ->
                    if (note != null) showDeleteUndo(note)
                }
            }
        }
    }

    private fun renderEmptyState(state: QuickNotesUiState) {
        val isEmpty = !state.isLoading && state.notes.isEmpty()
        emptyContainer.isVisible = isEmpty
        recyclerView.isVisible = !isEmpty
        if (!isEmpty) return

        when {
            state.query.isNotBlank() -> {
                emptyTitle.setText(R.string.quick_notes_no_results_title)
                emptyMessage.setText(R.string.quick_notes_no_results_message)
            }
            state.filter == QuickNoteFilter.PINNED -> {
                emptyTitle.setText(R.string.quick_notes_no_pinned_title)
                emptyMessage.setText(R.string.quick_notes_no_pinned_message)
            }
            state.filter == QuickNoteFilter.ARCHIVED -> {
                emptyTitle.setText(R.string.quick_notes_no_archived_title)
                emptyMessage.setText(R.string.quick_notes_no_archived_message)
            }
            else -> {
                emptyTitle.setText(R.string.quick_notes_empty_title)
                emptyMessage.setText(R.string.quick_notes_empty_message)
            }
        }
    }

    private fun showDeleteUndo(note: QuickNote) {
        Snackbar.make(
            recyclerView,
            R.string.quick_notes_deleted,
            Snackbar.LENGTH_LONG
        ).setAction(R.string.quick_notes_undo) {
            viewModel.restoreDeleted(note)
        }.addCallback(
            object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    if (
                        event == DISMISS_EVENT_TIMEOUT ||
                        event == DISMISS_EVENT_SWIPE
                    ) {
                        viewModel.dismissDeleteUndo(note.id)
                    }
                }
            }
        ).show()
    }

    private fun openEditor(note: QuickNote?) {
        startActivity(QuickNoteEditorActivity.intent(this, note?.id))
    }
}
