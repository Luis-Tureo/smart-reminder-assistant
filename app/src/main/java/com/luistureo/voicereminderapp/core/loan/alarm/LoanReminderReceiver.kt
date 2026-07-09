package com.luistureo.voicereminderapp.core.loan.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.luistureo.voicereminderapp.core.loan.LoanReminderPolicy
import com.luistureo.voicereminderapp.core.loan.notification.LoanNotificationHelper
import com.luistureo.voicereminderapp.data.local.database.ReminderDatabase
import com.luistureo.voicereminderapp.data.repository.LoanRepositoryImpl
import com.luistureo.voicereminderapp.domain.loan.model.LoanReminderKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoanReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val loanId = intent.getIntExtra(EXTRA_LOAN_ID, 0)
        if (loanId <= 0) return

        val kind = intent.getStringExtra(EXTRA_REMINDER_KIND)
            ?.let { runCatching { LoanReminderKind.valueOf(it) }.getOrNull() }
            ?: LoanReminderKind.SAME_DAY
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = LoanRepositoryImpl(
                    ReminderDatabase.getDatabase(context).loanDao()
                )
                val scheduler = LoanReminderScheduler(context.applicationContext)
                val loan = repository.getLoanById(loanId)
                if (loan == null || !LoanReminderPolicy.shouldSchedule(loan)) {
                    scheduler.cancelLoanReminders(loanId)
                    return@launch
                }

                LoanNotificationHelper(context).showLoanReminderNotification(
                    loan = loan,
                    notificationId = NOTIFICATION_OFFSET + loanId
                )

                if (kind == LoanReminderKind.REPEAT_AFTER_DUE) {
                    scheduler.scheduleLoanReminders(loan)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_LOAN_ID = "extra_loan_id"
        const val EXTRA_REMINDER_KIND = "extra_loan_reminder_kind"
        private const val NOTIFICATION_OFFSET = 400_000
    }
}
