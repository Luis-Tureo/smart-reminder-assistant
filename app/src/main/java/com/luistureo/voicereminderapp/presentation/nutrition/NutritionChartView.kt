package com.luistureo.voicereminderapp.presentation.nutrition

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionStatistics
import kotlin.math.max

class NutritionChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.nutrition_primary)
    }
    private val completedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.nutrition_success)
    }
    private var statistics: NutritionStatistics? = null

    fun setStatistics(value: NutritionStatistics?) {
        statistics = value
        contentDescription = value?.let {
            context.getString(
                R.string.nutrition_statistics_accessibility,
                it.plannedMeals,
                it.completedMeals,
                it.hydrationMl
            )
        } ?: context.getString(R.string.nutrition_statistics_empty)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val values = statistics?.dailyValues.orEmpty()
        if (values.isEmpty()) return
        val maxValue = max(1, values.maxOf { max(it.plannedMeals, it.hydrationMl / 250) })
        val slot = width.toFloat() / values.size
        val baseline = height - paddingBottom.toFloat()
        val available = height - paddingTop - paddingBottom.toFloat()
        values.forEachIndexed { index, value ->
            val left = index * slot + slot * 0.15f
            val middle = index * slot + slot * 0.5f
            val right = index * slot + slot * 0.85f
            val plannedHeight = available * value.plannedMeals / maxValue
            val completedHeight = available * value.completedMeals / maxValue
            canvas.drawRect(left, baseline - plannedHeight, middle, baseline, barPaint)
            canvas.drawRect(middle, baseline - completedHeight, right, baseline, completedPaint)
        }
    }
}

