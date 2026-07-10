package com.luistureo.voicereminderapp.presentation.routine.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTemplate

class RoutineTemplateAdapter(
    private val onView: (RoutineTemplate) -> Unit,
    private val onUse: (RoutineTemplate) -> Unit,
    private val onDelete: (RoutineTemplate) -> Unit
) : ListAdapter<RoutineTemplate, RoutineTemplateAdapter.ViewHolder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_routine_template, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(parent: android.view.View) : RecyclerView.ViewHolder(parent) {
        private val name: TextView = parent.findViewById(R.id.tvRoutineTemplateName)
        private val description: TextView = parent.findViewById(R.id.tvRoutineTemplateDescription)
        private val benefits: TextView = parent.findViewById(R.id.tvRoutineTemplateBenefits)
        private val metadata: TextView = parent.findViewById(R.id.tvRoutineTemplateMetadata)
        private val view: MaterialButton = parent.findViewById(R.id.btnViewRoutineTemplate)
        private val use: MaterialButton = parent.findViewById(R.id.btnUseRoutineTemplate)
        private val delete: MaterialButton = parent.findViewById(R.id.btnDeleteRoutineTemplate)

        fun bind(template: RoutineTemplate) {
            name.text = template.name
            description.text = template.description
            benefits.text = template.benefitsExplanation
            metadata.text = itemView.resources.getQuantityString(
                R.plurals.routine_template_metadata,
                template.suggestedTasks.size,
                when (template.period) {
                    com.luistureo.voicereminderapp.domain.routine.model.RoutinePeriod.MORNING -> "Mañana"
                    com.luistureo.voicereminderapp.domain.routine.model.RoutinePeriod.AFTERNOON -> "Tarde"
                    com.luistureo.voicereminderapp.domain.routine.model.RoutinePeriod.NIGHT -> "Noche"
                },
                template.estimatedTotalDurationMinutes,
                template.suggestedTasks.size
            )
            view.setOnClickListener { onView(template) }
            use.setOnClickListener { onUse(template) }
            delete.visibility = if (template.builtIn) android.view.View.GONE else android.view.View.VISIBLE
            delete.setOnClickListener { onDelete(template) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<RoutineTemplate>() {
        override fun areItemsTheSame(oldItem: RoutineTemplate, newItem: RoutineTemplate) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: RoutineTemplate, newItem: RoutineTemplate) =
            oldItem == newItem
    }
}
