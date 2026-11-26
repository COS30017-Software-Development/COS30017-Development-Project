package com.example.cos30017developmentproject.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.cos30017developmentproject.MyRequestsActivity
import com.example.cos30017developmentproject.R
import com.example.cos30017developmentproject.data.remote.FcmTokenRepository
import com.example.cos30017developmentproject.data.remote.FirestoreNotification
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class RequestMessagingService : FirebaseMessagingService() {
	override fun onNewToken(token: String) {
		FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
			FcmTokenRepository.register(uid, token)
		}
	}

	override fun onMessageReceived(message: RemoteMessage) {
		val notificationId = message.data[FIELD_NOTIFICATION_ID] ?: return
		if (com.example.cos30017developmentproject.LostAndFoundApplication.isInForeground) return
		if (!recordNotificationOnce(notificationId)) return

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
			ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
			PackageManager.PERMISSION_GRANTED
		) {
			Log.i(TAG, "Notification permission is not granted; keeping the in-app notification available")
			return
		}
		ensureNotificationChannel()
		val direction = if (message.data[FIELD_TYPE] == FirestoreNotification.TYPE_CLAIM_REQUEST ||
			message.data[FIELD_TYPE] == FirestoreNotification.TYPE_FOUND_REQUEST
		) MyRequestsActivity.DIRECTION_RECEIVED else MyRequestsActivity.DIRECTION_SENT
		val intent = Intent(this, MyRequestsActivity::class.java).apply {
			putExtra(MyRequestsActivity.EXTRA_DIRECTION, direction)
			putExtra(MyRequestsActivity.EXTRA_NOTIFICATION_ID, notificationId)
			flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
		}
		val pendingIntent = PendingIntent.getActivity(
			this,
			notificationId.hashCode(),
			intent,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
		)
		val title = message.data[FIELD_TITLE].orEmpty().ifBlank { getString(R.string.notifications_title) }
		val body = message.data[FIELD_MESSAGE].orEmpty()
		val notification = NotificationCompat.Builder(this, CHANNEL_ID)
			.setSmallIcon(R.mipmap.ic_launcher)
			.setContentTitle(title)
			.setContentText(body)
			.setStyle(NotificationCompat.BigTextStyle().bigText(body))
			.setPriority(NotificationCompat.PRIORITY_HIGH)
			.setAutoCancel(true)
			.setContentIntent(pendingIntent)
			.build()
		NotificationManagerCompat.from(this).notify(notificationId.hashCode(), notification)
	}

	private fun recordNotificationOnce(notificationId: String): Boolean {
		val preferences = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
		val seen = preferences.getStringSet(KEY_SEEN_IDS, emptySet()).orEmpty().toMutableList()
		if (notificationId in seen) return false
		seen.add(notificationId)
		if (seen.size > MAX_SEEN_IDS) seen.removeAt(0)
		preferences.edit().putStringSet(KEY_SEEN_IDS, seen.toSet()).apply()
		return true
	}

	private fun ensureNotificationChannel() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
		val channel = NotificationChannel(
			CHANNEL_ID,
			getString(R.string.notification_channel_name),
			NotificationManager.IMPORTANCE_HIGH
		)
		getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
	}

	private companion object {
		const val TAG = "RequestMessagingService"
		const val CHANNEL_ID = "request_notifications"
		const val PREFERENCES_NAME = "push_notification_state"
		const val KEY_SEEN_IDS = "seen_ids"
		const val MAX_SEEN_IDS = 100
		const val FIELD_NOTIFICATION_ID = "notificationId"
		const val FIELD_TYPE = "type"
		const val FIELD_TITLE = "title"
		const val FIELD_MESSAGE = "message"
	}
}
