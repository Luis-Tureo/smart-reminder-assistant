package com.luistureo.voicereminderapp.presentation.nutrition.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionHydrationEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class NutritionHydrationAdapter(
    private val onDelete: (NutritionHydrationEntry) -> Unit
) : RecyclerView.Adapter<NutritionHydrationAdapter.ViewHolder>() {
    private var items = emptyList<NutritionHydrationEntry>()
    fun submitList(value: List<NutritionHydrationEntry>) {
        items = value
        notifyDataSetChanged()
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_nutrition_hydration, parent, false)
    )
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val amount: TextView = view.findViewById(R.id.tvNutritionHydrationAmount)
        private val time: TextView = view.findViewById(R.id.tvNutritionHydrationTime)
        private val delete: MaterialButton = view.findViewById(R.id.btnNutritionHydrationDelete)
        fun bind(item: NutritionHydrationEntry) {
            amount.text = itemView.context.getString(R.string.nutrition_hydration_amount, item.amountMl)
            time.text = Instant.ofEpochMilli(item.loggedAtEpochMillis)
                .atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
            delete.setOnClickListener { onDelete(item) }
        }
    }
}

