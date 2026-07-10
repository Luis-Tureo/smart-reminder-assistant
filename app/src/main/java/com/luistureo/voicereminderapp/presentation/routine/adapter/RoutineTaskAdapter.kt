package com.luistureo.voicereminderapp.presentation.routine.adapter

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.domain.routine.model.RoutineTask
import com.luistureo.voicereminderapp.presentation.routine.RoutineUiFormatter

class RoutineTaskAdapter(
    private val onToggle: (RoutineTask, Boolean) -> Unit
) : ListAdapter<RoutineTask, RoutineTaskAdapter.ViewHolder>(Diff) {
    var interactionsEnabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount, PAYLOAD_ENABLED)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_routine_task, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_ENABLED)) holder.updateEnabled() else super.onBindViewHolder(
            holder,
            position,
            payloads
        )
    }

    inner class ViewHolder(parent: View) : RecyclerView.ViewHolder(parent) {
        private val checkbox: MaterialCheckBox = parent.findViewById(R.id.checkRoutineTask)
        private val timeIcon: ImageView = parent.findViewById(R.id.imageRoutineTaskTime)
        private val title: TextView = parent.findViewById(R.id.tvRoutineTaskTitle)
        private val metadata: TextView = parent.findViewById(R.id.tvRoutineTaskMetadata)

        fun bind(task: RoutineTask) {
            checkbox.setOnCheckedChangeListener(null)
            checkbox.isChecked = task.completed
            title.text = task.title
            title.paintFlags = if (task.completed) {
                title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            val metadataParts = buildList {
                task.optionalTime?.let { add(RoutineUiFormatter.time(it)) }
                task.estimatedDurationMinutes?.let {
                    add(itemView.context.getString(R.string.routine_task_duration, it))
                }
            }
            metadata.text = metadataParts.joinToString(" · ")
            metadata.visibility = if (metadataParts.isEmpty()) View.GONE else View.VISIBLE
            timeIcon.visibility = if (task.optionalTime == null) View.GONE else View.VISIBLE
            checkbox.contentDescription = itemView.context.getString(
                R.string.routine_task_checkbox_description,
                task.title
            )
            updateEnabled()
            checkbox.setOnCheckedChangeListener { _, checked -> onToggle(task, checked) }
        }

        fun updateEnabled() {
            checkbox.isEnabled = interactionsEnabled
            itemView.alpha = if (interactionsEnabled) 1f else 0.72f
        }
    }

    private object Diff : DiffUtil.ItemCallback<RoutineTask>() {
        override fun areItemsTheSame(oldItem: RoutineTask, newItem: RoutineTask) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: RoutineTask, newItem: RoutineTask) = oldItem == newItem
    }

    private companion object {
        const val PAYLOAD_ENABLED = "enabled"
    }
}
