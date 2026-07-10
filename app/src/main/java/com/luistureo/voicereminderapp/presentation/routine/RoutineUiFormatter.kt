package com.luistureo.voicereminderapp.presentation.routine

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.domain.routine.model.Routine
import com.luistureo.voicereminderapp.domain.routine.model.RoutinePeriod
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object RoutineUiFormatter {
    @DrawableRes
    fun icon(period: RoutinePeriod): Int = when (period) {
        RoutinePeriod.MORNING -> R.drawable.ic_routine_morning
        RoutinePeriod.AFTERNOON -> R.drawable.ic_routine_afternoon
        RoutinePeriod.NIGHT -> R.drawable.ic_routine_night
    }

    @DrawableRes
    fun icon(routine: Routine): Int = when (routine.icon) {
        "light_mode" -> R.drawable.ic_routine_afternoon
        "bedtime" -> R.drawable.ic_routine_night
        "wb_sunny" -> R.drawable.ic_routine_morning
        else -> icon(routine.period)
    }

    @StringRes
    fun periodLabel(period: RoutinePeriod): Int = when (period) {
        RoutinePeriod.MORNING -> R.string.routine_morning
        RoutinePeriod.AFTERNOON -> R.string.routine_afternoon
        RoutinePeriod.NIGHT -> R.string.routine_night
    }

    fun time(time: LocalTime): String = time.format(TIME_FORMATTER)

    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
}
