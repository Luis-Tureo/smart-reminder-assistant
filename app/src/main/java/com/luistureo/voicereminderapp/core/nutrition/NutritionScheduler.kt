package com.luistureo.voicereminderapp.core.nutrition

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import com.luistureo.voicereminderapp.domain.nutrition.model.DatedNutritionMeal
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMealStatus
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionPreferences
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionReminderType
import com.luistureo.voicereminderapp.domain.nutrition.repository.NutritionRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class NutritionScheduler(private val context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager: AlarmManager
        get() = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun syncMeal(item: DatedNutritionMeal, preferences: NutritionPreferences) {
        cancelMeal(item.meal.id)
        val trigger = resolveMealTrigger(item) ?: return
        if (
            !preferences.remindersEnabled ||
            item.meal.period !in preferences.enabledMealPeriods ||
            item.meal.status != NutritionMealStatus.PLANNED
        ) return
        if (trigger <= System.currentTimeMillis()) return
        scheduleAlarm(
            trigger,
            mealPendingIntent(item, postponed = false, PendingIntent.FLAG_UPDATE_CURRENT)
        )
    }

    fun schedulePostpone(
        item: DatedNutritionMeal,
        preferences: NutritionPreferences,
        minutes: Int = DEFAULT_POSTPONE_MINUTES
    ) {
        cancelPostponedMeal(item.meal.id)
        if (
            !preferences.remindersEnabled ||
            item.meal.period !in preferences.enabledMealPeriods ||
            item.meal.status != NutritionMealStatus.PLANNED
        ) return
        val trigger = System.currentTimeMillis() + minutes.coerceIn(1, 180) * 60_000L
        scheduleAlarm(
            trigger,
            mealPendingIntent(item, postponed = true, PendingIntent.FLAG_UPDATE_CURRENT)
        )
    }

    fun cancelMeal(mealId: Int) {
        cancelMealAlarm(mealId, postponed = false)
        cancelPostponedMeal(mealId)
        NutritionNotificationHelper(appContext).cancelMealNotification(mealId)
    }

    fun scheduleNextHydrationReminder(
        preferences: NutritionPreferences,
        now: ZonedDateTime = ZonedDateTime.now()
    ) {
        cancelHydrationReminder()
        if (!preferences.hydrationEnabled || !preferences.remindersEnabled) return
        val start = preferences.hydrationReminderStartMinutes ?: return
        val end = preferences.hydrationReminderEndMinutes ?: return
        val interval = preferences.hydrationReminderIntervalMinutes?.takeIf { it > 0 } ?: return
        val trigger = nextHydrationTrigger(start, end, interval, now) ?: return
        val intent = Intent(appContext, NutritionAlarmReceiver::class.java).apply {
            data = "voicereminder://nutrition/alarm/hydration".toUri()
            putExtra(NutritionAlarmReceiver.EXTRA_ALARM_KIND, NutritionAlarmKind.HYDRATION.name)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            HYDRATION_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleAlarm(trigger, pendingIntent)
    }

    fun cancelHydrationReminder() {
        val intent = Intent(appContext, NutritionAlarmReceiver::class.java).apply {
            data = "voicereminder://nutrition/alarm/hydration".toUri()
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            HYDRATION_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    suspend fun syncAll(repository: NutritionRepository) {
        val preferences = repository.getPreferences()
        val today = LocalDate.now()
        repository.getPlans(today, today.plusYears(1)).flatMap { plan ->
            plan.meals.map { meal -> DatedNutritionMeal(plan.date, meal) }
        }.forEach { syncMeal(it, preferences) }
        scheduleNextHydrationReminder(preferences)
    }

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun resolveMealTrigger(item: DatedNutritionMeal): Long? = when (item.meal.reminderType) {
        NutritionReminderType.NONE -> null
        NutritionReminderType.CUSTOM -> item.meal.customReminderAtEpochMillis
        NutritionReminderType.EXACT_TIME -> item.meal.time?.let { time ->
            item.date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        NutritionReminderType.MINUTES_BEFORE -> item.meal.time?.let { time ->
            item.date.atTime(time)
                .minusMinutes((item.meal.reminderMinutesBefore ?: 0).toLong())
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }
    }

    private fun nextHydrationTrigger(
        startMinutes: Int,
        endMinutes: Int,
        intervalMinutes: Int,
        now: ZonedDateTime
    ): Long? {
        val safeStart = startMinutes.coerceIn(0, 1439)
        val safeEnd = endMinutes.coerceIn(safeStart, 1439)
        val minuteOfDay = now.hour * 60 + now.minute
        val targetDay: LocalDate
        val targetMinute: Int
        when {
            minuteOfDay < safeStart -> {
                targetDay = now.toLocalDate()
                targetMinute = safeStart
            }
            minuteOfDay >= safeEnd -> {
                targetDay = now.toLocalDate().plusDays(1)
                targetMinute = safeStart
            }
            else -> {
                val elapsed = minuteOfDay - safeStart
                val nextSlot = safeStart + ((elapsed / intervalMinutes) + 1) * intervalMinutes
                if (nextSlot <= safeEnd) {
                    targetDay = now.toLocalDate()
                    targetMinute = nextSlot
                } else {
                    targetDay = now.toLocalDate().plusDays(1)
                    targetMinute = safeStart
                }
            }
        }
        return targetDay.atStartOfDay(now.zone).plusMinutes(targetMinute.toLong())
            .toInstant().toEpochMilli()
    }

    private fun mealPendingIntent(
        item: DatedNutritionMeal,
        postponed: Boolean,
        flags: Int
    ): PendingIntent {
        val intent = Intent(appContext, NutritionAlarmReceiver::class.java).apply {
            data = alarmUri(item.meal.id, postponed)
            putExtra(NutritionAlarmReceiver.EXTRA_ALARM_KIND, NutritionAlarmKind.MEAL.name)
            putExtra(NutritionAlarmReceiver.EXTRA_MEAL_ID, item.meal.id)
            putExtra(NutritionAlarmReceiver.EXTRA_EXPECTED_UPDATED_AT, item.meal.updatedAtEpochMillis)
        }
        return PendingIntent.getBroadcast(
            appContext,
            requestCode(item.meal.id, postponed),
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cancelPostponedMeal(mealId: Int) = cancelMealAlarm(mealId, postponed = true)

    private fun cancelMealAlarm(mealId: Int, postponed: Boolean) {
        val intent = Intent(appContext, NutritionAlarmReceiver::class.java).apply {
            data = alarmUri(mealId, postponed)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            requestCode(mealId, postponed),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun scheduleAlarm(triggerAt: Long, pendingIntent: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun requestCode(mealId: Int, postponed: Boolean): Int =
        MEAL_REQUEST_CODE_OFFSET + mealId * 10 + if (postponed) 1 else 0

    private fun alarmUri(mealId: Int, postponed: Boolean) =
        "voicereminder://nutrition/alarm/meal/$mealId/${if (postponed) "postponed" else "base"}".toUri()

    private companion object {
        const val DEFAULT_POSTPONE_MINUTES = 10
        const val MEAL_REQUEST_CODE_OFFSET = 900_000
        const val HYDRATION_REQUEST_CODE = 999_999
    }
}

enum class NutritionAlarmKind { MEAL, HYDRATION }
