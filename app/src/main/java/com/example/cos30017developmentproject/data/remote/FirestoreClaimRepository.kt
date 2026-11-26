package com.example.cos30017developmentproject.data.remote

import com.example.cos30017developmentproject.auth.FirebaseAuthManager
import com.example.cos30017developmentproject.data.local.ClaimDescriptionValidation
import com.example.cos30017developmentproject.data.local.ClaimReviewResult
import com.example.cos30017developmentproject.data.local.ClaimStatus
import com.example.cos30017developmentproject.data.local.ClaimSubmissionResult
import com.example.cos30017developmentproject.data.local.ClaimValidation
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
	val itemType: String,
	val studentId: String,
	val ownerUid: String,
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
	private val notifications = firestore.collection(NOTIFICATIONS_COLLECTION)

	val currentUserUid: String?
		get() = firebaseAuth.currentUser?.uid

	suspend fun isAdmin(firebaseUid: String): Boolean =
		FirebaseAuthManager(firebaseAuth).hasAdminRole(firebaseUid)

	fun observeMyClaims(): Flow<List<FirestoreClaim>> {
		val uid = currentUserUid
			?: return callbackFlow { close(IllegalStateException("A Firebase user must be signed in")) }
		return observeClaims(claims.whereEqualTo(FIELD_STUDENT_ID, uid))
	}

	fun observeIncomingRequests(): Flow<List<FirestoreClaim>> {
		val uid = currentUserUid
			?: return callbackFlow { close(IllegalStateException("A Firebase user must be signed in")) }
		return callbackFlow {
			val requestRegistrations = mutableMapOf<String, ListenerRegistration>()
			val requestsByItem = mutableMapOf<String, List<FirestoreClaim>>()
			fun publishRequests() {
				synchronized(requestRegistrations) {
					trySend(
						requestsByItem.values.flatten()
							.distinctBy(FirestoreClaim::claimId)
							.sortedByDescending(FirestoreClaim::submittedAt)
					)
				}
			}

			val itemRegistration = items.whereEqualTo(FIELD_POSTED_BY, uid)
				.addSnapshotListener { itemSnapshot, itemError ->
					if (itemError != null) {
						close(itemError)
					} else if (itemSnapshot == null) {
						close(IllegalStateException("Firestore returned an empty owned-item snapshot"))
					} else {
						val itemIds = itemSnapshot.documents.mapNotNull { item ->
							item.getString(FIELD_ITEM_ID) ?: item.id.takeIf { item.exists() }
						}.toSet()
						synchronized(requestRegistrations) {
							(requestRegistrations.keys - itemIds).forEach { removedItemId ->
								requestRegistrations.remove(removedItemId)?.remove()
								requestsByItem.remove(removedItemId)
							}
							(itemIds - requestRegistrations.keys).forEach { itemId ->
								requestRegistrations[itemId] = claims
									.whereEqualTo(FIELD_ITEM_ID, itemId)
									.addSnapshotListener { requestSnapshot, requestError ->
										if (requestError != null) {
											close(requestError)
										} else if (requestSnapshot == null) {
											close(IllegalStateException("Firestore returned an empty item-request snapshot"))
										} else {
											try {
												synchronized(requestRegistrations) {
													requestsByItem[itemId] = requestSnapshot.documents
														.mapNotNull { it.toFirestoreClaim(uid) }
													publishRequests()
												}
											} catch (exception: Exception) {
												close(exception)
											}
										}
									}
							}
							publishRequests()
						}
					}
				}
			awaitClose {
				itemRegistration.remove()
				synchronized(requestRegistrations) {
					requestRegistrations.values.forEach(ListenerRegistration::remove)
					requestRegistrations.clear()
					requestsByItem.clear()
				}
			}
		}
	}

	fun observeAllClaims(): Flow<List<FirestoreClaim>> = observeClaims(claims)

	suspend fun hasPendingClaim(itemId: String): Boolean {
		val uid = currentUserUid ?: return false
		val document = claims.document(claimDocumentId(uid, itemId)).get().await()
		return document.exists() && document.getString(FIELD_STATUS).isNotRejected()
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
		val notificationReference = notifications.document()
		return try {
			firestore.runTransaction { transaction ->
				val item = transaction.get(itemReference)
				if (!item.exists()) return@runTransaction ClaimSubmissionResult.ITEM_NOT_FOUND
				val itemType = item.getString(FIELD_TYPE)
					?: return@runTransaction ClaimSubmissionResult.ITEM_NOT_FOUND
				if (itemType !in setOf(FirestoreItemRepository.TYPE_FOUND, FirestoreItemRepository.TYPE_LOST)) {
					return@runTransaction ClaimSubmissionResult.ITEM_NOT_FOUND
				}
				val ownerUid = item.getString(FIELD_POSTED_BY)
					?: return@runTransaction ClaimSubmissionResult.ITEM_NOT_FOUND
				if (ownerUid == uid) return@runTransaction ClaimSubmissionResult.OWNER_CANNOT_CLAIM
				if (item.getString(FIELD_STATUS) == ItemStatus.CLOSED) {
					return@runTransaction ClaimSubmissionResult.ITEM_CLOSED
				}

				val existingClaim = transaction.get(claimReference)
				if (existingClaim.exists() && existingClaim.getString(FIELD_STATUS).isNotRejected()) {
					return@runTransaction ClaimSubmissionResult.ALREADY_CLAIMED
				}
				val itemTitle = item.getString(FIELD_ITEM_NAME)
					?: return@runTransaction ClaimSubmissionResult.ITEM_NOT_FOUND
				val itemLocation = item.getString(FIELD_ITEM_LOCATION)
					?: return@runTransaction ClaimSubmissionResult.ITEM_NOT_FOUND
				val submittedAt = Timestamp.now()
				val senderName = studentName.trim().take(MAX_NOTIFICATION_SENDER_NAME_LENGTH)
				val requestType = if (itemType == FirestoreItemRepository.TYPE_FOUND) {
					FirestoreNotification.TYPE_CLAIM_REQUEST
				} else {
					FirestoreNotification.TYPE_FOUND_REQUEST
				}
				val requestData = mapOf(
					FIELD_CLAIM_ID to claimReference.id,
					FIELD_NOTIFICATION_ID to notificationReference.id,
					FIELD_ITEM_ID to itemId,
					FIELD_ITEM_TYPE to itemType,
					FIELD_STUDENT_ID to uid,
					FIELD_OWNER_UID to ownerUid,
					FIELD_DESCRIPTION to validatedDescription,
					FIELD_STATUS to ClaimStatus.PENDING,
					FIELD_SUBMITTED_AT to submittedAt,
					FIELD_ITEM_TITLE to itemTitle,
					FIELD_CLAIM_ITEM_LOCATION to itemLocation,
					FIELD_STUDENT_NAME to senderName,
					FIELD_STUDENT_EMAIL to studentEmail,
					FIELD_STUDENT_NUMBER to studentNumber
				)
				transaction.set(claimReference, requestData)
				transaction.set(
					notificationReference,
					mapOf(
						FIELD_NOTIFICATION_RECIPIENT_UID to ownerUid,
						FIELD_NOTIFICATION_SENDER_UID to uid,
						FIELD_NOTIFICATION_SENDER_NAME to senderName,
						FIELD_NOTIFICATION_TYPE to requestType,
						FIELD_NOTIFICATION_REQUEST_ID to claimReference.id,
						FIELD_ITEM_ID to itemId,
						FIELD_NOTIFICATION_ITEM_TYPE to itemType,
						FIELD_NOTIFICATION_ITEM_TITLE to itemTitle,
						FIELD_NOTIFICATION_ITEM_LOCATION to itemLocation,
						FIELD_NOTIFICATION_RELATED_ITEM_ID to null,
						FIELD_NOTIFICATION_RELATED_ITEM_TYPE to null,
						FIELD_NOTIFICATION_RELATED_ITEM_TITLE to null,
						FIELD_NOTIFICATION_RELATED_ITEM_LOCATION to null,
						FIELD_NOTIFICATION_TITLE to requestType.notificationTitle(),
						FIELD_NOTIFICATION_MESSAGE to
							"${senderName.ifBlank { "A student" }} sent a request for \"$itemTitle\".",
						FIELD_NOTIFICATION_CREATED_AT to submittedAt,
						FIELD_NOTIFICATION_IS_READ to false
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

	suspend fun acceptRequest(claimId: String): ClaimReviewResult =
		processAcceptance(claimId, requireOwner = true)

	suspend fun approveClaim(claimId: String): ClaimReviewResult {
		if (!isCurrentUserAdmin()) return ClaimReviewResult.ACCESS_DENIED
		return processAcceptance(claimId, requireOwner = false)
	}

	private suspend fun processAcceptance(claimId: String, requireOwner: Boolean): ClaimReviewResult {
		val uid = currentUserUid ?: return ClaimReviewResult.ACCESS_DENIED
		val claimReference = claims.document(claimId)
		val notificationReference = notifications.document()
		return try {
			firestore.runTransaction { transaction ->
				val claim = transaction.get(claimReference)
				if (!claim.exists()) return@runTransaction ClaimReviewResult.NOT_FOUND
				if (!claim.getString(FIELD_STATUS).isPending()) {
					return@runTransaction ClaimReviewResult.ALREADY_PROCESSED
				}
				val itemId = claim.getString(FIELD_ITEM_ID)
					?: return@runTransaction ClaimReviewResult.ITEM_NOT_FOUND
				val itemReference = items.document(itemId)
				val item = transaction.get(itemReference)
				if (!item.exists()) return@runTransaction ClaimReviewResult.ITEM_NOT_FOUND
				val ownerUid = item.getString(FIELD_POSTED_BY)
					?: return@runTransaction ClaimReviewResult.ITEM_NOT_FOUND
				if (requireOwner && ownerUid != uid) return@runTransaction ClaimReviewResult.ACCESS_DENIED
				if (item.getString(FIELD_STATUS) == ItemStatus.CLOSED) {
					return@runTransaction ClaimReviewResult.ITEM_CLOSED
				}
				val senderUid = claim.getString(FIELD_STUDENT_ID)
					?: return@runTransaction ClaimReviewResult.NOT_FOUND
				val itemType = claim.getString(FIELD_ITEM_TYPE)
					?: item.getString(FIELD_TYPE)
					?: return@runTransaction ClaimReviewResult.ITEM_NOT_FOUND
				val itemTitle = claim.getString(FIELD_ITEM_TITLE)
					?: item.getString(FIELD_ITEM_NAME)
					?: return@runTransaction ClaimReviewResult.ITEM_NOT_FOUND
				val itemLocation = claim.getString(FIELD_CLAIM_ITEM_LOCATION)
					?: item.getString(FIELD_ITEM_LOCATION)
					?: return@runTransaction ClaimReviewResult.ITEM_NOT_FOUND
				val acceptedAt = Timestamp.now()

				transaction.update(
					claimReference,
					mapOf(
						FIELD_STATUS to ClaimStatus.APPROVED,
						FIELD_OWNER_UID to ownerUid,
						FIELD_ITEM_TYPE to itemType
					)
				)
				transaction.update(
					itemReference,
					mapOf(
						FIELD_STATUS to ItemStatus.PENDING_RETURN,
						FIELD_LAST_ACCEPTED_REQUEST_ID to claimId
					)
				)
				transaction.set(
					notificationReference,
					mapOf(
						FIELD_NOTIFICATION_RECIPIENT_UID to senderUid,
						FIELD_NOTIFICATION_SENDER_UID to ownerUid,
						FIELD_NOTIFICATION_SENDER_NAME to "",
						FIELD_NOTIFICATION_TYPE to FirestoreNotification.TYPE_REQUEST_ACCEPTED,
						FIELD_NOTIFICATION_REQUEST_ID to claimId,
						FIELD_ITEM_ID to itemId,
						FIELD_NOTIFICATION_ITEM_TYPE to itemType,
						FIELD_NOTIFICATION_ITEM_TITLE to itemTitle,
						FIELD_NOTIFICATION_ITEM_LOCATION to itemLocation,
						FIELD_NOTIFICATION_RELATED_ITEM_ID to null,
						FIELD_NOTIFICATION_RELATED_ITEM_TYPE to null,
						FIELD_NOTIFICATION_RELATED_ITEM_TITLE to null,
						FIELD_NOTIFICATION_RELATED_ITEM_LOCATION to null,
						FIELD_NOTIFICATION_TITLE to "Request accepted",
						FIELD_NOTIFICATION_MESSAGE to "Your request for \"$itemTitle\" was accepted.",
						FIELD_NOTIFICATION_CREATED_AT to acceptedAt,
						FIELD_NOTIFICATION_IS_READ to false
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
			firestore.runTransaction { transaction ->
				val claim = transaction.get(claimReference)
				if (!claim.exists()) return@runTransaction ClaimReviewResult.NOT_FOUND
				if (!claim.getString(FIELD_STATUS).isPending()) {
					return@runTransaction ClaimReviewResult.ALREADY_PROCESSED
				}
				transaction.update(claimReference, FIELD_STATUS, ClaimStatus.REJECTED)
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
					close(IllegalStateException("Firestore returned an empty request snapshot"))
				} else {
					try {
						trySend(
							snapshot.documents.mapNotNull { it.toFirestoreClaim() }
								.sortedByDescending(FirestoreClaim::submittedAt)
						)
					} catch (exception: Exception) {
						close(exception)
					}
				}
			}
			awaitClose { registration.remove() }
		}

	private fun DocumentSnapshot.toFirestoreClaim(ownerUidOverride: String? = null): FirestoreClaim? {
		if (!exists()) return null
		val submittedAt = getTimestamp(FIELD_SUBMITTED_AT)
			?: throw IllegalStateException("Firestore request is missing its submittedAt timestamp")
		return FirestoreClaim(
			claimId = getString(FIELD_CLAIM_ID) ?: id,
			itemId = getString(FIELD_ITEM_ID)
				?: throw IllegalStateException("Firestore request is missing its itemId"),
			itemType = getString(FIELD_ITEM_TYPE) ?: FirestoreItemRepository.TYPE_FOUND,
			studentId = getString(FIELD_STUDENT_ID)
				?: throw IllegalStateException("Firestore request is missing its studentId"),
			ownerUid = getString(FIELD_OWNER_UID) ?: ownerUidOverride.orEmpty(),
			description = getString(FIELD_DESCRIPTION)
				?: throw IllegalStateException("Firestore request is missing its message"),
			status = getString(FIELD_STATUS).normalizedRequestStatus(),
			submittedAt = submittedAt.toDate().time,
			itemTitle = getString(FIELD_ITEM_TITLE)
				?: throw IllegalStateException("Firestore request is missing its itemTitle"),
			itemLocation = getString(FIELD_CLAIM_ITEM_LOCATION)
				?: throw IllegalStateException("Firestore request is missing its itemLocation"),
			studentName = getString(FIELD_STUDENT_NAME).orEmpty(),
			studentEmail = getString(FIELD_STUDENT_EMAIL).orEmpty(),
			studentNumber = getString(FIELD_STUDENT_NUMBER).orEmpty()
		)
	}

	private fun String?.isPending(): Boolean = this == ClaimStatus.PENDING || this == LEGACY_PENDING

	private fun String?.isNotRejected(): Boolean = this != ClaimStatus.REJECTED && this != LEGACY_REJECTED

	private fun String?.normalizedRequestStatus(): String = when (this) {
		LEGACY_PENDING -> ClaimStatus.PENDING
		LEGACY_APPROVED -> ClaimStatus.APPROVED
		LEGACY_REJECTED -> ClaimStatus.REJECTED
		else -> this ?: ClaimStatus.PENDING
	}

	private fun String.notificationTitle(): String =
		if (this == FirestoreNotification.TYPE_CLAIM_REQUEST) "New claim request" else "New found request"

	private fun claimDocumentId(studentUid: String, itemId: String): String = "${studentUid}_$itemId"

	private companion object {
		const val ITEMS_COLLECTION = "items"
		const val CLAIMS_COLLECTION = "claims"
		const val NOTIFICATIONS_COLLECTION = "notifications"
		const val FIELD_CLAIM_ID = "claimId"
		const val FIELD_NOTIFICATION_ID = "notificationId"
		const val FIELD_ITEM_ID = "itemId"
		const val FIELD_ITEM_TYPE = "itemType"
		const val FIELD_STUDENT_ID = "studentId"
		const val FIELD_OWNER_UID = "ownerUid"
		const val FIELD_DESCRIPTION = "description"
		const val FIELD_STATUS = "status"
		const val FIELD_SUBMITTED_AT = "submittedAt"
		const val FIELD_ITEM_TITLE = "itemTitle"
		const val FIELD_CLAIM_ITEM_LOCATION = "itemLocation"
		const val FIELD_STUDENT_NAME = "studentName"
		const val FIELD_STUDENT_EMAIL = "studentEmail"
		const val FIELD_STUDENT_NUMBER = "studentNumber"
		const val FIELD_POSTED_BY = "postedBy"
		const val FIELD_TYPE = "type"
		const val FIELD_ITEM_NAME = "itemName"
		const val FIELD_ITEM_LOCATION = "location"
		const val FIELD_LAST_ACCEPTED_REQUEST_ID = "lastAcceptedRequestId"
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
		const val MAX_NOTIFICATION_SENDER_NAME_LENGTH = 100
		const val LEGACY_PENDING = "PENDING"
		const val LEGACY_APPROVED = "APPROVED"
		const val LEGACY_REJECTED = "REJECTED"
	}
}
