package com.luistureo.voicereminderapp.presentation.ui.adapter

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.domain.model.Reminder

class ReminderAdapter(
    private var reminders: List<Reminder>,
    private val onDelete: (Reminder) -> Unit,
    private val onUpdate: (Reminder) -> Unit
) : RecyclerView.Adapter<ReminderAdapter.ReminderViewHolder>() {

    class ReminderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.tvReminderText)
        val date: TextView = itemView.findViewById(R.id.tvReminderDate)
        val time: TextView = itemView.findViewById(R.id.tvReminderTime)
        val check: CheckBox = itemView.findViewById(R.id.checkCompleted)
        val delete: ImageButton = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReminderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reminder, parent, false)
        return ReminderViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReminderViewHolder, position: Int) {
        val reminder = reminders[position]

        holder.text.text = reminder.text
        holder.date.text = reminder.date
        holder.time.text = reminder.time

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

    private fun applyCompletedStyle(holder: ReminderViewHolder, isCompleted: Boolean) {
        if (isCompleted) {
            holder.text.paintFlags = holder.text.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            holder.date.paintFlags = holder.date.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            holder.time.paintFlags = holder.time.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG

            holder.text.alpha = 0.5f
            holder.date.alpha = 0.5f
            holder.time.alpha = 0.5f
        } else {
            holder.text.paintFlags =
                holder.text.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.date.paintFlags =
                holder.date.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            holder.time.paintFlags =
                holder.time.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()

            holder.text.alpha = 1.0f
            holder.date.alpha = 1.0f
            holder.time.alpha = 1.0f
        }
    }
}