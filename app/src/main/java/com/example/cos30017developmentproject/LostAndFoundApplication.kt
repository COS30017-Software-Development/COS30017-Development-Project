package com.example.cos30017developmentproject

import android.app.Application
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import com.example.cos30017developmentproject.data.local.AppDatabase
import com.example.cos30017developmentproject.data.remote.FirestoreNotification
import com.example.cos30017developmentproject.data.remote.FirestoreNotificationRepository
import com.example.cos30017developmentproject.data.remote.FirestoreItemRepository
import com.example.cos30017developmentproject.ItemDetailsActivity
import com.example.cos30017developmentproject.ui.notifications.NotificationAlertTracker
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class LostAndFoundApplication : Application() {
	private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
	private lateinit var notificationRepository: FirestoreNotificationRepository
	private var notificationsJob: Job? = null
	private var currentActivity: Activity? = null
	private var observedUid: String? = null
	private var alertTracker = NotificationAlertTracker()
	private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
		val uid = auth.currentUser?.uid
		if (uid != observedUid) {
			observedUid = uid
			notificationsJob?.cancel()
			alertTracker = NotificationAlertTracker()
			if (uid != null) observeIncomingNotifications()
		}
	}

	override fun onCreate() {
		super.onCreate()
		AppDatabase.getInstance(this).openHelper.writableDatabase
		notificationRepository = FirestoreNotificationRepository()
		registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
			override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
			override fun onActivityStarted(activity: Activity) = Unit
			override fun onActivityResumed(activity: Activity) {
				currentActivity = activity
			}
			override fun onActivityPaused(activity: Activity) {
				if (currentActivity === activity) currentActivity = null
			}
			override fun onActivityStopped(activity: Activity) = Unit
			override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
			override fun onActivityDestroyed(activity: Activity) {
				if (currentActivity === activity) currentActivity = null
			}
		})
		FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
	}

	private fun observeIncomingNotifications() {
		notificationsJob = applicationScope.launch {
			notificationRepository.observeNotifications()
				.catch { error -> Log.e(TAG, "Unable to observe incoming notifications", error) }
				.collect { notifications ->
					val newIds = alertTracker.newlyObserved(notifications.map(FirestoreNotification::id))
					newIds.forEach { id ->
						notifications.firstOrNull { it.id == id && !it.isRead }?.let(::showNotification)
					}
				}
		}
	}

	private fun showNotification(notification: FirestoreNotification) {
		val activity = currentActivity?.takeUnless { it.isFinishing || it.isDestroyed } ?: return
		val message = SpannableStringBuilder()
			.append(notification.title)
			.append('\n')
			.append(notification.message)
			.apply {
				setSpan(StyleSpan(Typeface.BOLD), 0, notification.title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
			}
		Snackbar.make(
			activity.findViewById(android.R.id.content),
			message,
			Snackbar.LENGTH_LONG
		).setAction(R.string.notifications_open) {
			applicationScope.launch {
				try {
					notificationRepository.markAsRead(notification.id)
				} catch (error: Exception) {
					Log.e(TAG, "Unable to mark opened notification as read", error)
				}
			}
			activity.startActivity(Intent(activity, ItemDetailsActivity::class.java).apply {
				putExtra(ItemDetailsActivity.EXTRA_ITEM_ID, notification.itemId)
				putExtra(
					ItemDetailsActivity.EXTRA_ITEM_TYPE,
					if (notification.itemType == FirestoreItemRepository.TYPE_FOUND) {
						ItemDetailsActivity.TYPE_FOUND
					} else {
						ItemDetailsActivity.TYPE_LOST
					}
				)
			})
		}.show()
	}

	private companion object {
		const val TAG = "LostAndFoundApplication"
	}
}
