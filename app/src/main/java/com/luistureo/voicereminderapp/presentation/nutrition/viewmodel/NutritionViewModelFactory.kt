package com.luistureo.voicereminderapp.presentation.nutrition.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.luistureo.voicereminderapp.core.nutrition.NutritionScheduler
import com.luistureo.voicereminderapp.data.repository.NutritionRepositoryProvider
import com.luistureo.voicereminderapp.domain.nutrition.usecase.AddNutritionShoppingItemUseCase
import com.luistureo.voicereminderapp.domain.nutrition.usecase.ApplyNutritionTemplateUseCase
import com.luistureo.voicereminderapp.domain.nutrition.usecase.ChangeNutritionMealStatusUseCase
import com.luistureo.voicereminderapp.domain.nutrition.usecase.CopyNutritionDayUseCase
import com.luistureo.voicereminderapp.domain.nutrition.usecase.SaveNutritionMealUseCase
import com.luistureo.voicereminderapp.domain.nutrition.usecase.TrackHydrationUseCase

class NutritionViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repository = NutritionRepositoryProvider.from(appContext)
        return NutritionViewModel(
            repository = repository,
            saveMeal = SaveNutritionMealUseCase(repository),
            changeMealStatus = ChangeNutritionMealStatusUseCase(repository),
            copyDay = CopyNutritionDayUseCase(repository),
            trackHydration = TrackHydrationUseCase(repository),
            addShoppingItem = AddNutritionShoppingItemUseCase(repository),
            applyTemplate = ApplyNutritionTemplateUseCase(repository),
            scheduler = NutritionScheduler(appContext)
        ) as T
    }
}

