package com.luistureo.voicereminderapp.presentation.loan.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.loan.ClpFormatter
import com.luistureo.voicereminderapp.core.utils.DateTimeFormatter
import com.luistureo.voicereminderapp.domain.loan.model.Loan

class LoanListAdapter(
    private val onClick: (Loan) -> Unit
) : RecyclerView.Adapter<LoanListAdapter.LoanViewHolder>() {

    private var loans: List<Loan> = emptyList()

    class LoanViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val person: TextView = itemView.findViewById(R.id.tvLoanPerson)
        val type: TextView = itemView.findViewById(R.id.tvLoanType)
        val amount: TextView = itemView.findViewById(R.id.tvLoanAmount)
        val remaining: TextView = itemView.findViewById(R.id.tvLoanRemaining)
        val dueDate: TextView = itemView.findViewById(R.id.tvLoanDueDate)
        val status: TextView = itemView.findViewById(R.id.tvLoanStatus)
        val attachment: TextView = itemView.findViewById(R.id.tvLoanAttachmentIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LoanViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_loan, parent, false)
        return LoanViewHolder(view)
    }

    override fun onBindViewHolder(holder: LoanViewHolder, position: Int) {
        val loan = loans[position]
        holder.person.text = loan.personName
        holder.type.text = loan.type.label
        holder.amount.text = ClpFormatter.format(loan.totalExpectedAmountClp)
        holder.remaining.text = holder.itemView.context.getString(
            R.string.loan_list_remaining,
            ClpFormatter.format(loan.remainingAmountClp)
        )
        holder.dueDate.text = holder.itemView.context.getString(
            R.string.loan_list_due_date,
            DateTimeFormatter.formatDateFromEpoch(loan.dueDateEpochMillis)
        )
        holder.status.text = loan.status.label
        holder.attachment.isVisible = loan.hasAttachment
        holder.itemView.setOnClickListener { onClick(loan) }
    }

    override fun getItemCount(): Int = loans.size

    fun submitList(nextLoans: List<Loan>) {
        loans = nextLoans
        notifyDataSetChanged()
    }
}
