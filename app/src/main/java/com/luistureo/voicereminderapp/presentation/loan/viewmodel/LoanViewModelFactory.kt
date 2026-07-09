package com.luistureo.voicereminderapp.presentation.loan.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.luistureo.voicereminderapp.core.loan.alarm.LoanReminderScheduler
import com.luistureo.voicereminderapp.data.local.database.ReminderDatabase
import com.luistureo.voicereminderapp.data.repository.LoanRepositoryImpl
import com.luistureo.voicereminderapp.domain.loan.usecase.AddLoanPaymentUseCase
import com.luistureo.voicereminderapp.domain.loan.usecase.DeleteLoanUseCase
import com.luistureo.voicereminderapp.domain.loan.usecase.GetLoanByIdUseCase
import com.luistureo.voicereminderapp.domain.loan.usecase.GetLoansUseCase
import com.luistureo.voicereminderapp.domain.loan.usecase.MarkLoanFullyPaidUseCase
import com.luistureo.voicereminderapp.domain.loan.usecase.SaveLoanUseCase

class LoanViewModelFactory(
    context: Context
) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repository = LoanRepositoryImpl(
            ReminderDatabase.getDatabase(appContext).loanDao()
        )
        return LoanViewModel(
            getLoansUseCase = GetLoansUseCase(repository),
            getLoanByIdUseCase = GetLoanByIdUseCase(repository),
            saveLoanUseCase = SaveLoanUseCase(repository),
            addLoanPaymentUseCase = AddLoanPaymentUseCase(repository),
            markLoanFullyPaidUseCase = MarkLoanFullyPaidUseCase(repository),
            deleteLoanUseCase = DeleteLoanUseCase(repository),
            scheduler = LoanReminderScheduler(appContext)
        ) as T
    }
}
