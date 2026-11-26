package com.example.cos30017developmentproject.ui.notifications

import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cos30017developmentproject.R
import com.example.cos30017developmentproject.data.remote.FirestoreNotification
import com.example.cos30017developmentproject.data.remote.FirestoreNotificationRepository
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class NotificationsPage(
	private val view: View,
	private val repository: FirestoreNotificationRepository,
	private val lifecycleOwner: LifecycleOwner,
	private val onOpen: (FirestoreNotification) -> Unit
) {
	private val adapter = NotificationAdapter { notification ->
		if (!notification.isRead) markAsRead(notification.id)
		onOpen(notification)
	}
	private var notificationsJob: Job? = null

	init {
		view.findViewById<RecyclerView>(R.id.notifications_list).apply {
			layoutManager = LinearLayoutManager(view.context)
			adapter = this@NotificationsPage.adapter
		}
		view.findViewById<MaterialButton>(R.id.notifications_mark_all).setOnClickListener {
			markAllAsRead()
		}
	}

	fun startObserving() {
		if (notificationsJob?.isActive == true) return
		notificationsJob = lifecycleOwner.lifecycleScope.launch {
			repository.observeNotifications()
				.catch { error ->
					Log.e(TAG, "Unable to load current user's notifications", error)
					Toast.makeText(view.context, R.string.notifications_load_failed, Toast.LENGTH_SHORT).show()
					emit(emptyList())
				}
				.collect { notifications ->
					adapter.submitList(notifications)
					view.findViewById<TextView>(R.id.notifications_empty).isVisible = notifications.isEmpty()
					view.findViewById<View>(R.id.notifications_mark_all).isVisible =
						notifications.any { !it.isRead }
				}
		}
	}

	fun stopObserving() {
		notificationsJob?.cancel()
		notificationsJob = null
	}

	private fun markAsRead(notificationId: String) {
		lifecycleOwner.lifecycleScope.launch {
			try {
				repository.markAsRead(notificationId)
			} catch (error: Exception) {
				Log.e(TAG, "Unable to mark notification as read", error)
				Toast.makeText(view.context, R.string.notifications_update_failed, Toast.LENGTH_SHORT).show()
			}
		}
	}

	private fun markAllAsRead() {
		lifecycleOwner.lifecycleScope.launch {
			try {
				repository.markAllAsRead()
			} catch (error: Exception) {
				Log.e(TAG, "Unable to mark notifications as read", error)
				Toast.makeText(view.context, R.string.notifications_update_failed, Toast.LENGTH_SHORT).show()
			}
		}
	}

	private companion object {
		const val TAG = "NotificationsPage"
	}
}
