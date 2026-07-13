package com.luistureo.voicereminderapp.presentation.nutrition.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionShoppingItem

class NutritionShoppingAdapter(
    private val onChecked: (NutritionShoppingItem, Boolean) -> Unit
) : RecyclerView.Adapter<NutritionShoppingAdapter.ViewHolder>() {
    private var items = emptyList<NutritionShoppingItem>()
    fun submitList(value: List<NutritionShoppingItem>) {
        items = value
        notifyDataSetChanged()
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_nutrition_shopping, parent, false)
    )
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val check: MaterialCheckBox = view.findViewById(R.id.checkNutritionShoppingItem)
        private val detail: TextView = view.findViewById(R.id.tvNutritionShoppingDetail)
        fun bind(item: NutritionShoppingItem) {
            check.setOnCheckedChangeListener(null)
            check.text = item.name
            check.isChecked = item.checked
            detail.text = listOfNotNull(item.quantityOrNote, item.category).joinToString(" · ")
            check.setOnCheckedChangeListener { _, checked -> onChecked(item, checked) }
        }
    }
}

