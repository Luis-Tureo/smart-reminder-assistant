package com.luistureo.voicereminderapp.presentation.recovery.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.luistureo.voicereminderapp.R

data class RecoveryToolRow(val id: Int, val label: String)

class RecoveryToolAdapter(private val onDelete: (RecoveryToolRow) -> Unit) :
    RecyclerView.Adapter<RecoveryToolAdapter.ViewHolder>() {
    private var items: List<RecoveryToolRow> = emptyList()

    fun submitList(value: List<RecoveryToolRow>) {
        items = value
        notifyDataSetChanged()
    }

    class ViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_recovery_tool, parent, false)
    ) {
        val label: TextView = itemView.findViewById(R.id.tvRecoveryToolLabel)
        val delete: ImageButton = itemView.findViewById(R.id.btnDeleteRecoveryTool)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(parent)
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.label.text = item.label
        holder.delete.setOnClickListener { onDelete(item) }
    }
}
