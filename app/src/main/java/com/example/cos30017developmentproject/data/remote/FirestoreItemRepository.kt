package com.example.cos30017developmentproject.data.remote

import android.net.Uri
import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.Timestamp
import kotlinx.coroutines.CancellationException
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
	private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
	private val firebaseStorage: FirebaseStorage = FirebaseStorage.getInstance()
) {
	private val items: CollectionReference = firestore.collection(ITEMS_COLLECTION)
	private val imageStorage = ItemImageStorage(firebaseStorage)

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
		imageName: String = DEFAULT_IMAGE_NAME,
		imageUri: Uri? = null,
		imageContentType: String = DEFAULT_IMAGE_CONTENT_TYPE
	): String {
		require(type == TYPE_LOST || type == TYPE_FOUND) { "Unsupported item type" }
		val uid = currentUserUid ?: throw IllegalStateException("A Firebase user must be signed in to post an item")
		val document = items.document()
		var uploadedImage: UploadedItemImage? = null
		try {
			document.set(mapOf(
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
			)).await()
			uploadedImage = imageUri?.let { imageStorage.upload(document.id, uid, it, imageContentType) }
			if (uploadedImage != null) document.update(FIELD_IMAGE_URL, uploadedImage.downloadUrl).await()
		} catch (exception: Exception) {
			uploadedImage?.reference?.let { reference ->
				try {
					reference.delete().await()
				} catch (cleanupException: Exception) {
					exception.addSuppressed(cleanupException)
				}
			}
			try {
				document.delete().await()
			} catch (cleanupException: Exception) {
				exception.addSuppressed(cleanupException)
			}
			throw exception
		}
		return document.id
	}

	suspend fun updateItem(
		itemId: String,
		itemName: String,
		description: String,
		category: String,
		location: String,
		imageName: String? = null,
		imageUri: Uri? = null,
		imageContentType: String = DEFAULT_IMAGE_CONTENT_TYPE,
		removeImage: Boolean = false
	): Exception? {
		require(!(imageUri != null && removeImage)) { "An image cannot be uploaded and removed at the same time" }
		val document = items.document(itemId)
		val previousItem = getItem(itemId) ?: throw IllegalStateException("Item does not exist")
		val uid = currentUserUid ?: throw IllegalStateException("A Firebase user must be signed in to edit an item")
		val uploadedImage = imageUri?.let { imageStorage.upload(itemId, uid, it, imageContentType) }
		val updates = mutableMapOf<String, Any?>(
			FIELD_ITEM_NAME to itemName,
			FIELD_DESCRIPTION to description,
			FIELD_CATEGORY to category,
			FIELD_LOCATION to location
		)
		imageName?.let { updates[FIELD_IMAGE_NAME] = it }
		if (uploadedImage != null || removeImage) updates[FIELD_IMAGE_URL] = uploadedImage?.downloadUrl
		try {
			document.update(updates).await()
		} catch (exception: Exception) {
			uploadedImage?.reference?.let { reference ->
				try {
					reference.delete().await()
				} catch (cleanupException: Exception) {
					exception.addSuppressed(cleanupException)
				}
			}
			throw exception
		}
		if (uploadedImage == null && !removeImage) return null
		val previousUrl = previousItem.imageUrl ?: return null
		if (previousUrl == uploadedImage?.downloadUrl) return null
		return try {
			imageStorage.delete(previousUrl, itemId)
			null
		} catch (exception: CancellationException) {
			throw exception
		} catch (exception: Exception) {
			Log.e(TAG, "Item image was updated but the previous Storage object could not be removed", exception)
			exception
		}
	}

	suspend fun deleteItem(itemId: String): Exception? {
		val imageUrl = getItem(itemId)?.imageUrl
		items.document(itemId).delete().await()
		if (imageUrl == null) return null
		return try {
			imageStorage.delete(imageUrl, itemId)
			null
		} catch (exception: CancellationException) {
			throw exception
		} catch (exception: Exception) {
			Log.e(TAG, "Item was deleted but its Storage image could not be removed", exception)
			exception
		}
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
		const val STATUS_PENDING_RETURN = "PENDING_RETURN"
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
		private const val DEFAULT_IMAGE_CONTENT_TYPE = "image/jpeg"
		private const val TAG = "FirestoreItemRepository"
	}
}

private data class UploadedItemImage(
	val reference: StorageReference,
	val downloadUrl: String
)

private class ItemImageStorage(private val storage: FirebaseStorage) {
	suspend fun upload(itemId: String, ownerUid: String, uri: Uri, contentType: String): UploadedItemImage {
		val reference = storage.reference.child("items/$itemId/${java.util.UUID.randomUUID()}")
		val metadata = com.google.firebase.storage.StorageMetadata.Builder()
			.setContentType(contentType)
			.setCustomMetadata("ownerUid", ownerUid)
			.setCustomMetadata("itemId", itemId)
			.build()
		try {
			reference.putFile(uri, metadata).await()
			return UploadedItemImage(reference, reference.downloadUrl.await().toString())
		} catch (exception: Exception) {
			try {
				reference.delete().await()
			} catch (cleanupException: Exception) {
				exception.addSuppressed(cleanupException)
			}
			throw exception
		}
	}

	suspend fun delete(downloadUrl: String, itemId: String) {
		val reference = try {
			storage.getReferenceFromUrl(downloadUrl)
		} catch (_: IllegalArgumentException) {
			return
		}
		if (reference.bucket != storage.reference.bucket || !reference.path.startsWith("items/$itemId/")) return
		reference.delete().await()
	}
}
