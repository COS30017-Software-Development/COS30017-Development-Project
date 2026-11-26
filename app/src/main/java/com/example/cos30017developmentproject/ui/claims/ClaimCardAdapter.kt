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
	private val onClaimClick: (ClaimCard) -> Unit,
	private val onAccept: (ClaimCard) -> Unit
) : ListAdapter<ClaimCard, ClaimCardAdapter.ViewHolder>(DiffCallback) {

	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
		val view = LayoutInflater.from(parent.context).inflate(R.layout.item_claim_card, parent, false)
		return ViewHolder(view)
	}

	override fun onBindViewHolder(holder: ViewHolder, position: Int) {
		holder.bind(getItem(position), onClaimClick, onAccept)
	}

	class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
		private val title = view.findViewById<TextView>(R.id.claim_card_title)
		private val status = view.findViewById<TextView>(R.id.claim_card_status)
		private val itemType = view.findViewById<TextView>(R.id.claim_card_item_type)
		private val description = view.findViewById<TextView>(R.id.claim_card_description)
		private val submitted = view.findViewById<TextView>(R.id.claim_card_submitted)
		private val accept = view.findViewById<View>(R.id.request_card_accept)

		fun bind(
			card: ClaimCard,
			onClaimClick: (ClaimCard) -> Unit,
			onAccept: (ClaimCard) -> Unit
		) {
			title.text = card.title
			description.text = card.description
			val itemTypeName = itemView.context.getString(
				when (card.itemType) {
					FirestoreItemRepository.TYPE_LOST -> R.string.lost
					else -> R.string.found
				}
			)
			itemType.text = itemView.context.getString(
				R.string.request_item_type_format,
				itemView.context.getString(
					if (card.itemType == FirestoreItemRepository.TYPE_FOUND) {
						R.string.claim_request
					} else {
						R.string.found_request
					}
				),
				itemTypeName
			)
			status.setText(
				when (card.status) {
					ClaimStatus.APPROVED -> R.string.claim_status_approved
					ClaimStatus.REJECTED -> R.string.claim_status_rejected
					else -> R.string.claim_status_pending
				}
			)
			viewLocation.text = card.itemLocation
			requestSender.text = card.senderName
			requestSender.visibility = if (card.isIncoming && card.senderName.isNotBlank()) View.VISIBLE else View.GONE
			accept.visibility =
				if (card.isIncoming && card.status == ClaimStatus.PENDING) View.VISIBLE else View.GONE
			accept.setOnClickListener { onAccept(card) }
			val date = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(card.submittedAt))
			submitted.text = itemView.context.getString(R.string.claim_submitted_label) + ": " + date
			itemView.setOnClickListener { onClaimClick(card) }
		}

		private val viewLocation = view.findViewById<TextView>(R.id.request_card_location)
		private val requestSender = view.findViewById<TextView>(R.id.request_card_sender)
	}

	private object DiffCallback : DiffUtil.ItemCallback<ClaimCard>() {
		override fun areItemsTheSame(oldItem: ClaimCard, newItem: ClaimCard) =
			oldItem.claimId == newItem.claimId

		override fun areContentsTheSame(oldItem: ClaimCard, newItem: ClaimCard) = oldItem == newItem
	}
}
