package com.example.cos30017developmentproject.ui.notifications

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.cos30017developmentproject.R
import com.example.cos30017developmentproject.data.remote.FirestoreNotification
import java.text.DateFormat
import java.util.Date

class NotificationAdapter(
	private val onClick: (FirestoreNotification) -> Unit
) : ListAdapter<FirestoreNotification, NotificationAdapter.NotificationViewHolder>(DIFF) {
	override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder =
		NotificationViewHolder(
			LayoutInflater.from(parent.context).inflate(R.layout.card_notification, parent, false)
		)

	override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
		holder.bind(getItem(position), onClick)
	}

	class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
		private val title = itemView.findViewById<TextView>(R.id.notification_title)
		private val message = itemView.findViewById<TextView>(R.id.notification_message)
		private val sender = itemView.findViewById<TextView>(R.id.notification_sender)
		private val relatedItem = itemView.findViewById<TextView>(R.id.notification_related_item)
		private val date = itemView.findViewById<TextView>(R.id.notification_date)
		private val unreadDot = itemView.findViewById<View>(R.id.notification_unread_dot)

		fun bind(notification: FirestoreNotification, onClick: (FirestoreNotification) -> Unit) {
			title.text = notification.title
			message.text = notification.message
			val senderName = notification.senderName?.takeIf(String::isNotBlank)
			sender.isVisible = senderName != null
			sender.text = senderName?.let { itemView.context.getString(R.string.notification_from, it) }
			val relatedType = notification.relatedItemType ?: notification.itemType
			val relatedTitle = notification.relatedItemTitle ?: notification.itemTitle
			val relatedLocation = notification.relatedItemLocation ?: notification.itemLocation
			relatedItem.text = itemView.context.getString(
				R.string.notification_related_item,
				itemView.context.getString(
					if (relatedType == "FOUND") R.string.found else R.string.lost
				),
				relatedTitle,
				relatedLocation
			)
			date.text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
				.format(Date(notification.createdAt))
			unreadDot.isVisible = !notification.isRead
			itemView.alpha = if (notification.isRead) 0.75f else 1f
			itemView.setOnClickListener { onClick(notification) }
		}
	}

	private companion object {
		val DIFF = object : DiffUtil.ItemCallback<FirestoreNotification>() {
			override fun areItemsTheSame(oldItem: FirestoreNotification, newItem: FirestoreNotification): Boolean =
				oldItem.id == newItem.id

			override fun areContentsTheSame(oldItem: FirestoreNotification, newItem: FirestoreNotification): Boolean =
				oldItem == newItem
		}
	}
}
