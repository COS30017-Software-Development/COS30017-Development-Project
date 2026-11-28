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
import com.cloudinary.android.MediaManager
import com.example.cos30017developmentproject.data.local.AppDatabase
import com.example.cos30017developmentproject.data.remote.FirestoreNotification
import com.example.cos30017developmentproject.data.remote.FirestoreNotificationRepository
import com.example.cos30017developmentproject.data.remote.FcmTokenRepository
import com.example.cos30017developmentproject.ui.notifications.NotificationAlertTracker
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
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
			FirebaseMessaging.getInstance().token
				.addOnSuccessListener { token ->
					if (uid != null) FcmTokenRepository.register(uid, token)
				}
				.addOnFailureListener { error -> Log.e(TAG, "Unable to retrieve FCM token", error) }
		}
	}

	override fun onCreate() {
		super.onCreate()
		try {
			MediaManager.get()
		} catch (_: IllegalStateException) {
			MediaManager.init(this, mapOf("cloud_name" to "x1rpt5a5"))
		}
		AppDatabase.getInstance(this).openHelper.writableDatabase
		notificationRepository = FirestoreNotificationRepository()
		registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
			override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
			override fun onActivityStarted(activity: Activity) = Unit
			override fun onActivityResumed(activity: Activity) {
				currentActivity = activity
				isInForeground = true
			}
			override fun onActivityPaused(activity: Activity) {
				if (currentActivity === activity) {
					currentActivity = null
					isInForeground = false
				}
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
		showNotification(notification.id, notification.type, notification.title, notification.message)
	}

	fun showForegroundPush(notificationId: String, type: String, title: String, message: String) {
		if (alertTracker.markNotified(notificationId)) {
			showNotification(notificationId, type, title, message)
		}
	}

	private fun showNotification(notificationId: String, type: String, title: String, body: String) {
		val activity = currentActivity?.takeUnless { it.isFinishing || it.isDestroyed } ?: return
		val displayText = SpannableStringBuilder()
			.append(title)
			.append('\n')
			.append(body)
			.apply {
				setSpan(StyleSpan(Typeface.BOLD), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
			}
		Snackbar.make(
			activity.findViewById(android.R.id.content),
			displayText,
			Snackbar.LENGTH_LONG
		).setAction(R.string.notifications_open) {
			applicationScope.launch {
				try {
					notificationRepository.markAsRead(notificationId)
				} catch (error: Exception) {
					Log.e(TAG, "Unable to mark opened notification as read", error)
				}
			}
			activity.startActivity(Intent(activity, MyRequestsActivity::class.java).apply {
				putExtra(
					MyRequestsActivity.EXTRA_DIRECTION,
					if (type == FirestoreNotification.TYPE_CLAIM_REQUEST ||
						type == FirestoreNotification.TYPE_FOUND_REQUEST
					) MyRequestsActivity.DIRECTION_RECEIVED else MyRequestsActivity.DIRECTION_SENT
				)
				putExtra(MyRequestsActivity.EXTRA_NOTIFICATION_ID, notificationId)
			})
		}.show()
	}

	companion object {
		const val TAG = "LostAndFoundApplication"

		@Volatile
		var isInForeground: Boolean = false
			private set
	}
}
