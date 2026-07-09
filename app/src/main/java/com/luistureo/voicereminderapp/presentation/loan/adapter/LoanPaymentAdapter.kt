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
import com.luistureo.voicereminderapp.domain.loan.model.LoanPayment

class LoanPaymentAdapter : RecyclerView.Adapter<LoanPaymentAdapter.PaymentViewHolder>() {

    private var payments: List<LoanPayment> = emptyList()

    class PaymentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val amount: TextView = itemView.findViewById(R.id.tvPaymentAmount)
        val date: TextView = itemView.findViewById(R.id.tvPaymentDate)
        val note: TextView = itemView.findViewById(R.id.tvPaymentNote)
        val attachment: TextView = itemView.findViewById(R.id.tvPaymentAttachment)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_loan_payment, parent, false)
        return PaymentViewHolder(view)
    }

    override fun onBindViewHolder(holder: PaymentViewHolder, position: Int) {
        val payment = payments[position]
        holder.amount.text = ClpFormatter.format(payment.paidAmountClp)
        holder.date.text = DateTimeFormatter.formatDateFromEpoch(payment.paymentDateEpochMillis)
        holder.note.text = payment.note.orEmpty()
        holder.note.isVisible = !payment.note.isNullOrBlank()
        holder.attachment.isVisible = !payment.attachmentUri.isNullOrBlank()
    }

    override fun getItemCount(): Int = payments.size

    fun submitList(nextPayments: List<LoanPayment>) {
        payments = nextPayments
        notifyDataSetChanged()
    }
}
