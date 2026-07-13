package com.luistureo.voicereminderapp.presentation.nutrition.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMealStatus
import com.luistureo.voicereminderapp.presentation.nutrition.NutritionUiFormatter
import com.luistureo.voicereminderapp.presentation.nutrition.state.NutritionMealListItem

class NutritionMealAdapter(
    private val compact: Boolean = false,
    private val onEdit: (NutritionMealListItem) -> Unit = {},
    private val onDuplicate: (NutritionMealListItem) -> Unit = {},
    private val onMove: (NutritionMealListItem) -> Unit = {},
    private val onComplete: (NutritionMealListItem) -> Unit = {},
    private val onSkip: (NutritionMealListItem) -> Unit = {}
) : RecyclerView.Adapter<NutritionMealAdapter.ViewHolder>() {
    private var items: List<NutritionMealListItem> = emptyList()

    fun submitList(value: List<NutritionMealListItem>) {
        items = value
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_nutrition_meal, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount(): Int = items.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val period: TextView = view.findViewById(R.id.tvNutritionMealPeriod)
        private val name: TextView = view.findViewById(R.id.tvNutritionMealName)
        private val dateTime: TextView = view.findViewById(R.id.tvNutritionMealDateTime)
        private val status: TextView = view.findViewById(R.id.tvNutritionMealStatus)
        private val edit: MaterialButton = view.findViewById(R.id.btnNutritionMealEdit)
        private val duplicate: MaterialButton = view.findViewById(R.id.btnNutritionMealDuplicate)
        private val move: MaterialButton = view.findViewById(R.id.btnNutritionMealMove)
        private val complete: MaterialButton = view.findViewById(R.id.btnNutritionMealComplete)
        private val skip: MaterialButton = view.findViewById(R.id.btnNutritionMealSkip)

        fun bind(item: NutritionMealListItem) {
            period.text = NutritionUiFormatter.period(item.meal.period)
            name.text = item.meal.name
            dateTime.text = buildString {
                append(NutritionUiFormatter.date(item.date))
                item.meal.time?.let { append(" · ").append(it.toString()) }
            }
            status.text = NutritionUiFormatter.status(item.meal.status)
            duplicate.isVisible = !compact
            move.isVisible = !compact
            edit.setOnClickListener { onEdit(item) }
            duplicate.setOnClickListener { onDuplicate(item) }
            move.setOnClickListener { onMove(item) }
            complete.isEnabled = item.meal.status != NutritionMealStatus.COMPLETED
            skip.isEnabled = item.meal.status != NutritionMealStatus.SKIPPED
            complete.setOnClickListener { onComplete(item) }
            skip.setOnClickListener { onSkip(item) }
        }
    }
}

