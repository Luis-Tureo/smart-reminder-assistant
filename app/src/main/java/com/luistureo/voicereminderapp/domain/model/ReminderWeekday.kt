package com.luistureo.voicereminderapp.domain.model

import java.time.DayOfWeek

enum class ReminderWeekday(
    val dayOfWeek: DayOfWeek,
    val shortLabel: String
) {
    MONDAY(DayOfWeek.MONDAY, "Lun"),
    TUESDAY(DayOfWeek.TUESDAY, "Mar"),
    WEDNESDAY(DayOfWeek.WEDNESDAY, "Mie"),
    THURSDAY(DayOfWeek.THURSDAY, "Jue"),
    FRIDAY(DayOfWeek.FRIDAY, "Vie"),
    SATURDAY(DayOfWeek.SATURDAY, "Sab"),
    SUNDAY(DayOfWeek.SUNDAY, "Dom");

    companion object {
        fun fromDayOfWeek(dayOfWeek: DayOfWeek): ReminderWeekday {
            return entries.first { it.dayOfWeek == dayOfWeek }
        }
    }
}
