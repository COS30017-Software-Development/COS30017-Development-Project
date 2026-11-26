package com.example.cos30017developmentproject.data.remote

import com.example.cos30017developmentproject.data.local.FoundSuggestionPolicy
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

enum class FoundSuggestionSubmissionResult {
	SUCCESS,
	UNAUTHENTICATED,
	FOUND_ITEM_NOT_FOUND,
	LOST_ITEM_NOT_FOUND,
	OWNER_CANNOT_SUGGEST,
	ITEM_NOT_AVAILABLE,
	ALREADY_SUGGESTED,
	FAILED
}

class FirestoreSuggestionRepository(
	private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
	private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) {
	private val items = firestore.collection(ITEMS_COLLECTION)
	private val suggestions = firestore.collection(SUGGESTIONS_COLLECTION)
	private val notifications = firestore.collection(NOTIFICATIONS_COLLECTION)

	val currentUserUid: String?
		get() = firebaseAuth.currentUser?.uid

	suspend fun submitFoundSuggestion(
		foundItemId: String,
		lostItemId: String,
		senderName: String
	): FoundSuggestionSubmissionResult {
		val senderUid = firebaseAuth.currentUser?.uid
			?: return FoundSuggestionSubmissionResult.UNAUTHENTICATED
		val suggestionId = suggestionDocumentId(senderUid, foundItemId, lostItemId)
		val foundItemReference = items.document(foundItemId)
		val lostItemReference = items.document(lostItemId)
		val suggestionReference = suggestions.document(suggestionId)
		val notificationReference = notifications.document()
		return try {
			firestore.runTransaction { transaction ->
				val foundItem = transaction.get(foundItemReference)
				if (!foundItem.exists()) return@runTransaction FoundSuggestionSubmissionResult.FOUND_ITEM_NOT_FOUND
				val lostItem = transaction.get(lostItemReference)
				if (!lostItem.exists()) return@runTransaction FoundSuggestionSubmissionResult.LOST_ITEM_NOT_FOUND
				val existingSuggestion = transaction.get(suggestionReference)
				if (existingSuggestion.exists()) return@runTransaction FoundSuggestionSubmissionResult.ALREADY_SUGGESTED

				val lostOwnerUid = lostItem.getString(FIELD_POSTED_BY)
					?: return@runTransaction FoundSuggestionSubmissionResult.LOST_ITEM_NOT_FOUND
				if (senderUid == lostOwnerUid) {
					return@runTransaction FoundSuggestionSubmissionResult.OWNER_CANNOT_SUGGEST
				}
				if (!FoundSuggestionPolicy.isEligible(
						foundType = foundItem.getString(FIELD_TYPE).orEmpty(),
						foundStatus = foundItem.getString(FIELD_STATUS).orEmpty(),
						lostType = lostItem.getString(FIELD_TYPE).orEmpty(),
						lostStatus = lostItem.getString(FIELD_STATUS).orEmpty(),
						senderUid = senderUid,
						lostItemOwnerUid = lostOwnerUid
					)
				) {
					return@runTransaction FoundSuggestionSubmissionResult.ITEM_NOT_AVAILABLE
				}

				val foundTitle = foundItem.getString(FIELD_ITEM_NAME)
					?: return@runTransaction FoundSuggestionSubmissionResult.FOUND_ITEM_NOT_FOUND
				val foundLocation = foundItem.getString(FIELD_LOCATION)
					?: return@runTransaction FoundSuggestionSubmissionResult.FOUND_ITEM_NOT_FOUND
				val lostTitle = lostItem.getString(FIELD_ITEM_NAME)
					?: return@runTransaction FoundSuggestionSubmissionResult.LOST_ITEM_NOT_FOUND
				val lostLocation = lostItem.getString(FIELD_LOCATION)
					?: return@runTransaction FoundSuggestionSubmissionResult.LOST_ITEM_NOT_FOUND
				val senderDisplayName = senderName.trim().take(MAX_NAME_LENGTH)
				val submittedAt = Timestamp.now()

				transaction.set(
					suggestionReference,
					mapOf(
						FIELD_SUGGESTION_ID to suggestionId,
						FIELD_NOTIFICATION_ID to notificationReference.id,
						FIELD_SENDER_UID to senderUid,
						FIELD_SENDER_NAME to senderDisplayName,
						FIELD_RECIPIENT_UID to lostOwnerUid,
						FIELD_FOUND_ITEM_ID to foundItemId,
						FIELD_FOUND_ITEM_TITLE to foundTitle,
						FIELD_FOUND_ITEM_LOCATION to foundLocation,
						FIELD_LOST_ITEM_ID to lostItemId,
						FIELD_LOST_ITEM_TITLE to lostTitle,
						FIELD_LOST_ITEM_LOCATION to lostLocation,
						FIELD_SUBMITTED_AT to submittedAt
					)
				)
				val notificationSenderName = senderDisplayName.ifBlank { "A student" }
				transaction.set(
					notificationReference,
					mapOf(
						FIELD_NOTIFICATION_RECIPIENT_UID to lostOwnerUid,
						FIELD_NOTIFICATION_SENDER_UID to senderUid,
						FIELD_NOTIFICATION_SENDER_NAME to senderDisplayName,
						FIELD_NOTIFICATION_TYPE to FirestoreNotification.TYPE_FOUND_SUGGESTION,
						FIELD_NOTIFICATION_REQUEST_ID to suggestionId,
						FIELD_ITEM_ID to lostItemId,
						FIELD_ITEM_TYPE to FirestoreItemRepository.TYPE_LOST,
						FIELD_ITEM_TITLE to lostTitle,
						FIELD_ITEM_LOCATION to lostLocation,
						FIELD_RELATED_ITEM_ID to foundItemId,
						FIELD_RELATED_ITEM_TYPE to FirestoreItemRepository.TYPE_FOUND,
						FIELD_RELATED_ITEM_TITLE to foundTitle,
						FIELD_RELATED_ITEM_LOCATION to foundLocation,
						FIELD_TITLE to NOTIFICATION_TITLE,
						FIELD_MESSAGE to "$notificationSenderName suggested \"$foundTitle\" for \"$lostTitle\".",
						FIELD_CREATED_AT to submittedAt,
						FIELD_IS_READ to false
					)
				)
				FoundSuggestionSubmissionResult.SUCCESS
			}.await()
		} catch (exception: CancellationException) {
			throw exception
		} catch (_: Exception) {
			FoundSuggestionSubmissionResult.FAILED
		}
	}

	private fun suggestionDocumentId(senderUid: String, foundItemId: String, lostItemId: String): String =
		"${senderUid}_${foundItemId}_${lostItemId}"

	private companion object {
		const val ITEMS_COLLECTION = "items"
		const val SUGGESTIONS_COLLECTION = "foundSuggestions"
		const val NOTIFICATIONS_COLLECTION = "notifications"
		const val FIELD_SUGGESTION_ID = "suggestionId"
		const val FIELD_NOTIFICATION_ID = "notificationId"
		const val FIELD_SENDER_UID = "senderUid"
		const val FIELD_SENDER_NAME = "senderName"
		const val FIELD_RECIPIENT_UID = "recipientUid"
		const val FIELD_FOUND_ITEM_ID = "foundItemId"
		const val FIELD_FOUND_ITEM_TITLE = "foundItemTitle"
		const val FIELD_FOUND_ITEM_LOCATION = "foundItemLocation"
		const val FIELD_LOST_ITEM_ID = "lostItemId"
		const val FIELD_LOST_ITEM_TITLE = "lostItemTitle"
		const val FIELD_LOST_ITEM_LOCATION = "lostItemLocation"
		const val FIELD_SUBMITTED_AT = "submittedAt"
		const val FIELD_POSTED_BY = "postedBy"
		const val FIELD_TYPE = "type"
		const val FIELD_STATUS = "status"
		const val FIELD_ITEM_NAME = "itemName"
		const val FIELD_LOCATION = "location"
		const val FIELD_NOTIFICATION_RECIPIENT_UID = "recipientUid"
		const val FIELD_NOTIFICATION_SENDER_UID = "senderUid"
		const val FIELD_NOTIFICATION_SENDER_NAME = "senderName"
		const val FIELD_NOTIFICATION_TYPE = "type"
		const val FIELD_NOTIFICATION_REQUEST_ID = "relatedRequestId"
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
		const val MAX_NAME_LENGTH = 100
		const val NOTIFICATION_TITLE = "Found item suggestion"
	}
}
