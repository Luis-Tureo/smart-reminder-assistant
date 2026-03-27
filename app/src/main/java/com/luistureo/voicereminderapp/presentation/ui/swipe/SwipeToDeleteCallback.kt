package com.luistureo.voicereminderapp.presentation.ui.swipe

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class SwipeToDeleteCallback(
    private val onSwiped: (position: Int) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

    private val backgroundPaint = Paint().apply {
        color = Color.RED
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 48f
        isAntiAlias = true
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        onSwiped(viewHolder.adapterPosition)
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val itemView = viewHolder.itemView

        // Fondo rojo
        c.drawRect(
            itemView.right + dX,
            itemView.top.toFloat(),
            itemView.right.toFloat(),
            itemView.bottom.toFloat(),
            backgroundPaint
        )

        // Texto "Delete"
        c.drawText(
            "Delete",
            itemView.right - 200f,
            itemView.top + (itemView.height / 2f),
            textPaint
        )

        super.onChildDraw(
            c,
            recyclerView,
            viewHolder,
            dX,
            dY,
            actionState,
            isCurrentlyActive
        )
    }
}