package com.luistureo.voicereminderapp.presentation.modules

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.modules.HomeModuleDefinition

class ModuleSelectionAdapter(
    private val modules: List<HomeModuleDefinition>,
    selectedIds: Set<String>,
    private val onSelectionChanged: (Set<String>) -> Unit
) : RecyclerView.Adapter<ModuleSelectionAdapter.ModuleViewHolder>() {

    private val selected = selectedIds.toMutableSet()

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = modules[position].id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModuleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_module_selection, parent, false)
        return ModuleViewHolder(view as MaterialCardView)
    }

    override fun onBindViewHolder(holder: ModuleViewHolder, position: Int) {
        holder.bind(modules[position])
    }

    override fun getItemCount(): Int = modules.size

    fun selectedIds(): Set<String> = selected.toSet()

    fun selectAll() {
        selected.clear()
        modules.filter(HomeModuleDefinition::isAvailable).mapTo(selected) { it.id }
        notifyItemRangeChanged(0, itemCount)
        onSelectionChanged(selectedIds())
    }

    fun clearSelection() {
        selected.clear()
        notifyItemRangeChanged(0, itemCount)
        onSelectionChanged(emptySet())
    }

    private fun updateSelection(module: HomeModuleDefinition, checked: Boolean) {
        if (checked) selected.add(module.id) else selected.remove(module.id)
        val index = modules.indexOfFirst { it.id == module.id }
        if (index >= 0) notifyItemChanged(index)
        onSelectionChanged(selectedIds())
    }

    inner class ModuleViewHolder(
        private val card: MaterialCardView
    ) : RecyclerView.ViewHolder(card) {
        private val icon: ImageView = card.findViewById(R.id.ivModuleIcon)
        private val name: TextView = card.findViewById(R.id.tvModuleName)
        private val description: TextView = card.findViewById(R.id.tvModuleDescription)
        private val checkBox: CheckBox = card.findViewById(R.id.checkModuleSelected)

        fun bind(module: HomeModuleDefinition) {
            val context = card.context
            val moduleName = context.getString(module.nameRes)
            val moduleDescription = context.getString(module.descriptionRes)
            val isChecked = module.id in selected

            icon.setImageResource(module.iconRes)
            icon.contentDescription = null
            name.setText(module.nameRes)
            description.setText(module.descriptionRes)
            card.isEnabled = module.isAvailable
            card.isCheckable = true
            card.isChecked = isChecked
            card.contentDescription = context.getString(
                if (isChecked) {
                    R.string.module_selection_selected_description
                } else {
                    R.string.module_selection_unselected_description
                },
                moduleName,
                moduleDescription
            )

            checkBox.setOnCheckedChangeListener(null)
            checkBox.isEnabled = module.isAvailable
            checkBox.isChecked = isChecked
            checkBox.contentDescription = context.getString(
                R.string.module_selection_checkbox_description,
                moduleName
            )
            checkBox.setOnCheckedChangeListener { _, checked ->
                updateSelection(module, checked)
            }
            card.setOnClickListener {
                if (module.isAvailable) updateSelection(module, module.id !in selected)
            }
        }
    }
}
