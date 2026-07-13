package com.luistureo.voicereminderapp.presentation.recovery.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.domain.recovery.model.RecoverySupportContact

class RecoveryContactAdapter(
    private val onView: (RecoverySupportContact) -> Unit,
    private val onCall: (RecoverySupportContact) -> Unit,
    private val onSms: (RecoverySupportContact) -> Unit,
    private val onDelete: (RecoverySupportContact) -> Unit
) : ListAdapter<RecoverySupportContact, RecoveryContactAdapter.ViewHolder>(Diff) {
    class ViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_recovery_contact, parent, false)
    ) {
        val name: TextView = itemView.findViewById(R.id.tvRecoveryContactName)
        val description: TextView = itemView.findViewById(R.id.tvRecoveryContactDescription)
        val view: MaterialButton = itemView.findViewById(R.id.btnRecoveryContactView)
        val call: MaterialButton = itemView.findViewById(R.id.btnRecoveryContactCall)
        val sms: MaterialButton = itemView.findViewById(R.id.btnRecoveryContactSms)
        val delete: ImageButton = itemView.findViewById(R.id.btnRecoveryContactDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(parent)
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.name.text = item.name
        holder.description.text = item.description.orEmpty()
        holder.view.setOnClickListener { onView(item) }
        holder.call.setOnClickListener { onCall(item) }
        holder.sms.setOnClickListener { onSms(item) }
        holder.delete.setOnClickListener { onDelete(item) }
    }

    private object Diff : DiffUtil.ItemCallback<RecoverySupportContact>() {
        override fun areItemsTheSame(oldItem: RecoverySupportContact, newItem: RecoverySupportContact) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: RecoverySupportContact, newItem: RecoverySupportContact) = oldItem == newItem
    }
}
