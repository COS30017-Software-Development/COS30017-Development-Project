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
import com.example.cos30017developmentproject.data.remote.FirestoreItemRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClaimCardAdapter(
	private val onClaimClick: (ClaimCard) -> Unit
) : ListAdapter<ClaimCard, ClaimCardAdapter.ViewHolder>(DiffCallback) {

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		val view = LayoutInflater.from(parent.context).inflate(R.layout.item_claim_card, parent, false)
		return ViewHolder(view)
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		holder.bind(getItem(position), onClaimClick)
	}

	class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		private val title = view.findViewById<TextView>(R.id.claim_card_title)
		private val status = view.findViewById<TextView>(R.id.claim_card_status)
		private val itemType = view.findViewById<TextView>(R.id.claim_card_item_type)
		private val description = view.findViewById<TextView>(R.id.claim_card_description)
		private val submitted = view.findViewById<TextView>(R.id.claim_card_submitted)

		fun bind(card: ClaimCard, onClaimClick: (ClaimCard) -> Unit) {
			title.text = card.title
			description.text = card.description
			val itemTypeName = itemView.context.getString(
				when (card.itemType) {
					FirestoreItemRepository.TYPE_LOST -> R.string.lost
					else -> R.string.found
				}
			)
			itemType.text = itemView.context.getString(R.string.claim_item_type_format, itemTypeName)
			status.setText(
				when (card.status) {
					ClaimStatus.APPROVED -> R.string.claim_status_approved
					ClaimStatus.REJECTED -> R.string.claim_status_rejected
					else -> R.string.claim_status_pending
				}
			)
			val date = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(card.submittedAt))
			submitted.text = itemView.context.getString(R.string.claim_submitted_label) + ": " + date
			itemView.setOnClickListener { onClaimClick(card) }
		}
	}

	private object DiffCallback : DiffUtil.ItemCallback<ClaimCard>() {
		override fun areItemsTheSame(oldItem: ClaimCard, newItem: ClaimCard) =
			oldItem.foundItemId == newItem.foundItemId

		override fun areContentsTheSame(oldItem: ClaimCard, newItem: ClaimCard) = oldItem == newItem
	}
}
