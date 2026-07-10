package com.luistureo.voicereminderapp.presentation.routine.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.content.res.ColorStateList
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.presentation.routine.RoutineUiFormatter
import com.luistureo.voicereminderapp.presentation.routine.state.RoutineDashboardItem

class RoutineDashboardAdapter(
    private val onClick: (RoutineDashboardItem) -> Unit
) : ListAdapter<RoutineDashboardItem, RoutineDashboardAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_routine_dashboard, parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    inner class ViewHolder(parent: android.view.View) : RecyclerView.ViewHolder(parent) {
        private val icon: ImageView = parent.findViewById(R.id.imageRoutinePeriod)
        private val name: TextView = parent.findViewById(R.id.tvRoutineDashboardName)
        private val status: TextView = parent.findViewById(R.id.tvRoutineDashboardStatus)
        private val progressText: TextView = parent.findViewById(R.id.tvRoutineDashboardProgress)
        private val percentage: TextView = parent.findViewById(R.id.tvRoutineDashboardPercentage)
        private val progress: LinearProgressIndicator = parent.findViewById(R.id.progressRoutineDashboard)

        fun bind(item: RoutineDashboardItem) {
            val context = itemView.context
            icon.setImageResource(RoutineUiFormatter.icon(item.routine))
            icon.imageTintList = ColorStateList.valueOf(item.routine.color)
            name.text = item.routine.name
            val activityStatus = if (item.routine.enabled) {
                item.routine.startTime?.let {
                    context.getString(R.string.routine_starts_at, RoutineUiFormatter.time(it))
                } ?: context.getString(R.string.routine_enabled)
            } else {
                context.getString(R.string.routine_disabled)
            }
            status.text = context.getString(
                R.string.routine_period_status,
                context.getString(RoutineUiFormatter.periodLabel(item.routine.period)),
                activityStatus
            )
            progressText.text = context.resources.getQuantityString(
                R.plurals.routine_task_progress,
                item.totalTasks,
                item.completedTasks,
                item.totalTasks
            )
            percentage.text = context.getString(R.string.routine_percentage, item.percentage)
            progress.setIndicatorColor(item.routine.color)
            progress.setProgressCompat(item.percentage, true)
            itemView.contentDescription = context.getString(
                R.string.routine_open_description,
                item.routine.name
            )
            itemView.setOnClickListener { onClick(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<RoutineDashboardItem>() {
        override fun areItemsTheSame(oldItem: RoutineDashboardItem, newItem: RoutineDashboardItem) =
            oldItem.routine.id == newItem.routine.id

        override fun areContentsTheSame(oldItem: RoutineDashboardItem, newItem: RoutineDashboardItem) =
            oldItem == newItem
    }
}
