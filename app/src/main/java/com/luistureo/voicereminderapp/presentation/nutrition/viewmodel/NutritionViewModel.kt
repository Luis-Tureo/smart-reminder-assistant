package com.luistureo.voicereminderapp.presentation.nutrition.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luistureo.voicereminderapp.core.nutrition.NutritionScheduler
import com.luistureo.voicereminderapp.domain.nutrition.model.DatedNutritionMeal
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionChartRange
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMeal
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMealStatus
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionPreferences
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionShoppingItem
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionTemplate
import com.luistureo.voicereminderapp.domain.nutrition.repository.NutritionRepository
import com.luistureo.voicereminderapp.domain.nutrition.usecase.AddNutritionShoppingItemUseCase
import com.luistureo.voicereminderapp.domain.nutrition.usecase.ApplyNutritionTemplateUseCase
import com.luistureo.voicereminderapp.domain.nutrition.usecase.ChangeNutritionMealStatusUseCase
import com.luistureo.voicereminderapp.domain.nutrition.usecase.CopyNutritionDayUseCase
import com.luistureo.voicereminderapp.domain.nutrition.usecase.SaveNutritionMealUseCase
import com.luistureo.voicereminderapp.domain.nutrition.usecase.TrackHydrationUseCase
import com.luistureo.voicereminderapp.domain.nutrition.validation.NutritionHydrationSettingsPolicy
import com.luistureo.voicereminderapp.presentation.nutrition.state.NutritionDashboardSummary
import com.luistureo.voicereminderapp.presentation.nutrition.state.NutritionMealListItem
import com.luistureo.voicereminderapp.presentation.nutrition.state.NutritionUiState
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NutritionViewModel(
    private val repository: NutritionRepository,
    private val saveMeal: SaveNutritionMealUseCase,
    private val changeMealStatus: ChangeNutritionMealStatusUseCase,
    private val copyDay: CopyNutritionDayUseCase,
    private val trackHydration: TrackHydrationUseCase,
    private val addShoppingItem: AddNutritionShoppingItemUseCase,
    private val applyTemplate: ApplyNutritionTemplateUseCase,
    private val scheduler: NutritionScheduler,
    private val todayProvider: () -> LocalDate = LocalDate::now
) : ViewModel() {
    private val _uiState = MutableStateFlow(NutritionUiState())
    val uiState: StateFlow<NutritionUiState> = _uiState.asStateFlow()

    fun loadDashboard() = launchOperation {
        val today = todayProvider()
        val preferences = repository.getPreferences()
        val plan = repository.getPlan(today)
        val hydration = repository.getHydrationEntries(today, today)
        val enabledMeals = plan.meals.filter { it.period in preferences.enabledMealPeriods }
        _uiState.update {
            it.copy(
                selectedDate = today,
                plan = plan,
                mealItems = enabledMeals.map { meal -> NutritionMealListItem(today, meal) },
                hydrationEntries = hydration,
                preferences = preferences,
                preferencesLoaded = true,
                dashboardSummary = NutritionDashboardSummary(
                    plannedMeals = enabledMeals.size,
                    completedMeals = enabledMeals.count { meal ->
                        meal.status == NutritionMealStatus.COMPLETED
                    },
                    pendingReminders = enabledMeals.count { meal ->
                        meal.status == NutritionMealStatus.PLANNED &&
                            meal.reminderType != com.luistureo.voicereminderapp.domain.nutrition.model.NutritionReminderType.NONE
                    },
                    hydrationMl = hydration.sumOf { entry -> entry.amountMl },
                    hydrationTargetMl = preferences.hydrationTargetMl
                )
            )
        }
    }

    fun loadPlanning(date: LocalDate, weekly: Boolean) = launchOperation {
        val end = if (weekly) date.plusDays(6) else date
        val plans = repository.getPlans(date, end)
        _uiState.update {
            it.copy(
                selectedDate = date,
                isWeeklyMode = weekly,
                mealItems = plans.flatMap { plan ->
                    plan.meals.map { meal -> NutritionMealListItem(plan.date, meal) }
                }
            )
        }
    }

    fun loadMeal(mealId: Int) = launchOperation {
        _uiState.update { it.copy(selectedMeal = repository.getMeal(mealId)) }
    }

    fun saveMeal(date: LocalDate, meal: NutritionMeal, onSaved: (Int) -> Unit) {
        viewModelScope.launch {
            runCatching { saveMeal.invoke(date, meal) }
                .onSuccess { saved ->
                    val preferences = repository.getPreferences()
                    scheduler.syncMeal(saved, preferences)
                    _uiState.update { it.copy(selectedMeal = saved, message = "Comida guardada.") }
                    onSaved(saved.meal.id)
                }
                .onFailure(::showError)
        }
    }

    fun deleteMeal(item: NutritionMealListItem, onDeleted: () -> Unit = {}) = launchOperation(
        successMessage = "Comida eliminada."
    ) {
        repository.deleteMeal(item.meal.id)
        scheduler.cancelMeal(item.meal.id)
        reloadPlanningState()
        onDeleted()
    }

    fun duplicateMeal(item: NutritionMealListItem) = launchOperation(
        successMessage = "Comida duplicada."
    ) {
        repository.duplicateMeal(item.meal.id)?.let { duplicated ->
            scheduler.syncMeal(duplicated, repository.getPreferences())
        }
        reloadPlanningState()
    }

    fun moveMeal(item: NutritionMealListItem, targetDate: LocalDate) = launchOperation(
        successMessage = "Comida movida."
    ) {
        scheduler.cancelMeal(item.meal.id)
        repository.moveMeal(item.meal.id, targetDate)?.let { moved ->
            scheduler.syncMeal(moved, repository.getPreferences())
        }
        reloadPlanningState()
    }

    fun copyDay(source: LocalDate, target: LocalDate) = launchOperation(
        successMessage = "Día copiado. Puedes editar cada comida."
    ) {
        val preferences = repository.getPreferences()
        copyDay.invoke(source, target).forEach { scheduler.syncMeal(it, preferences) }
        reloadPlanningState()
    }

    fun setMealStatus(item: NutritionMealListItem, status: NutritionMealStatus) = launchOperation {
        changeMealStatus(item.meal.id, status)?.let { updated ->
            if (status == NutritionMealStatus.PLANNED) {
                scheduler.syncMeal(updated, repository.getPreferences())
            } else {
                scheduler.cancelMeal(item.meal.id)
            }
        }
        reloadPlanningState()
    }

    fun loadHydration(date: LocalDate = todayProvider()) = launchOperation {
        _uiState.update {
            it.copy(
                selectedDate = date,
                hydrationEntries = repository.getHydrationEntries(date, date),
                preferences = repository.getPreferences(),
                preferencesLoaded = true
            )
        }
    }

    fun addHydration(amountMl: Int) = launchOperation {
        trackHydration(_uiState.value.selectedDate, amountMl)
        loadHydrationValues(_uiState.value.selectedDate)
    }

    fun deleteHydrationEntry(entryId: Int) = launchOperation {
        repository.deleteHydrationEntry(entryId)
        loadHydrationValues(_uiState.value.selectedDate)
    }

    fun saveHydrationSettings(preferences: NutritionPreferences) = launchOperation(
        successMessage = "Configuración de hidratación guardada."
    ) {
        NutritionHydrationSettingsPolicy.requireValid(preferences)
        repository.savePreferences(preferences)
        scheduler.scheduleNextHydrationReminder(preferences)
        _uiState.update { it.copy(preferences = preferences, preferencesLoaded = true) }
    }

    fun loadShopping() = launchOperation {
        _uiState.update { it.copy(shoppingItems = repository.getShoppingItems()) }
    }

    fun addShopping(
        name: String,
        quantityOrNote: String?,
        category: String,
        increaseExisting: Boolean = false
    ) = launchOperation {
        val result = addShoppingItem(name, quantityOrNote, category, increaseExisting)
        _uiState.update {
            it.copy(
                shoppingItems = repository.getShoppingItems(),
                duplicateShoppingCandidate = result.item.takeIf { result.duplicateFound }
            )
        }
    }

    fun resolveShoppingDuplicate(
        candidate: NutritionShoppingItem,
        quantityOrNote: String?
    ) = launchOperation {
        addShoppingItem(candidate.name, quantityOrNote, candidate.category, increaseExisting = true)
        _uiState.update {
            it.copy(
                shoppingItems = repository.getShoppingItems(),
                duplicateShoppingCandidate = null
            )
        }
    }

    fun dismissShoppingDuplicate() {
        _uiState.update { it.copy(duplicateShoppingCandidate = null) }
    }

    fun setShoppingChecked(itemId: Int, checked: Boolean) = launchOperation {
        repository.setShoppingItemChecked(itemId, checked)
        _uiState.update { it.copy(shoppingItems = repository.getShoppingItems()) }
    }

    fun removeCompletedShopping() = launchOperation {
        repository.archiveCompletedShoppingItems()
        _uiState.update { it.copy(shoppingItems = repository.getShoppingItems()) }
    }

    fun clearShopping() = launchOperation {
        repository.clearShoppingList()
        _uiState.update { it.copy(shoppingItems = repository.getShoppingItems()) }
    }

    fun addShoppingFromPlan(date: LocalDate = todayProvider()) = launchOperation {
        val added = repository.addShoppingFromPlan(date)
        _uiState.update {
            it.copy(
                shoppingItems = repository.getShoppingItems(),
                message = if (added == 0) {
                    "No se encontraron productos nuevos en la planificación."
                } else {
                    "$added productos agregados desde la planificación."
                }
            )
        }
    }

    fun loadTemplates() = launchOperation {
        repository.initializeBuiltInTemplates()
        _uiState.update { it.copy(templates = repository.getTemplates()) }
    }

    fun loadTemplate(templateId: Int) = launchOperation {
        _uiState.update { it.copy(selectedTemplate = repository.getTemplate(templateId)) }
    }

    fun applyTemplate(template: NutritionTemplate, date: LocalDate, onApplied: () -> Unit) {
        viewModelScope.launch {
            runCatching { applyTemplate.invoke(template, date) }
                .onSuccess { meals ->
                    val preferences = repository.getPreferences()
                    meals.forEach { scheduler.syncMeal(it, preferences) }
                    _uiState.update { it.copy(message = "Plantilla aplicada. Puedes editar cada comida.") }
                    onApplied()
                }
                .onFailure(::showError)
        }
    }

    fun loadPreferences() = launchOperation {
        _uiState.update {
            it.copy(preferences = repository.getPreferences(), preferencesLoaded = true)
        }
    }

    fun savePreferences(preferences: NutritionPreferences, onSaved: () -> Unit = {}) =
        launchOperation(successMessage = "Preferencias guardadas.") {
            repository.savePreferences(preferences)
            scheduler.syncAll(repository)
            _uiState.update { it.copy(preferences = preferences, preferencesLoaded = true) }
            onSaved()
        }

    fun loadStatistics(range: NutritionChartRange) = launchOperation {
        _uiState.update {
            it.copy(statistics = repository.getStatistics(range, todayProvider()))
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    private suspend fun reloadPlanningState() {
        val state = _uiState.value
        val end = if (state.isWeeklyMode) state.selectedDate.plusDays(6) else state.selectedDate
        val plans = repository.getPlans(state.selectedDate, end)
        _uiState.update {
            it.copy(mealItems = plans.flatMap { plan ->
                plan.meals.map { meal -> NutritionMealListItem(plan.date, meal) }
            })
        }
    }

    private suspend fun loadHydrationValues(date: LocalDate) {
        _uiState.update {
            it.copy(hydrationEntries = repository.getHydrationEntries(date, date))
        }
    }

    private fun launchOperation(
        successMessage: String? = null,
        block: suspend () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching { block() }
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            message = successMessage ?: state.message
                        )
                    }
                }
                .onFailure(::showError)
        }
    }

    private fun showError(error: Throwable) {
        _uiState.update {
            it.copy(
                isLoading = false,
                message = error.message ?: "No fue posible completar la acción."
            )
        }
    }
}
