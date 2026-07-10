package com.luistureo.voicereminderapp.presentation.routine.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.presentation.routine.RoutineUiFormatter
import com.luistureo.voicereminderapp.presentation.routine.state.RoutineTaskDraft

class RoutineEditorTaskAdapter(
    private val onEdit: (RoutineTaskDraft) -> Unit,
    private val onDelete: (RoutineTaskDraft) -> Unit
) : ListAdapter<RoutineTaskDraft, RoutineEditorTaskAdapter.ViewHolder>(Diff) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_routine_editor_task, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(parent: View) : RecyclerView.ViewHolder(parent) {
        private val drag: ImageView = parent.findViewById(R.id.imageRoutineTaskDrag)
        private val title: TextView = parent.findViewById(R.id.tvRoutineEditorTaskTitle)
        private val metadata: TextView = parent.findViewById(R.id.tvRoutineEditorTaskMetadata)
        private val edit: ImageButton = parent.findViewById(R.id.btnEditRoutineTask)
        private val delete: ImageButton = parent.findViewById(R.id.btnDeleteRoutineTask)

        fun bind(task: RoutineTaskDraft) {
            val context = itemView.context
            title.text = task.title
            val parts = buildList {
                task.optionalTime?.let { add(RoutineUiFormatter.time(it)) }
                task.estimatedDurationMinutes?.let {
                    add(context.getString(R.string.routine_task_duration, it))
                }
            }
            metadata.text = parts.joinToString(" · ")
            metadata.visibility = if (parts.isEmpty()) View.GONE else View.VISIBLE
            drag.contentDescription = context.getString(R.string.routine_task_drag_description, task.title)
            edit.contentDescription = context.getString(R.string.routine_task_edit_description, task.title)
            delete.contentDescription = context.getString(R.string.routine_task_delete_description, task.title)
            edit.setOnClickListener { onEdit(task) }
            delete.setOnClickListener { onDelete(task) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<RoutineTaskDraft>() {
        override fun areItemsTheSame(oldItem: RoutineTaskDraft, newItem: RoutineTaskDraft) =
            oldItem.localId == newItem.localId

        override fun areContentsTheSame(oldItem: RoutineTaskDraft, newItem: RoutineTaskDraft) =
            oldItem == newItem
    }
}
