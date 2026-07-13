package com.luistureo.voicereminderapp.presentation.nutrition.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionTemplateMeal
import com.luistureo.voicereminderapp.presentation.nutrition.NutritionUiFormatter

class NutritionTemplatePreviewAdapter(
    private val onEdit: (NutritionTemplateMeal) -> Unit,
    private val onRemove: (NutritionTemplateMeal) -> Unit
) : RecyclerView.Adapter<NutritionTemplatePreviewAdapter.ViewHolder>() {
    private var items = emptyList<NutritionTemplateMeal>()
    fun submitList(value: List<NutritionTemplateMeal>) {
        items = value
        notifyDataSetChanged()
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_nutrition_template_preview_meal, parent, false)
    )
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.tvNutritionPreviewMealName)
        private val period: TextView = view.findViewById(R.id.tvNutritionPreviewMealPeriod)
        private val edit: MaterialButton = view.findViewById(R.id.btnNutritionPreviewMealEdit)
        private val remove: MaterialButton = view.findViewById(R.id.btnNutritionPreviewMealRemove)
        fun bind(item: NutritionTemplateMeal) {
            name.text = item.name
            period.text = NutritionUiFormatter.period(item.period)
            edit.setOnClickListener { onEdit(item) }
            remove.setOnClickListener { onRemove(item) }
        }
    }
}

