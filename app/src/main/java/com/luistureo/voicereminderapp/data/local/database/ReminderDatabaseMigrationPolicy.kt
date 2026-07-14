package com.luistureo.voicereminderapp.data.local.database

object ReminderDatabaseMigrationPolicy {
    val supportedMigrationRanges: List<Pair<Int, Int>> = listOf(
        1 to 2,
        2 to 3,
        3 to 6,
        6 to 7,
        7 to 8,
        8 to 9,
        9 to 10,
        10 to 11,
        11 to 12,
        12 to 13,
        13 to 14,
        14 to 15,
        15 to 16
    )
    const val USE_DESTRUCTIVE_MIGRATION: Boolean = false
}
