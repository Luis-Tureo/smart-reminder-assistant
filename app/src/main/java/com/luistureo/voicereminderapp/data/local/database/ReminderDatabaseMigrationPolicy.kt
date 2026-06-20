package com.luistureo.voicereminderapp.data.local.database

object ReminderDatabaseMigrationPolicy {
    val supportedMigrationRanges: List<Pair<Int, Int>> = listOf(
        6 to 7,
        7 to 8,
        8 to 9,
        9 to 10,
        10 to 11,
        11 to 12
    )
    const val USE_DESTRUCTIVE_MIGRATION: Boolean = false
}
