package com.luistureo.voicereminderapp.presentation.routine

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.luistureo.voicereminderapp.domain.routine.model.RoutineChartType
import com.luistureo.voicereminderapp.domain.routine.model.RoutineExecutionState
import com.luistureo.voicereminderapp.domain.routine.model.RoutinePeriod
import com.luistureo.voicereminderapp.domain.routine.model.RoutineStatistics
import java.time.format.DateTimeFormatter

class RoutineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 34f }
    private var statistics: RoutineStatistics? = null
    private var type: RoutineChartType = RoutineChartType.BAR

    fun render(statistics: RoutineStatistics, type: RoutineChartType) {
        this.statistics = statistics
        this.type = type
        contentDescription = buildDescription(statistics)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val stats = statistics ?: return
        when (type) {
            RoutineChartType.BAR -> drawBars(canvas, stats)
            RoutineChartType.CIRCULAR -> drawCircle(canvas, stats)
            RoutineChartType.CALENDAR -> drawCalendar(canvas, stats)
            RoutineChartType.PERCENTAGE_LIST -> drawList(canvas, stats)
        }
    }

    private fun drawBars(canvas: Canvas, stats: RoutineStatistics) {
        val labels = mapOf(RoutinePeriod.MORNING to "Mañana", RoutinePeriod.AFTERNOON to "Tarde", RoutinePeriod.NIGHT to "Noche")
        stats.periodPercentages.forEachIndexed { index, value ->
            val top = 32f + index * 82f
            paint.color = Color.DKGRAY
            canvas.drawText("${labels[value.period]} — ${value.percentage} %", 20f, top + 28f, paint)
            paint.color = 0xFF496A84.toInt()
            canvas.drawRoundRect(20f, top + 38f, 20f + (width - 40f) * value.percentage / 100f,
                top + 62f, 12f, 12f, paint)
        }
    }

    private fun drawCircle(canvas: Canvas, stats: RoutineStatistics) {
        val size = minOf(width, height).toFloat() - 60f
        val oval = RectF((width - size) / 2, 20f, (width + size) / 2, 20f + size)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 34f
        paint.color = 0xFFE0E4E8.toInt()
        canvas.drawOval(oval, paint)
        paint.color = 0xFF496A84.toInt()
        canvas.drawArc(oval, -90f, 360f * stats.taskCompletionPercentage / 100f, false, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.DKGRAY
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 46f
        canvas.drawText("${stats.taskCompletionPercentage} %", width / 2f, oval.centerY() + 16f, paint)
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 34f
    }

    private fun drawCalendar(canvas: Canvas, stats: RoutineStatistics) {
        val formatter = DateTimeFormatter.ofPattern("dd/MM")
        stats.dailyStates.entries.sortedBy { it.key }.takeLast(28).forEachIndexed { index, entry ->
            val column = index % 7
            val row = index / 7
            val cellWidth = width / 7f
            val x = column * cellWidth + 8f
            val y = row * 66f + 38f
            paint.color = colorFor(entry.value)
            canvas.drawCircle(x + 18f, y - 10f, 16f, paint)
            paint.color = Color.WHITE
            paint.textSize = 18f
            canvas.drawText(labelFor(entry.value), x + 11f, y - 4f, paint)
            paint.color = Color.DKGRAY
            paint.textSize = 22f
            canvas.drawText(formatter.format(entry.key), x, y + 22f, paint)
        }
        paint.textSize = 34f
    }

    private fun drawList(canvas: Canvas, stats: RoutineStatistics) = drawBars(canvas, stats)

    private fun colorFor(state: RoutineExecutionState): Int = when (state) {
        RoutineExecutionState.COMPLETED -> 0xFF3C7A57.toInt()
        RoutineExecutionState.PARTIALLY_COMPLETED -> 0xFFC98524.toInt()
        RoutineExecutionState.SKIPPED -> 0xFF6C7480.toInt()
        else -> 0xFFB24C4C.toInt()
    }

    private fun labelFor(state: RoutineExecutionState): String = when (state) {
        RoutineExecutionState.COMPLETED -> "C"
        RoutineExecutionState.PARTIALLY_COMPLETED -> "P"
        RoutineExecutionState.SKIPPED -> "O"
        else -> "N"
    }

    private fun buildDescription(stats: RoutineStatistics): String =
        "Progreso ${stats.taskCompletionPercentage} por ciento. " +
            stats.periodPercentages.joinToString { "${it.period}: ${it.percentage} por ciento" }
}
