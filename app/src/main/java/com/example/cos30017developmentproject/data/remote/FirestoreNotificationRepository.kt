package com.example.cos30017developmentproject.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class FirestoreNotification(
	val id: String,
	val recipientUid: String,
	val senderUid: String,
	val senderName: String?,
	val type: String,
	val relatedRequestId: String,
	val itemId: String,
	val itemType: String,
	val itemTitle: String,
	val itemLocation: String,
	val relatedItemId: String?,
	val relatedItemType: String?,
	val relatedItemTitle: String?,
	val relatedItemLocation: String?,
	val title: String,
	val message: String,
	val createdAt: Long,
	val isRead: Boolean
) {
	companion object {
		const val TYPE_CLAIM_REQUEST = "CLAIM_REQUEST"
		const val TYPE_FOUND_REQUEST = "FOUND_REQUEST"
		const val TYPE_REQUEST_ACCEPTED = "REQUEST_ACCEPTED"
	}
}

class FirestoreNotificationRepository(
	private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
	private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) {
	private val notifications: CollectionReference = firestore.collection(NOTIFICATIONS_COLLECTION)

	fun observeNotifications(): Flow<List<FirestoreNotification>> {
		val uid = firebaseAuth.currentUser?.uid
			?: return callbackFlow { close(IllegalStateException("A Firebase user must be signed in")) }
		return observeForRecipient(uid)
	}

	fun observeUnreadCount(): Flow<Int> =
		observeNotifications().map { notifications -> notifications.count { !it.isRead } }

	suspend fun markAsRead(notificationId: String) {
		val uid = firebaseAuth.currentUser?.uid
			?: throw IllegalStateException("A Firebase user must be signed in")
		val document = notifications.document(notificationId).get().await()
		if (!document.exists() || document.getString(FIELD_RECIPIENT_UID) != uid) return
		if (document.getBoolean(FIELD_IS_READ) == false) {
			document.reference.update(FIELD_IS_READ, true).await()
		}
	}

	suspend fun markAllAsRead() {
		val uid = firebaseAuth.currentUser?.uid
			?: throw IllegalStateException("A Firebase user must be signed in")
		val unread = notifications.whereEqualTo(FIELD_RECIPIENT_UID, uid).get().await()
			.documents.filter { it.getBoolean(FIELD_IS_READ) == false }
		unread.chunked(MAX_BATCH_SIZE).forEach { chunk ->
			val batch = firestore.batch()
			chunk.forEach { batch.update(it.reference, FIELD_IS_READ, true) }
			batch.commit().await()
		}
	}

	private fun observeForRecipient(uid: String): Flow<List<FirestoreNotification>> = callbackFlow {
		val registration: ListenerRegistration = notifications
			.whereEqualTo(FIELD_RECIPIENT_UID, uid)
			.addSnapshotListener { snapshot, error ->
				if (error != null) {
					close(error)
				} else if (snapshot == null) {
					close(IllegalStateException("Firestore returned an empty notification snapshot"))
				} else {
					try {
						trySend(
							snapshot.documents.mapNotNull { it.toFirestoreNotification() }
								.sortedByDescending(FirestoreNotification::createdAt)
						)
					} catch (exception: Exception) {
						close(exception)
					}
				}
			}
		awaitClose { registration.remove() }
	}

	private fun DocumentSnapshot.toFirestoreNotification(): FirestoreNotification? {
		if (!exists()) return null
		val createdAt = getTimestamp(FIELD_CREATED_AT)
			?: throw IllegalStateException("Firestore notification is missing its createdAt timestamp")
		return FirestoreNotification(
			id = id,
			recipientUid = getString(FIELD_RECIPIENT_UID)
				?: throw IllegalStateException("Firestore notification is missing its recipientUid"),
			senderUid = getString(FIELD_SENDER_UID).orEmpty(),
			senderName = getString(FIELD_SENDER_NAME),
			type = getString(FIELD_TYPE)
				?: if (getString(FIELD_LEGACY_CLAIM_ID) != null) {
					FirestoreNotification.TYPE_CLAIM_REQUEST
				} else {
					throw IllegalStateException("Firestore notification is missing its type")
				},
			relatedRequestId = getString(FIELD_RELATED_REQUEST_ID)
				?: getString(FIELD_LEGACY_CLAIM_ID).orEmpty(),
			itemId = getString(FIELD_ITEM_ID)
				?: throw IllegalStateException("Firestore notification is missing its itemId"),
			itemType = getString(FIELD_ITEM_TYPE)
				?: throw IllegalStateException("Firestore notification is missing its itemType"),
			itemTitle = getString(FIELD_ITEM_TITLE).orEmpty(),
			itemLocation = getString(FIELD_ITEM_LOCATION).orEmpty(),
			relatedItemId = getString(FIELD_RELATED_ITEM_ID),
			relatedItemType = getString(FIELD_RELATED_ITEM_TYPE),
			relatedItemTitle = getString(FIELD_RELATED_ITEM_TITLE),
			relatedItemLocation = getString(FIELD_RELATED_ITEM_LOCATION),
			title = getString(FIELD_TITLE)
				?: throw IllegalStateException("Firestore notification is missing its title"),
			message = getString(FIELD_MESSAGE)
				?: throw IllegalStateException("Firestore notification is missing its message"),
			createdAt = createdAt.toDate().time,
			isRead = getBoolean(FIELD_IS_READ)
				?: throw IllegalStateException("Firestore notification is missing its isRead state")
		)
	}

	private companion object {
		const val NOTIFICATIONS_COLLECTION = "notifications"
		const val FIELD_RECIPIENT_UID = "recipientUid"
		const val FIELD_SENDER_UID = "senderUid"
		const val FIELD_SENDER_NAME = "senderName"
		const val FIELD_TYPE = "type"
		const val FIELD_RELATED_REQUEST_ID = "relatedRequestId"
		const val FIELD_ITEM_ID = "itemId"
		const val FIELD_ITEM_TYPE = "itemType"
		const val FIELD_ITEM_TITLE = "itemTitle"
		const val FIELD_ITEM_LOCATION = "itemLocation"
		const val FIELD_RELATED_ITEM_ID = "relatedItemId"
		const val FIELD_RELATED_ITEM_TYPE = "relatedItemType"
		const val FIELD_RELATED_ITEM_TITLE = "relatedItemTitle"
		const val FIELD_RELATED_ITEM_LOCATION = "relatedItemLocation"
		const val FIELD_TITLE = "title"
		const val FIELD_MESSAGE = "message"
		const val FIELD_CREATED_AT = "createdAt"
		const val FIELD_IS_READ = "isRead"
		const val FIELD_LEGACY_CLAIM_ID = "claimId"
		const val MAX_BATCH_SIZE = 500
	}
}
