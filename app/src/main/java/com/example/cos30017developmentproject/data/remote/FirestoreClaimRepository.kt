package com.example.cos30017developmentproject.data.remote

import com.example.cos30017developmentproject.auth.FirebaseAuthManager
import com.example.cos30017developmentproject.data.local.ClaimReviewPolicy
import com.example.cos30017developmentproject.data.local.ClaimStatus
import com.example.cos30017developmentproject.data.local.ClaimSubmissionResult
import com.example.cos30017developmentproject.data.local.ClaimReviewResult
import com.example.cos30017developmentproject.data.local.ClaimValidation
import com.example.cos30017developmentproject.data.local.ClaimDescriptionValidation
import com.example.cos30017developmentproject.data.local.ItemStatus
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class FirestoreClaim(
	val claimId: String,
	val itemId: String,
	val studentId: String,
	val description: String,
	val status: String,
	val submittedAt: Long,
	val itemTitle: String,
	val itemLocation: String,
	val studentName: String,
	val studentEmail: String,
	val studentNumber: String
)

class FirestoreClaimRepository(
	private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
	private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) {
	private val claims: CollectionReference = firestore.collection(CLAIMS_COLLECTION)
	private val items = firestore.collection(ITEMS_COLLECTION)

	val currentUserUid: String?
		get() = firebaseAuth.currentUser?.uid

	suspend fun isAdmin(firebaseUid: String): Boolean =
		FirebaseAuthManager(firebaseAuth).hasAdminRole(firebaseUid)

	fun observeMyClaims(): Flow<List<FirestoreClaim>> {
		val uid = currentUserUid
			?: return callbackFlow { close(IllegalStateException("A Firebase user must be signed in")) }
		return observeClaims(claims.whereEqualTo(FIELD_STUDENT_ID, uid))
	}

	fun observeAllClaims(): Flow<List<FirestoreClaim>> = observeClaims(claims)

	suspend fun hasPendingClaim(itemId: String): Boolean {
		val uid = currentUserUid ?: return false
		val document = claims.document(claimDocumentId(uid, itemId)).get().await()
		return document.exists() && document.getString(FIELD_STATUS) == ClaimStatus.PENDING
	}

	suspend fun submitClaim(
		itemId: String,
		description: String,
		studentName: String,
		studentEmail: String,
		studentNumber: String
	): ClaimSubmissionResult {
		val uid = currentUserUid ?: return ClaimSubmissionResult.FAILED
		val validatedDescription = description.trim()
		when (ClaimValidation.validateDescription(validatedDescription)) {
			ClaimDescriptionValidation.TOO_SHORT -> return ClaimSubmissionResult.DESCRIPTION_TOO_SHORT
			ClaimDescriptionValidation.TOO_LONG -> return ClaimSubmissionResult.DESCRIPTION_TOO_LONG
			ClaimDescriptionValidation.VALID -> Unit
		}

		val itemReference = items.document(itemId)
		val claimReference = claims.document(claimDocumentId(uid, itemId))
		val notificationReference = firestore.collection(NOTIFICATIONS_COLLECTION).document()
		return try {
			firestore.runTransaction { transaction ->
				val item = transaction.get(itemReference)
				if (!item.exists()) return@runTransaction ClaimSubmissionResult.ITEM_NOT_FOUND
				if (item.getString(FIELD_ITEM_TYPE) != FirestoreItemRepository.TYPE_FOUND) {
					return@runTransaction ClaimSubmissionResult.ITEM_NOT_FOUND
				}
				if (item.getString(FIELD_POSTED_BY) == uid) {
					return@runTransaction ClaimSubmissionResult.OWNER_CANNOT_CLAIM
				}
				val itemStatus = item.getString(FIELD_STATUS)
				if (itemStatus == ItemStatus.CLOSED) return@runTransaction ClaimSubmissionResult.ITEM_CLOSED
				if (itemStatus != FirestoreItemRepository.STATUS_OPEN && itemStatus != ItemStatus.CLAIMED) {
					return@runTransaction ClaimSubmissionResult.FAILED
				}
				val pendingCount = item.getLong(FIELD_PENDING_CLAIM_COUNT) ?: 0L
				if (pendingCount < 0L) return@runTransaction ClaimSubmissionResult.FAILED

				val existingClaim = transaction.get(claimReference)
				if (existingClaim.exists() && existingClaim.getString(FIELD_STATUS) != ClaimStatus.REJECTED) {
					return@runTransaction ClaimSubmissionResult.ALREADY_CLAIMED
				}

				val itemTitle = item.getString(FIELD_ITEM_NAME)
					?: return@runTransaction ClaimSubmissionResult.ITEM_NOT_FOUND
				val itemLocation = item.getString(FIELD_ITEM_LOCATION)
					?: return@runTransaction ClaimSubmissionResult.ITEM_NOT_FOUND
				val submittedAt = Timestamp.now()
				val notificationSenderName = studentName.trim().take(MAX_NOTIFICATION_SENDER_NAME_LENGTH)
				val claimData = mapOf(
					FIELD_CLAIM_ID to claimReference.id,
					FIELD_NOTIFICATION_ID to notificationReference.id,
					FIELD_ITEM_ID to itemId,
					FIELD_STUDENT_ID to uid,
					FIELD_DESCRIPTION to validatedDescription,
					FIELD_STATUS to ClaimStatus.PENDING,
					FIELD_SUBMITTED_AT to submittedAt,
					FIELD_ITEM_TITLE to itemTitle,
					FIELD_CLAIM_ITEM_LOCATION to itemLocation,
					FIELD_STUDENT_NAME to studentName,
					FIELD_STUDENT_EMAIL to studentEmail,
					FIELD_STUDENT_NUMBER to studentNumber
				)
				transaction.set(claimReference, claimData)
				transaction.update(
					itemReference,
					mapOf(
						FIELD_STATUS to ItemStatus.CLAIMED,
						FIELD_PENDING_CLAIM_COUNT to pendingCount + 1
					)
				)
				transaction.set(
					notificationReference,
					mapOf(
						FIELD_NOTIFICATION_RECIPIENT_UID to item.getString(FIELD_POSTED_BY),
						FIELD_NOTIFICATION_SENDER_UID to uid,
						FIELD_NOTIFICATION_SENDER_NAME to notificationSenderName,
						FIELD_NOTIFICATION_TYPE to FirestoreNotification.TYPE_CLAIM_REQUEST,
						FIELD_NOTIFICATION_REQUEST_ID to claimReference.id,
						FIELD_ITEM_ID to itemId,
						FIELD_NOTIFICATION_ITEM_TYPE to FirestoreItemRepository.TYPE_FOUND,
						FIELD_NOTIFICATION_ITEM_TITLE to itemTitle,
						FIELD_NOTIFICATION_ITEM_LOCATION to itemLocation,
						FIELD_NOTIFICATION_RELATED_ITEM_ID to null,
						FIELD_NOTIFICATION_RELATED_ITEM_TYPE to null,
						FIELD_NOTIFICATION_RELATED_ITEM_TITLE to null,
						FIELD_NOTIFICATION_RELATED_ITEM_LOCATION to null,
						FIELD_NOTIFICATION_TITLE to NOTIFICATION_TITLE,
						FIELD_NOTIFICATION_MESSAGE to
							"${notificationSenderName.ifBlank { "A student" }} submitted a claim for \"$itemTitle\".",
						FIELD_NOTIFICATION_CREATED_AT to submittedAt,
						FIELD_NOTIFICATION_IS_READ to false,
					)
				)
				ClaimSubmissionResult.SUCCESS
			}.await()
		} catch (exception: CancellationException) {
			throw exception
		} catch (_: Exception) {
			ClaimSubmissionResult.FAILED
		}
	}

	suspend fun approveClaim(claimId: String): ClaimReviewResult {
		if (!isCurrentUserAdmin()) return ClaimReviewResult.ACCESS_DENIED
		val claimReference = claims.document(claimId)
		return try {
			val existingClaim = claimReference.get().await()
			if (!existingClaim.exists()) return ClaimReviewResult.NOT_FOUND
			val itemId = existingClaim.getString(FIELD_ITEM_ID)
				?: return ClaimReviewResult.ITEM_NOT_FOUND
			firestore.runTransaction { transaction ->
				val claim = transaction.get(claimReference)
				if (!claim.exists()) return@runTransaction ClaimReviewResult.NOT_FOUND
				if (!ClaimReviewPolicy.canProcess(claim.getString(FIELD_STATUS).orEmpty())) {
					return@runTransaction ClaimReviewResult.ALREADY_PROCESSED
				}
				if (claim.getString(FIELD_ITEM_ID) != itemId) {
					return@runTransaction ClaimReviewResult.ITEM_NOT_FOUND
				}
				val itemReference = items.document(itemId)
				val item = transaction.get(itemReference)
				if (!item.exists()) return@runTransaction ClaimReviewResult.ITEM_NOT_FOUND
				if (item.getString(FIELD_STATUS) == ItemStatus.CLOSED) {
					return@runTransaction ClaimReviewResult.ITEM_CLOSED
				}
				val pendingCount = item.getLong(FIELD_PENDING_CLAIM_COUNT) ?: 0L
				if (pendingCount <= 0L) return@runTransaction ClaimReviewResult.FAILED

				transaction.update(claimReference, FIELD_STATUS, ClaimStatus.APPROVED)
				transaction.update(
					itemReference,
					mapOf(
						FIELD_STATUS to ClaimReviewPolicy.approvedItemStatus(),
						FIELD_PENDING_CLAIM_COUNT to pendingCount - 1,
						FIELD_LAST_REVIEWED_CLAIM_ID to claimId
					)
				)
				ClaimReviewResult.SUCCESS
			}.await()
		} catch (exception: CancellationException) {
			throw exception
		} catch (_: Exception) {
			ClaimReviewResult.FAILED
		}
	}

	suspend fun rejectClaim(claimId: String): ClaimReviewResult {
		if (!isCurrentUserAdmin()) return ClaimReviewResult.ACCESS_DENIED
		val claimReference = claims.document(claimId)
		return try {
			val existingClaim = claimReference.get().await()
			if (!existingClaim.exists()) return ClaimReviewResult.NOT_FOUND
			val itemId = existingClaim.getString(FIELD_ITEM_ID)
				?: return ClaimReviewResult.ITEM_NOT_FOUND
			firestore.runTransaction { transaction ->
				val claim = transaction.get(claimReference)
				if (!claim.exists()) return@runTransaction ClaimReviewResult.NOT_FOUND
				if (!ClaimReviewPolicy.canProcess(claim.getString(FIELD_STATUS).orEmpty())) {
					return@runTransaction ClaimReviewResult.ALREADY_PROCESSED
				}
				if (claim.getString(FIELD_ITEM_ID) != itemId) {
					return@runTransaction ClaimReviewResult.ITEM_NOT_FOUND
				}
				val itemReference = items.document(itemId)
				val item = transaction.get(itemReference)
				if (!item.exists()) return@runTransaction ClaimReviewResult.ITEM_NOT_FOUND
				val pendingCount = item.getLong(FIELD_PENDING_CLAIM_COUNT) ?: 0L
				if (pendingCount <= 0L) return@runTransaction ClaimReviewResult.FAILED

				val remainingPendingCount = pendingCount - 1
				val itemStatus = item.getString(FIELD_STATUS)
				val nextItemStatus = if (itemStatus == ItemStatus.CLOSED) {
					ItemStatus.CLOSED
				} else if (remainingPendingCount > 0L) {
					ItemStatus.CLAIMED
				} else {
					ItemStatus.OPEN
				}
				transaction.update(claimReference, FIELD_STATUS, ClaimStatus.REJECTED)
				transaction.update(
					itemReference,
					mapOf(
						FIELD_STATUS to nextItemStatus,
						FIELD_PENDING_CLAIM_COUNT to remainingPendingCount,
						FIELD_LAST_REVIEWED_CLAIM_ID to claimId
					)
				)
				ClaimReviewResult.SUCCESS
			}.await()
		} catch (exception: CancellationException) {
			throw exception
		} catch (_: Exception) {
			ClaimReviewResult.FAILED
		}
	}

	private suspend fun isCurrentUserAdmin(): Boolean =
		currentUserUid?.let { isAdmin(it) } == true

	private fun observeClaims(query: com.google.firebase.firestore.Query): Flow<List<FirestoreClaim>> =
		callbackFlow {
			val registration: ListenerRegistration = query.addSnapshotListener { snapshot, error ->
				if (error != null) {
					close(error)
				} else if (snapshot == null) {
					close(IllegalStateException("Firestore returned an empty claim snapshot"))
				} else {
					try {
						trySend(snapshot.documents.mapNotNull { it.toFirestoreClaim() }
							.sortedByDescending(FirestoreClaim::submittedAt))
					} catch (exception: Exception) {
						close(exception)
					}
				}
			}
			awaitClose { registration.remove() }
		}

	private fun DocumentSnapshot.toFirestoreClaim(): FirestoreClaim? {
		if (!exists()) return null
		val submittedAt = getTimestamp(FIELD_SUBMITTED_AT)
			?: throw IllegalStateException("Firestore claim is missing its submittedAt timestamp")
		return FirestoreClaim(
			claimId = getString(FIELD_CLAIM_ID)
				?: throw IllegalStateException("Firestore claim is missing its claimId"),
			itemId = getString(FIELD_ITEM_ID)
				?: throw IllegalStateException("Firestore claim is missing its itemId"),
			studentId = getString(FIELD_STUDENT_ID)
				?: throw IllegalStateException("Firestore claim is missing its studentId"),
			description = getString(FIELD_DESCRIPTION)
				?: throw IllegalStateException("Firestore claim is missing its description"),
			status = getString(FIELD_STATUS)
				?: throw IllegalStateException("Firestore claim is missing its status"),
			submittedAt = submittedAt.toDate().time,
			itemTitle = getString(FIELD_ITEM_TITLE)
				?: throw IllegalStateException("Firestore claim is missing its itemTitle"),
			itemLocation = getString(FIELD_CLAIM_ITEM_LOCATION)
				?: throw IllegalStateException("Firestore claim is missing its itemLocation"),
			studentName = getString(FIELD_STUDENT_NAME).orEmpty(),
			studentEmail = getString(FIELD_STUDENT_EMAIL).orEmpty(),
			studentNumber = getString(FIELD_STUDENT_NUMBER).orEmpty()
		)
	}

	private fun claimDocumentId(studentUid: String, itemId: String): String = "${studentUid}_$itemId"

	private companion object {
		const val ITEMS_COLLECTION = "items"
		const val CLAIMS_COLLECTION = "claims"
		const val FIELD_CLAIM_ID = "claimId"
		const val FIELD_NOTIFICATION_ID = "notificationId"
		const val FIELD_ITEM_ID = "itemId"
		const val FIELD_STUDENT_ID = "studentId"
		const val FIELD_DESCRIPTION = "description"
		const val FIELD_STATUS = "status"
		const val FIELD_SUBMITTED_AT = "submittedAt"
		const val FIELD_ITEM_TITLE = "itemTitle"
		const val FIELD_CLAIM_ITEM_LOCATION = "itemLocation"
		const val FIELD_STUDENT_NAME = "studentName"
		const val FIELD_STUDENT_EMAIL = "studentEmail"
		const val FIELD_STUDENT_NUMBER = "studentNumber"
		const val FIELD_ITEM_TYPE = "type"
		const val FIELD_POSTED_BY = "postedBy"
		const val FIELD_ITEM_NAME = "itemName"
		const val FIELD_ITEM_LOCATION = "location"
		const val FIELD_PENDING_CLAIM_COUNT = "pendingClaimCount"
		const val FIELD_LAST_REVIEWED_CLAIM_ID = "lastReviewedClaimId"
		const val NOTIFICATIONS_COLLECTION = "notifications"
		const val FIELD_NOTIFICATION_RECIPIENT_UID = "recipientUid"
		const val FIELD_NOTIFICATION_SENDER_UID = "senderUid"
		const val FIELD_NOTIFICATION_SENDER_NAME = "senderName"
		const val FIELD_NOTIFICATION_TYPE = "type"
		const val FIELD_NOTIFICATION_REQUEST_ID = "relatedRequestId"
		const val FIELD_NOTIFICATION_ITEM_TYPE = "itemType"
		const val FIELD_NOTIFICATION_ITEM_TITLE = "itemTitle"
		const val FIELD_NOTIFICATION_ITEM_LOCATION = "itemLocation"
		const val FIELD_NOTIFICATION_RELATED_ITEM_ID = "relatedItemId"
		const val FIELD_NOTIFICATION_RELATED_ITEM_TYPE = "relatedItemType"
		const val FIELD_NOTIFICATION_RELATED_ITEM_TITLE = "relatedItemTitle"
		const val FIELD_NOTIFICATION_RELATED_ITEM_LOCATION = "relatedItemLocation"
		const val FIELD_NOTIFICATION_TITLE = "title"
		const val FIELD_NOTIFICATION_MESSAGE = "message"
		const val FIELD_NOTIFICATION_CREATED_AT = "createdAt"
		const val FIELD_NOTIFICATION_IS_READ = "isRead"
		const val NOTIFICATION_TITLE = "New claim request"
		const val MAX_NOTIFICATION_SENDER_NAME_LENGTH = 100
	}

}
