package com.luistureo.voicereminderapp.domain.model

enum class ReminderWeekday(
    val isoDayNumber: Int,
    val shortLabel: String
) {
    MONDAY(1, "Lun"),
    TUESDAY(2, "Mar"),
    WEDNESDAY(3, "Mie"),
    THURSDAY(4, "Jue"),
    FRIDAY(5, "Vie"),
    SATURDAY(6, "Sab"),
    SUNDAY(7, "Dom");

    companion object {
        fun fromIsoDayNumber(isoDayNumber: Int): ReminderWeekday {
            return entries.first { it.isoDayNumber == isoDayNumber }
        }
    }
}
