package com.luistureo.voicereminderapp.core.modules

import android.content.Context

class ModuleSelectionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun isSelectionCompleted(): Boolean = preferences.getBoolean(
        KEY_MODULE_SELECTION_COMPLETED,
        false
    )

    fun selectedModuleIds(): LinkedHashSet<String> {
        val stored = preferences.getStringSet(KEY_SELECTED_HOME_MODULES, emptySet())
            ?.toSet()
            .orEmpty()
        return HomeModuleRegistry.sanitizeIds(stored)
    }

    fun saveSelection(moduleIds: Set<String>): Boolean {
        val sanitized = HomeModuleRegistry.sanitizeIds(moduleIds)
        if (sanitized.isEmpty()) return false

        return preferences.edit()
            .putStringSet(KEY_SELECTED_HOME_MODULES, sanitized)
            .putBoolean(KEY_MODULE_SELECTION_COMPLETED, true)
            .commit()
    }

    companion object {
        const val KEY_MODULE_SELECTION_COMPLETED = "module_selection_completed"
        const val KEY_SELECTED_HOME_MODULES = "selected_home_modules"
        private const val PREFERENCES_NAME = "home_module_selection"
    }
}
