package com.luistureo.voicereminderapp.data.repository

import android.content.Context
import com.luistureo.voicereminderapp.data.local.database.ReminderDatabase
import com.luistureo.voicereminderapp.domain.nutrition.repository.NutritionRepository

object NutritionRepositoryProvider {
    fun from(context: Context): NutritionRepository = NutritionRepositoryImpl(
        ReminderDatabase.getDatabase(context.applicationContext).nutritionDao()
    )
}

