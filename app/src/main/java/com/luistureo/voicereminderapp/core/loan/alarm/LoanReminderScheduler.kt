package com.luistureo.voicereminderapp.core.loan.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.luistureo.voicereminderapp.core.loan.LoanReminderPolicy
import com.luistureo.voicereminderapp.domain.loan.model.Loan
import com.luistureo.voicereminderapp.domain.loan.model.LoanReminderKind

class LoanReminderScheduler(
    private val context: Context
) {
    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleLoanReminders(loan: Loan) {
        cancelLoanReminders(loan.id)
        LoanReminderPolicy.resolveReminderTimes(loan).forEach { (kind, triggerAtMillis) ->
            val pendingIntent = buildPendingIntent(
                loan.id,
                kind,
                PendingIntent.FLAG_UPDATE_CURRENT
            ) ?: return@forEach
            scheduleAlarmSafely(triggerAtMillis, pendingIntent)
        }
    }

    fun cancelLoanReminders(loanId: Int) {
        LoanReminderKind.entries.forEach { kind ->
            val pendingIntent = buildPendingIntent(loanId, kind, PendingIntent.FLAG_NO_CREATE)
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    private fun buildPendingIntent(
        loanId: Int,
        kind: LoanReminderKind,
        flag: Int
    ): PendingIntent? {
        val intent = Intent(context, LoanReminderReceiver::class.java).apply {
            putExtra(LoanReminderReceiver.EXTRA_LOAN_ID, loanId)
            putExtra(LoanReminderReceiver.EXTRA_REMINDER_KIND, kind.name)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_OFFSET + (loanId * 10) + kind.ordinal,
            intent,
            flag or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun scheduleAlarmSafely(
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        try {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                return
            }

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    companion object {
        private const val REQUEST_CODE_OFFSET = 300_000
    }
}
