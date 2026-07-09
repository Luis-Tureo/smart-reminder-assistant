package com.luistureo.voicereminderapp.presentation.loan

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.widget.addTextChangedListener
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
import com.luistureo.voicereminderapp.domain.loan.model.LoanFilter
import com.luistureo.voicereminderapp.domain.loan.model.LoanSort
import com.luistureo.voicereminderapp.presentation.loan.adapter.LoanListAdapter
import com.luistureo.voicereminderapp.presentation.loan.viewmodel.LoanViewModel
import com.luistureo.voicereminderapp.presentation.loan.viewmodel.LoanViewModelFactory
import kotlinx.coroutines.launch

class LoanListActivity : ComponentActivity() {

    private lateinit var viewModel: LoanViewModel
    private lateinit var adapter: LoanListAdapter

    private lateinit var totalLentToMeText: TextView
    private lateinit var totalIOweText: TextView
    private lateinit var totalRecoveredText: TextView
    private lateinit var totalPendingText: TextView
    private lateinit var overdueText: TextView
    private lateinit var searchInput: TextInputEditText
    private lateinit var filterButton: MaterialButton
    private lateinit var sortButton: MaterialButton
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loan_list)

        viewModel = ViewModelProvider(
            this,
            LoanViewModelFactory(applicationContext)
        )[LoanViewModel::class.java]

        initViews()
        setupRecycler()
        setupListeners()
        observeState()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadLoans()
    }

    private fun initViews() {
        findViewById<ImageButton>(R.id.btnBackLoanList).setOnClickListener { finish() }
        totalLentToMeText = findViewById(R.id.tvSummaryLentToMe)
        totalIOweText = findViewById(R.id.tvSummaryIOwe)
        totalRecoveredText = findViewById(R.id.tvSummaryRecovered)
        totalPendingText = findViewById(R.id.tvSummaryPending)
        overdueText = findViewById(R.id.tvSummaryOverdue)
        searchInput = findViewById(R.id.inputLoanSearch)
        filterButton = findViewById(R.id.btnLoanFilter)
        sortButton = findViewById(R.id.btnLoanSort)
        emptyText = findViewById(R.id.tvLoanListEmpty)
    }

    private fun setupRecycler() {
        adapter = LoanListAdapter { loan ->
            startActivity(
                Intent(this, LoanDetailActivity::class.java)
                    .putExtra(LoanDetailActivity.EXTRA_LOAN_ID, loan.id)
            )
        }
        findViewById<RecyclerView>(R.id.recyclerLoans).apply {
            layoutManager = LinearLayoutManager(this@LoanListActivity)
            adapter = this@LoanListActivity.adapter
        }
    }

    private fun setupListeners() {
        findViewById<MaterialButton>(R.id.btnCreateLoan).setOnClickListener {
            startActivity(Intent(this, LoanEditorActivity::class.java))
        }

        searchInput.addTextChangedListener {
            viewModel.setQuery(it?.toString().orEmpty())
        }

        filterButton.setOnClickListener { showFilterDialog() }
        sortButton.setOnClickListener { showSortDialog() }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    totalLentToMeText.text = ClpFormatter.format(state.summary.totalLentToMeClp)
                    totalIOweText.text = ClpFormatter.format(state.summary.totalIOweClp)
                    totalRecoveredText.text = ClpFormatter.format(state.summary.totalRecoveredClp)
                    totalPendingText.text = ClpFormatter.format(state.summary.totalPendingClp)
                    overdueText.text = state.summary.overdueCount.toString()
                    filterButton.text = state.filter.label
                    sortButton.text = state.sort.label
                    adapter.submitList(state.visibleLoans)
                    emptyText.visibility = if (state.visibleLoans.isEmpty()) {
                        android.view.View.VISIBLE
                    } else {
                        android.view.View.GONE
                    }
                    state.message?.let {
                        Toast.makeText(this@LoanListActivity, it, Toast.LENGTH_SHORT).show()
                        viewModel.consumeMessage()
                    }
                }
            }
        }
    }

    private fun showFilterDialog() {
        val values = LoanFilter.entries.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.loan_filter_title)
            .setItems(values.map { it.label }.toTypedArray()) { _, which ->
                viewModel.setFilter(values[which])
            }
            .show()
    }

    private fun showSortDialog() {
        val values = LoanSort.entries.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.loan_sort_title)
            .setItems(values.map { it.label }.toTypedArray()) { _, which ->
                viewModel.setSort(values[which])
            }
            .show()
    }
}
