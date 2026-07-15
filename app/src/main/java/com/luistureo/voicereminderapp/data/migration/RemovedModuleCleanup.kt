package com.luistureo.voicereminderapp.data.migration

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.core.net.toUri

object RemovedModuleCleanup {
    private const val cleanupPreferences = "removed_module_cleanup"
    private const val cleanupCompletedKey = "cleanup_v18_completed"
    private const val databaseName = "reminder_database"
    private const val routinePreferences = "routine_assistant_preferences"
    private const val nextDaySummaryPreferences = "next_day_summary_preferences"
    private const val loanReceiverClass =
        "com.luistureo.voicereminderapp.core.loan.alarm.LoanReminderReceiver"
    private const val routineReceiverClass =
        "com.luistureo.voicereminderapp.core.routine.RoutineAlarmReceiver"
    private const val nextDaySummaryReceiverClass =
        "com.luistureo.voicereminderapp.core.alarm.NextDaySummaryReceiver"
    internal const val NEXT_DAY_SUMMARY_REQUEST_CODE = 9_001

    private val loanAlarmKinds = listOf(
        "SAME_DAY",
        "ONE_DAY_BEFORE",
        "THREE_DAYS_BEFORE",
        "CUSTOM",
        "REPEAT_AFTER_DUE"
    )
    private val routineAlarmTypes = listOf(
        "START",
        "DEADLINE",
        "PENDING_TASKS",
        "SNOOZED_START",
        "SNOOZED_DEADLINE",
        "SNOOZED_PENDING_TASKS",
        "DAY_CLOSE"
    )

    fun runOnce(context: Context) {
        val appContext = context.applicationContext
        val cleanupState = appContext.getSharedPreferences(cleanupPreferences, Context.MODE_PRIVATE)
        if (cleanupState.getBoolean(cleanupCompletedKey, false)) return

        val moduleIds = runCatching { readStoredModuleIds(appContext) }.getOrDefault(ModuleIds())
        cancelLoanArtifacts(appContext, moduleIds.loanIds)
        cancelRoutineArtifacts(appContext, moduleIds.routineIds)
        cancelNextDaySummaryArtifacts(appContext)
        appContext.getSharedPreferences(routinePreferences, Context.MODE_PRIVATE)
            .edit { clear() }
        appContext.getSharedPreferences(nextDaySummaryPreferences, Context.MODE_PRIVATE)
            .edit { clear() }
        appContext.cacheDir.resolve("loan_attachments").deleteRecursively()
        deleteNotificationChannels(appContext)
        cleanupState.edit { putBoolean(cleanupCompletedKey, true) }
    }

    internal fun loanAlarmRequestCode(loanId: Int, kindOrdinal: Int): Int =
        300_000 + loanId * 10 + kindOrdinal

    internal fun routineAlarmRequestCode(routineId: Int, typeOrdinal: Int): Int =
        500_000 + routineId * 10 + typeOrdinal

    internal fun loanNotificationId(loanId: Int): Int = 400_000 + loanId

    internal fun routineNotificationId(routineId: Int, kindOrdinal: Int): Int =
        500_000 + routineId * 10 + kindOrdinal

    private fun cancelLoanArtifacts(context: Context, loanIds: Set<Int>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        loanIds.forEach { loanId ->
            loanAlarmKinds.forEachIndexed { ordinal, kind ->
                val intent = Intent().apply {
                    setClassName(context.packageName, loanReceiverClass)
                    data = "voicereminder://alarm/loan/$loanId/$kind".toUri()
                }
                cancelBroadcast(
                    context,
                    alarmManager,
                    loanAlarmRequestCode(loanId, ordinal),
                    intent
                )
            }
            NotificationManagerCompat.from(context).cancel(loanNotificationId(loanId))
        }
    }

    private fun cancelRoutineArtifacts(context: Context, routineIds: Set<Int>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        routineIds.forEach { routineId ->
            routineAlarmTypes.forEachIndexed { ordinal, type ->
                val intent = Intent().apply {
                    setClassName(context.packageName, routineReceiverClass)
                    data = "voicereminder://routine/alarm/$routineId/$type".toUri()
                }
                cancelBroadcast(
                    context,
                    alarmManager,
                    routineAlarmRequestCode(routineId, ordinal),
                    intent
                )
            }
            repeat(4) { kindOrdinal ->
                NotificationManagerCompat.from(context).cancel(
                    routineNotificationId(routineId, kindOrdinal)
                )
            }
        }
    }

    private fun cancelNextDaySummaryArtifacts(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent().apply {
            setClassName(context.packageName, nextDaySummaryReceiverClass)
        }
        cancelBroadcast(context, alarmManager, NEXT_DAY_SUMMARY_REQUEST_CODE, intent)
        NotificationManagerCompat.from(context).cancel(NEXT_DAY_SUMMARY_REQUEST_CODE)
    }

    private fun cancelBroadcast(
        context: Context,
        alarmManager: AlarmManager,
        requestCode: Int,
        intent: Intent
    ) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun deleteNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.deleteNotificationChannel("loan_reminder_channel")
        manager.deleteNotificationChannel("daily_routine_channel")
        manager.deleteNotificationChannel("daily_routine_motivation_channel")
        manager.deleteNotificationChannel("next_day_summary_channel")
    }

    private fun readStoredModuleIds(context: Context): ModuleIds {
        val databaseFile = context.getDatabasePath(databaseName)
        if (!databaseFile.exists()) return ModuleIds()
        return SQLiteDatabase.openDatabase(
            databaseFile.path,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use { database ->
            ModuleIds(
                loanIds = database.readIdsIfTableExists("loan_records"),
                routineIds = database.readIdsIfTableExists("routines")
            )
        }
    }

    private fun SQLiteDatabase.readIdsIfTableExists(tableName: String): Set<Int> {
        val exists = rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
            arrayOf(tableName)
        ).use { it.moveToFirst() }
        if (!exists) return emptySet()
        return rawQuery("SELECT id FROM $tableName", null).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add(cursor.getInt(0))
            }
        }
    }

    private data class ModuleIds(
        val loanIds: Set<Int> = emptySet(),
        val routineIds: Set<Int> = emptySet()
    )
}
