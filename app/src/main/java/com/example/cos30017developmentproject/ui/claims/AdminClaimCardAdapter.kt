package com.example.cos30017developmentproject.ui.claims

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.cos30017developmentproject.R
import com.example.cos30017developmentproject.data.local.ClaimStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminClaimCardAdapter(
	private val onApprove: (AdminClaimCard) -> Unit,
	private val onReject: (AdminClaimCard) -> Unit
) : ListAdapter<AdminClaimCard, AdminClaimCardAdapter.ViewHolder>(DiffCallback) {

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		val view = LayoutInflater.from(parent.context).inflate(R.layout.item_admin_claim_card, parent, false)
		return ViewHolder(view)
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		holder.bind(getItem(position), onApprove, onReject)
	}

	class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		private val title = view.findViewById<TextView>(R.id.admin_claim_item_title)
		private val status = view.findViewById<TextView>(R.id.admin_claim_status)
		private val applicant = view.findViewById<TextView>(R.id.admin_claim_applicant)
		private val description = view.findViewById<TextView>(R.id.admin_claim_description)
		private val date = view.findViewById<TextView>(R.id.admin_claim_date)
		private val actions = view.findViewById<View>(R.id.admin_claim_actions)

		fun bind(card: AdminClaimCard, onApprove: (AdminClaimCard) -> Unit, onReject: (AdminClaimCard) -> Unit) {
			title.text = card.itemTitle
			status.setText(
				when (card.status) {
					ClaimStatus.APPROVED -> R.string.claim_status_approved
					ClaimStatus.REJECTED -> R.string.claim_status_rejected
					else -> R.string.claim_status_pending
				}
			)
			applicant.text = "${card.applicantName} • ${card.applicantStudentId}\n${card.applicantEmail}\n${card.itemLocation}"
			description.text = card.description
			val submitted = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(card.submittedAt))
			date.text = itemView.context.getString(R.string.admin_claim_submitted) + ": " + submitted
			actions.visibility = if (card.status == ClaimStatus.PENDING) View.VISIBLE else View.GONE
			val approve = itemView.findViewById<View>(R.id.admin_claim_approve)
			approve.visibility = if (card.canApprove) View.VISIBLE else View.GONE
			approve.setOnClickListener { onApprove(card) }
			itemView.findViewById<View>(R.id.admin_claim_reject).setOnClickListener { onReject(card) }
		}
	}

	private object DiffCallback : DiffUtil.ItemCallback<AdminClaimCard>() {
		override fun areItemsTheSame(oldItem: AdminClaimCard, newItem: AdminClaimCard) = oldItem.claimId == newItem.claimId
		override fun areContentsTheSame(oldItem: AdminClaimCard, newItem: AdminClaimCard) = oldItem == newItem
	}
}
