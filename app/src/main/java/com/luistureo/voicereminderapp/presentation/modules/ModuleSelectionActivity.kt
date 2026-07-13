package com.luistureo.voicereminderapp.presentation.modules

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.luistureo.voicereminderapp.MainActivity
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.modules.HomeModuleRegistry
import com.luistureo.voicereminderapp.core.modules.ModuleSelectionStore

class ModuleSelectionActivity : ComponentActivity() {
    private lateinit var store: ModuleSelectionStore
    private lateinit var adapter: ModuleSelectionAdapter
    private lateinit var validationMessage: TextView
    private var isFirstLaunch: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_module_selection)

        store = ModuleSelectionStore(applicationContext)
        isFirstLaunch = intent.getBooleanExtra(EXTRA_FIRST_LAUNCH, false)

        val initialSelection = savedInstanceState
            ?.getStringArrayList(STATE_DRAFT_SELECTION)
            ?.toSet()
            ?: if (isFirstLaunch) emptySet() else store.selectedModuleIds()

        setupHeader()
        setupList(initialSelection)
        setupActions()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(
            STATE_DRAFT_SELECTION,
            ArrayList(adapter.selectedIds())
        )
        super.onSaveInstanceState(outState)
    }

    private fun setupHeader() {
        val title = findViewById<TextView>(R.id.tvModuleSelectionTitle)
        val subtitle = findViewById<TextView>(R.id.tvModuleSelectionSubtitle)
        val back = findViewById<MaterialButton>(R.id.btnModuleSelectionBack)

        title.setText(
            if (isFirstLaunch) R.string.module_selection_first_title
            else R.string.module_selection_edit_title
        )
        ViewCompat.setAccessibilityHeading(title, true)
        subtitle.setText(R.string.module_selection_subtitle)
        back.isVisible = !isFirstLaunch
        back.setOnClickListener { finish() }
    }

    private fun setupList(initialSelection: Set<String>) {
        validationMessage = findViewById(R.id.tvModuleSelectionValidation)
        adapter = ModuleSelectionAdapter(
            modules = HomeModuleRegistry.modules,
            selectedIds = HomeModuleRegistry.sanitizeIds(initialSelection),
            onSelectionChanged = { selected ->
                if (selected.isNotEmpty()) validationMessage.isVisible = false
            }
        )
        findViewById<RecyclerView>(R.id.recyclerModuleSelection).apply {
            layoutManager = LinearLayoutManager(this@ModuleSelectionActivity)
            adapter = this@ModuleSelectionActivity.adapter
        }
    }

    private fun setupActions() {
        findViewById<MaterialButton>(R.id.btnSelectAllModules).setOnClickListener {
            adapter.selectAll()
        }
        findViewById<MaterialButton>(R.id.btnClearModuleSelection).setOnClickListener {
            adapter.clearSelection()
        }
        findViewById<MaterialButton>(R.id.btnSaveModuleSelection).apply {
            setText(
                if (isFirstLaunch) R.string.module_selection_load_app
                else R.string.module_selection_save
            )
            setOnClickListener { saveSelection() }
        }
    }

    private fun saveSelection() {
        val selected = adapter.selectedIds()
        if (selected.isEmpty()) {
            validationMessage.isVisible = true
            Toast.makeText(
                this,
                R.string.module_selection_required,
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!store.saveSelection(selected)) {
            Toast.makeText(
                this,
                R.string.module_selection_save_failed,
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (isFirstLaunch) {
            startActivity(Intent(this, MainActivity::class.java))
        } else {
            setResult(RESULT_OK)
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val EXTRA_FIRST_LAUNCH = "extra_first_launch"
        private const val STATE_DRAFT_SELECTION = "draft_module_selection"

        fun firstLaunchIntent(context: Context): Intent =
            Intent(context, ModuleSelectionActivity::class.java)
                .putExtra(EXTRA_FIRST_LAUNCH, true)

        fun editIntent(context: Context): Intent =
            Intent(context, ModuleSelectionActivity::class.java)
                .putExtra(EXTRA_FIRST_LAUNCH, false)
    }
}
