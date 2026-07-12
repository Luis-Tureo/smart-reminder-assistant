package com.luistureo.voicereminderapp.presentation.loan.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luistureo.voicereminderapp.core.loan.LoanCalculator
import com.luistureo.voicereminderapp.core.loan.LoanFilterSorter
import com.luistureo.voicereminderapp.core.loan.alarm.LoanReminderScheduler
import com.luistureo.voicereminderapp.domain.loan.model.LoanDraft
import com.luistureo.voicereminderapp.domain.loan.model.LoanFilter
import com.luistureo.voicereminderapp.domain.loan.model.LoanPayment
import com.luistureo.voicereminderapp.domain.loan.model.LoanSort
import com.luistureo.voicereminderapp.domain.loan.model.LoanStatus
import com.luistureo.voicereminderapp.domain.loan.usecase.AddLoanPaymentUseCase
import com.luistureo.voicereminderapp.domain.loan.usecase.DeleteLoanUseCase
import com.luistureo.voicereminderapp.domain.loan.usecase.GetLoanByIdUseCase
import com.luistureo.voicereminderapp.domain.loan.usecase.GetLoansUseCase
import com.luistureo.voicereminderapp.domain.loan.usecase.MarkLoanFullyPaidUseCase
import com.luistureo.voicereminderapp.domain.loan.usecase.SaveLoanUseCase
import com.luistureo.voicereminderapp.presentation.loan.state.LoanUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoanViewModel(
    private val getLoansUseCase: GetLoansUseCase,
    private val getLoanByIdUseCase: GetLoanByIdUseCase,
    private val saveLoanUseCase: SaveLoanUseCase,
    private val addLoanPaymentUseCase: AddLoanPaymentUseCase,
    private val markLoanFullyPaidUseCase: MarkLoanFullyPaidUseCase,
    private val deleteLoanUseCase: DeleteLoanUseCase,
    private val scheduler: LoanReminderScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoanUiState())
    val uiState: StateFlow<LoanUiState> = _uiState.asStateFlow()

    fun loadLoans() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            runCatching { getLoansUseCase() }
                .onSuccess { loans ->
                    _uiState.update {
                        val next = it.copy(
                            loans = loans,
                            summary = LoanCalculator.summary(loans),
                            isLoading = false,
                            message = null
                        )
                        next.copy(visibleLoans = buildVisibleLoans(next))
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isLoading = false, message = "No fue posible cargar los prestamos.")
                    }
                }
        }
    }

    fun loadLoan(loanId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null) }
            runCatching { getLoanByIdUseCase(loanId) }
                .onSuccess { loan ->
                    _uiState.update {
                        it.copy(
                            selectedLoan = loan,
                            isLoading = false,
                            message = if (loan == null) "No se encontro el prestamo." else null
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isLoading = false, message = "No fue posible cargar el prestamo.")
                    }
                }
        }
    }

    fun setQuery(query: String) {
        _uiState.update {
            val next = it.copy(query = query)
            next.copy(visibleLoans = buildVisibleLoans(next))
        }
    }

    fun setFilter(filter: LoanFilter) {
        _uiState.update {
            val next = it.copy(filter = filter)
            next.copy(visibleLoans = buildVisibleLoans(next))
        }
    }

    fun setSort(sort: LoanSort) {
        _uiState.update {
            val next = it.copy(sort = sort)
            next.copy(visibleLoans = buildVisibleLoans(next))
        }
    }

    fun saveLoan(draft: LoanDraft, onSaved: (Int) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { saveLoanUseCase(draft) }
                .onSuccess { loan ->
                    syncLoanAlarms(loan.id)
                    loadLoans()
                    _uiState.update { it.copy(selectedLoan = loan, message = "Prestamo guardado.") }
                    onSaved(loan.id)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(message = error.message ?: "No fue posible guardar el prestamo.")
                    }
                }
        }
    }

    fun addPayment(loanId: Int, payment: LoanPayment) {
        viewModelScope.launch {
            runCatching { addLoanPaymentUseCase(loanId, payment) }
                .onSuccess { loan ->
                    if (loan != null) {
                        syncLoanAlarms(loan.id)
                        loadLoan(loan.id)
                    }
                    _uiState.update { it.copy(message = "Pago registrado.") }
                }
                .onFailure {
                    _uiState.update { it.copy(message = "No fue posible registrar el pago.") }
                }
        }
    }

    fun markFullyPaid(loanId: Int) {
        viewModelScope.launch {
            runCatching { markLoanFullyPaidUseCase(loanId) }
                .onSuccess { loan ->
                    if (loan != null) {
                        scheduler.cancelLoanReminders(loan.id)
                        loadLoan(loan.id)
                    }
                    _uiState.update { it.copy(message = "Prestamo pagado completamente.") }
                }
                .onFailure {
                    _uiState.update { it.copy(message = "No fue posible marcar como pagado.") }
                }
        }
    }

    fun deleteLoan(loanId: Int, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                deleteLoanUseCase(loanId)
                scheduler.cancelLoanReminders(loanId)
            }
                .onSuccess {
                    _uiState.update { it.copy(message = "Prestamo eliminado.") }
                    onDeleted()
                }
                .onFailure {
                    _uiState.update { it.copy(message = "No fue posible eliminar el prestamo.") }
                }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private suspend fun syncLoanAlarms(loanId: Int) {
        val loan = getLoanByIdUseCase(loanId) ?: return
        if (loan.status == LoanStatus.PAID || loan.status == LoanStatus.CANCELED) {
            scheduler.cancelLoanReminders(loan.id)
        } else {
            scheduler.scheduleLoanReminders(loan)
        }
    }

    private fun buildVisibleLoans(state: LoanUiState) = LoanFilterSorter.apply(
        loans = state.loans,
        query = state.query,
        filter = state.filter,
        sort = state.sort
    )
}
