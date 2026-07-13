package com.luistureo.voicereminderapp.presentation.nutrition.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionTemplate

class NutritionTemplateAdapter(
    private val onPreview: (NutritionTemplate) -> Unit
) : RecyclerView.Adapter<NutritionTemplateAdapter.ViewHolder>() {
    private var items = emptyList<NutritionTemplate>()
    fun submitList(value: List<NutritionTemplate>) {
        items = value
        notifyDataSetChanged()
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_nutrition_template, parent, false)
    )
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.tvNutritionTemplateName)
        private val description: TextView = view.findViewById(R.id.tvNutritionTemplateDescription)
        private val detail: TextView = view.findViewById(R.id.tvNutritionTemplateDetail)
        private val preview: MaterialButton = view.findViewById(R.id.btnNutritionTemplatePreview)
        fun bind(item: NutritionTemplate) {
            name.text = item.name
            description.text = item.description
            detail.text = itemView.context.getString(
                R.string.nutrition_template_detail,
                item.preparationComplexity,
                item.practicalBenefits
            )
            preview.setOnClickListener { onPreview(item) }
        }
    }
}

