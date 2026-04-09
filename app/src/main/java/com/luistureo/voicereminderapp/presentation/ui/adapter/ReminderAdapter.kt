package com.luistureo.voicereminderapp.presentation.ui.adapter

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.domain.model.Reminder

class ReminderAdapter(
    private var reminders: List<Reminder>,
    private val onDelete: (Reminder) -> Unit,
    private val onUpdate: (Reminder) -> Unit
) : RecyclerView.Adapter<ReminderAdapter.ReminderViewHolder>() {

    class ReminderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.tvReminderText)
        val detail: TextView = itemView.findViewById(R.id.tvReminderDetail)
        val date: TextView = itemView.findViewById(R.id.tvReminderDate)
        val time: TextView = itemView.findViewById(R.id.tvReminderTime)
        val separator: TextView = itemView.findViewById(R.id.tvReminderSeparator)
        val check: CheckBox = itemView.findViewById(R.id.checkCompleted)
        val completedLabel: TextView = itemView.findViewById(R.id.tvCompletedLabel)
        val delete: ImageButton = itemView.findViewById(R.id.btnDelete)
        val iconContainer: FrameLayout = itemView.findViewById(R.id.iconContainer)
        val icon: ImageView = itemView.findViewById(R.id.ivReminderIcon)
        val calendarIcon: ImageView = itemView.findViewById(R.id.ivCalendar)
        val clockIcon: ImageView = itemView.findViewById(R.id.ivClock)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reminder, parent, false)
        return ReminderViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        val reminder = reminders[position]

        holder.title.text = reminder.title
        holder.detail.text = reminder.detail
        holder.date.text = reminder.date
        holder.time.text = reminder.time
        bindCategoryIcon(holder, reminder)

        holder.check.setOnCheckedChangeListener(null)
        holder.check.isChecked = reminder.isCompleted

        applyCompletedStyle(holder, reminder.isCompleted)

        holder.check.setOnCheckedChangeListener { _, isChecked ->
            applyCompletedStyle(holder, isChecked)
            val updatedReminder = reminder.copy(isCompleted = isChecked)
            onUpdate(updatedReminder)
        }

        holder.delete.setOnClickListener {
            onDelete(reminder)
        }
    }

    override fun getItemCount(): Int = reminders.size

    fun updateData(newReminders: List<Reminder>) {
        reminders = newReminders
        notifyDataSetChanged()
    }

    private fun bindCategoryIcon(holder: ReminderViewHolder, reminder: Reminder) {
        val normalizedContent = "${reminder.title} ${reminder.detail}".lowercase()
        val usesFitnessIcon = listOf(
            "gimnasio",
            "gym",
            "entren",
            "correr",
            "pesas",
            "ejercicio",
            "workout"
        ).any { keyword -> keyword in normalizedContent }

        if (usesFitnessIcon) {
            holder.iconContainer.setBackgroundResource(R.drawable.bg_reminder_icon_blue)
            holder.icon.setImageResource(R.drawable.ic_reminder_dumbbell)
            holder.icon.setColorFilter(
                ContextCompat.getColor(holder.itemView.context, R.color.reminder_icon_blue_fg)
            )
        } else {
            holder.iconContainer.setBackgroundResource(R.drawable.bg_reminder_icon_amber)
            holder.icon.setImageResource(R.drawable.ic_reminder_note)
            holder.icon.setColorFilter(
                ContextCompat.getColor(holder.itemView.context, R.color.reminder_icon_amber_fg)
            )
        }
    }

    private fun applyCompletedStyle(holder: ReminderViewHolder, isCompleted: Boolean) {
        val contentAlpha = if (isCompleted) 0.5f else 1.0f
        val detailAlpha = if (isCompleted) 0.42f else 0.92f
        val metaAlpha = if (isCompleted) 0.45f else 0.9f
        val actionAlpha = if (isCompleted) 0.75f else 1.0f

        if (isCompleted) {
            holder.title.paintFlags = holder.title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            holder.detail.paintFlags = holder.detail.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            holder.date.paintFlags = holder.date.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            holder.time.paintFlags = holder.time.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            holder.title.paintFlags =
                holder.title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.detail.paintFlags =
                holder.detail.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.date.paintFlags =
                holder.date.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.time.paintFlags =
                holder.time.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }

        animateAlpha(holder.title, contentAlpha)
        animateAlpha(holder.detail, detailAlpha)
        animateAlpha(holder.date, metaAlpha)
        animateAlpha(holder.time, metaAlpha)
        animateAlpha(holder.separator, metaAlpha)
        animateAlpha(holder.calendarIcon, metaAlpha)
        animateAlpha(holder.clockIcon, metaAlpha)
        animateAlpha(holder.iconContainer, actionAlpha)
        animateAlpha(holder.delete, actionAlpha)
        animateAlpha(holder.check, actionAlpha)
        animateAlpha(holder.completedLabel, actionAlpha)
    }

    private fun animateAlpha(view: View, targetAlpha: Float) {
        view.animate()
            .alpha(targetAlpha)
            .setDuration(180L)
            .start()
    }
}
