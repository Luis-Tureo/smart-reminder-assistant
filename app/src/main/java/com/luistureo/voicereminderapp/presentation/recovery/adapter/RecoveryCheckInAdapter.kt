package com.luistureo.voicereminderapp.presentation.recovery.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryCheckIn
import com.luistureo.voicereminderapp.domain.recovery.model.RecoveryCheckInStatus
import java.time.format.DateTimeFormatter
import java.util.Locale

class RecoveryCheckInAdapter :
    ListAdapter<RecoveryCheckIn, RecoveryCheckInAdapter.ViewHolder>(Diff) {
    class ViewHolder(parent: ViewGroup) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_recovery_check_in, parent, false)
    ) {
        val date: TextView = itemView.findViewById(R.id.tvRecoveryCheckInDate)
        val status: TextView = itemView.findViewById(R.id.tvRecoveryCheckInStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.date.text = item.date.format(
            DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("es-CL"))
        )
        holder.status.setText(
            when (item.status) {
                RecoveryCheckInStatus.ACHIEVED -> R.string.recovery_checkin_success
                RecoveryCheckInStatus.DIFFICULTY_MANAGED -> R.string.recovery_checkin_managed
                RecoveryCheckInStatus.LAPSE -> R.string.recovery_lapse_support
                RecoveryCheckInStatus.PREFER_NOT_TO_REGISTER -> R.string.recovery_checkin_skip
            }
        )
    }

    private object Diff : DiffUtil.ItemCallback<RecoveryCheckIn>() {
        override fun areItemsTheSame(oldItem: RecoveryCheckIn, newItem: RecoveryCheckIn) =
            oldItem.goalHistoryKey == newItem.goalHistoryKey && oldItem.date == newItem.date
        override fun areContentsTheSame(oldItem: RecoveryCheckIn, newItem: RecoveryCheckIn) =
            oldItem == newItem
    }
}
