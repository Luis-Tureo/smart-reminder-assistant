package com.luistureo.voicereminderapp.data.local.database

object ReminderDatabaseMigrationPolicy {
    val supportedMigrationRanges: List<Pair<Int, Int>> = listOf(6 to 7)
    const val USE_DESTRUCTIVE_MIGRATION: Boolean = false
}
