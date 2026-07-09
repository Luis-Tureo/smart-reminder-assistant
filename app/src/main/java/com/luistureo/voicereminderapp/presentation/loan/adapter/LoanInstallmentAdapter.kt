package com.luistureo.voicereminderapp.presentation.loan.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.loan.ClpFormatter
import com.luistureo.voicereminderapp.core.utils.DateTimeFormatter
import com.luistureo.voicereminderapp.domain.loan.model.LoanInstallment

class LoanInstallmentAdapter : RecyclerView.Adapter<LoanInstallmentAdapter.InstallmentViewHolder>() {

    private var installments: List<LoanInstallment> = emptyList()

    class InstallmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.tvInstallmentTitle)
        val dueDate: TextView = itemView.findViewById(R.id.tvInstallmentDueDate)
        val amount: TextView = itemView.findViewById(R.id.tvInstallmentAmount)
        val paid: TextView = itemView.findViewById(R.id.tvInstallmentPaid)
        val status: TextView = itemView.findViewById(R.id.tvInstallmentStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InstallmentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_loan_installment, parent, false)
        return InstallmentViewHolder(view)
    }

    override fun onBindViewHolder(holder: InstallmentViewHolder, position: Int) {
        val installment = installments[position]
        holder.title.text = holder.itemView.context.getString(
            R.string.loan_installment_number,
            installment.installmentNumber
        )
        holder.dueDate.text = DateTimeFormatter.formatDateFromEpoch(installment.dueDateEpochMillis)
        holder.amount.text = ClpFormatter.format(installment.expectedAmountClp)
        holder.paid.text = holder.itemView.context.getString(
            R.string.loan_installment_paid,
            ClpFormatter.format(installment.paidAmountClp)
        )
        holder.status.text = installment.status.label
    }

    override fun getItemCount(): Int = installments.size

    fun submitList(nextInstallments: List<LoanInstallment>) {
        installments = nextInstallments
        notifyDataSetChanged()
    }
}
