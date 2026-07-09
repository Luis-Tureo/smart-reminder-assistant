package com.luistureo.voicereminderapp.core.loan.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.luistureo.voicereminderapp.core.loan.ClpFormatter
import com.luistureo.voicereminderapp.domain.loan.model.Loan
import com.luistureo.voicereminderapp.domain.loan.model.LoanType

class LoanNotificationHelper(
    private val context: Context
) {
    fun showLoanReminderNotification(loan: Loan, notificationId: Int) {
        createChannel()
        if (!hasNotificationPermission()) return

        val title = if (loan.type == LoanType.MONEY_LENT_TO_ME) {
            "Dinero prestado pendiente"
        } else {
            "Deuda pendiente"
        }
        val body = "${loan.personName}: ${ClpFormatter.format(loan.remainingAmountClp)} pendiente"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .build()

        notifySafely(notificationId, notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Dinero prestado",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Recordatorios locales de dinero prestado"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun notifySafely(notificationId: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
        }
    }

    companion object {
        private const val CHANNEL_ID = "loan_reminder_channel"
    }
}
