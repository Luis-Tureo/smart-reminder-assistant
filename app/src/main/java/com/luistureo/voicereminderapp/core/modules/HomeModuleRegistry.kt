package com.luistureo.voicereminderapp.core.modules

import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import com.luistureo.voicereminderapp.R

data class HomeModuleDefinition(
    val id: String,
    @param:StringRes val nameRes: Int,
    @param:StringRes val descriptionRes: Int,
    @param:DrawableRes val iconRes: Int,
    @param:IdRes val homeCardId: Int,
    val destinationClassName: String,
    val displayOrder: Int,
    val isAvailable: Boolean = true
)

object HomeModuleRegistry {
    const val CALENDAR = "calendar"
    const val LOANS = "loans"
    const val ROUTINES = "routines"
    const val NUTRITION = "nutrition"
    const val RECOVERY = "recovery"
    const val QUICK_NOTES = "quick_notes"

    val modules: List<HomeModuleDefinition> = listOf(
        HomeModuleDefinition(
            id = CALENDAR,
            nameRes = R.string.module_calendar_name,
            descriptionRes = R.string.module_calendar_description,
            iconRes = R.drawable.ic_reminder_calendar,
            homeCardId = R.id.cardCalendar,
            destinationClassName =
                "com.luistureo.voicereminderapp.presentation.calendar.CalendarActivity",
            displayOrder = 0
        ),
        HomeModuleDefinition(
            id = LOANS,
            nameRes = R.string.module_loans_name,
            descriptionRes = R.string.module_loans_description,
            iconRes = android.R.drawable.ic_menu_save,
            homeCardId = R.id.cardLoan,
            destinationClassName =
                "com.luistureo.voicereminderapp.presentation.loan.LoanListActivity",
            displayOrder = 1
        ),
        HomeModuleDefinition(
            id = ROUTINES,
            nameRes = R.string.module_routines_name,
            descriptionRes = R.string.module_routines_description,
            iconRes = R.drawable.ic_routine_morning,
            homeCardId = R.id.cardDailyRoutines,
            destinationClassName =
                "com.luistureo.voicereminderapp.presentation.routine.RoutineDashboardActivity",
            displayOrder = 2
        ),
        HomeModuleDefinition(
            id = NUTRITION,
            nameRes = R.string.module_nutrition_name,
            descriptionRes = R.string.module_nutrition_description,
            iconRes = android.R.drawable.ic_menu_agenda,
            homeCardId = R.id.cardNutrition,
            destinationClassName =
                "com.luistureo.voicereminderapp.presentation.nutrition.NutritionDashboardActivity",
            displayOrder = 3
        ),
        HomeModuleDefinition(
            id = RECOVERY,
            nameRes = R.string.module_recovery_name,
            descriptionRes = R.string.module_recovery_description,
            iconRes = android.R.drawable.ic_menu_compass,
            homeCardId = R.id.cardRecovery,
            destinationClassName =
                "com.luistureo.voicereminderapp.presentation.recovery.RecoveryDashboardActivity",
            displayOrder = 4
        ),
        HomeModuleDefinition(
            id = QUICK_NOTES,
            nameRes = R.string.module_quick_notes_name,
            descriptionRes = R.string.module_quick_notes_description,
            iconRes = R.drawable.ic_reminder_note,
            homeCardId = R.id.cardQuickNotes,
            destinationClassName =
                "com.luistureo.voicereminderapp.presentation.notes.QuickNotesActivity",
            displayOrder = 5
        )
    ).sortedBy(HomeModuleDefinition::displayOrder)

    val knownIds: Set<String> = modules.mapTo(linkedSetOf(), HomeModuleDefinition::id)

    fun sanitizeIds(ids: Iterable<String>): LinkedHashSet<String> = modules
        .asSequence()
        .filter(HomeModuleDefinition::isAvailable)
        .map(HomeModuleDefinition::id)
        .filter(ids.toSet()::contains)
        .toCollection(linkedSetOf())

    fun selectedModules(ids: Iterable<String>): List<HomeModuleDefinition> {
        val selected = ids.toSet()
        return modules.filter { it.isAvailable && it.id in selected }
    }
}
