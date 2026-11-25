package com.example.cos30017developmentproject.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.Timestamp
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class FirestoreItem(
	val itemId: String,
	val itemName: String,
	val description: String,
	val category: String,
	val location: String,
	val date: Long,
	val type: String,
	val imageUrl: String?,
	val imageName: String,
	val status: String,
	val postedBy: String
)

class FirestoreItemRepository(
	firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
	private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) {
	private val items: CollectionReference = firestore.collection(ITEMS_COLLECTION)

	val currentUserUid: String?
		get() = firebaseAuth.currentUser?.uid

	fun observeItems(postedBy: String? = null): Flow<List<FirestoreItem>> = callbackFlow {
		val query = postedBy?.let { items.whereEqualTo(FIELD_POSTED_BY, it) } ?: items
		val registration: ListenerRegistration = query.addSnapshotListener { snapshot, error ->
			if (error != null) {
				close(error)
			} else if (snapshot == null) {
				close(IllegalStateException("Firestore returned an empty item snapshot"))
			} else {
				try {
					val records = snapshot.documents.mapNotNull { it.toFirestoreItem() }
						.sortedByDescending(FirestoreItem::date)
					trySend(records)
				} catch (exception: Exception) {
					close(exception)
				}
			}
		}
		awaitClose { registration.remove() }
	}

	suspend fun getItem(itemId: String): FirestoreItem? {
		val document = items.document(itemId).get().await()
		return if (document.exists()) document.toFirestoreItem()
			?: throw IllegalStateException("Firestore item is missing required fields")
		else null
	}

	suspend fun createItem(
		itemName: String,
		description: String,
		category: String,
		location: String,
		type: String,
		imageName: String
	): String {
		require(type == TYPE_LOST || type == TYPE_FOUND) { "Unsupported item type" }
		val uid = currentUserUid ?: throw IllegalStateException("A Firebase user must be signed in to post an item")
		val document = items.document()
		document.set(
			mapOf(
				FIELD_ITEM_ID to document.id,
				FIELD_ITEM_NAME to itemName,
				FIELD_DESCRIPTION to description,
				FIELD_CATEGORY to category,
				FIELD_LOCATION to location,
				FIELD_DATE to Timestamp.now(),
				FIELD_TYPE to type,
				FIELD_IMAGE_URL to null,
				FIELD_IMAGE_NAME to imageName,
				FIELD_STATUS to STATUS_OPEN,
				FIELD_PENDING_CLAIM_COUNT to 0,
				FIELD_POSTED_BY to uid
			)
		).await()
		return document.id
	}

	suspend fun updateItem(
		itemId: String,
		itemName: String,
		description: String,
		category: String,
		location: String,
		imageName: String
	) {
		items.document(itemId).update(
			mapOf(
				FIELD_ITEM_NAME to itemName,
				FIELD_DESCRIPTION to description,
				FIELD_CATEGORY to category,
				FIELD_LOCATION to location,
				FIELD_IMAGE_NAME to imageName
			)
		).await()
	}

	suspend fun deleteItem(itemId: String) {
		items.document(itemId).delete().await()
	}

	private fun DocumentSnapshot.toFirestoreItem(): FirestoreItem? {
		if (!exists()) return null
		val itemId = getString(FIELD_ITEM_ID)
			?: throw IllegalStateException("Firestore item is missing its itemId")
		val date = getTimestamp(FIELD_DATE)
			?: throw IllegalStateException("Firestore item is missing its date")
		return FirestoreItem(
			itemId = itemId,
			itemName = getString(FIELD_ITEM_NAME)
				?: throw IllegalStateException("Firestore item is missing its itemName"),
			description = getString(FIELD_DESCRIPTION)
				?: throw IllegalStateException("Firestore item is missing its description"),
			category = getString(FIELD_CATEGORY)
				?: throw IllegalStateException("Firestore item is missing its category"),
			location = getString(FIELD_LOCATION)
				?: throw IllegalStateException("Firestore item is missing its location"),
			date = date.toDate().time,
			type = getString(FIELD_TYPE)
				?: throw IllegalStateException("Firestore item is missing its type"),
			imageUrl = getString(FIELD_IMAGE_URL),
			imageName = getString(FIELD_IMAGE_NAME) ?: DEFAULT_IMAGE_NAME,
			status = getString(FIELD_STATUS)
				?: throw IllegalStateException("Firestore item is missing its status"),
			postedBy = getString(FIELD_POSTED_BY)
				?: throw IllegalStateException("Firestore item is missing its postedBy UID")
		)
	}

	companion object {
		const val TYPE_LOST = "LOST"
		const val TYPE_FOUND = "FOUND"
		const val STATUS_OPEN = "OPEN"
		private const val ITEMS_COLLECTION = "items"
		private const val FIELD_ITEM_ID = "itemId"
		private const val FIELD_ITEM_NAME = "itemName"
		private const val FIELD_DESCRIPTION = "description"
		private const val FIELD_CATEGORY = "category"
		private const val FIELD_LOCATION = "location"
		private const val FIELD_DATE = "date"
		private const val FIELD_TYPE = "type"
		private const val FIELD_IMAGE_URL = "imageUrl"
		private const val FIELD_IMAGE_NAME = "imageName"
		private const val FIELD_STATUS = "status"
		private const val FIELD_PENDING_CLAIM_COUNT = "pendingClaimCount"
		private const val FIELD_POSTED_BY = "postedBy"
		private const val DEFAULT_IMAGE_NAME = "sample_backpack"
	}
}
