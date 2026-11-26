package com.example.cos30017developmentproject.data.local

import com.example.cos30017developmentproject.auth.FirebaseAuthManager
import kotlinx.coroutines.flow.Flow
import androidx.room.withTransaction

class LostAndFoundRepository(database: AppDatabase) {
	private val database = database
	private val users = database.userDao()
	private val lostItems = database.lostItemDao()
	private val foundItems = database.foundItemDao()
	private val claims = database.claimDao()

	fun observeUsers(): Flow<List<UserEntity>> = users.observeAll()
	suspend fun getUser(id: Long): UserEntity? = users.getById(id)
	suspend fun getUserByFirebaseUid(firebaseUid: String): UserEntity? = users.getByFirebaseUid(firebaseUid)
	suspend fun addUser(user: UserEntity): Long = users.insert(user)
	suspend fun updateUser(user: UserEntity) = users.update(user)
	suspend fun deleteUser(user: UserEntity) = users.delete(user)

	fun observeLostItems(): Flow<List<LostItemEntity>> = lostItems.observeAll()
	suspend fun getLostItem(id: Long): LostItemEntity? = lostItems.getById(id)
	suspend fun addLostItem(item: LostItemEntity): Long = lostItems.insert(item)
	suspend fun updateLostItem(item: LostItemEntity): Boolean = updateLostItemOwned(
		item.id,
		item.postedByUserId,
		item.title,
		item.description,
		item.location,
		item.category,
		item.imageName
	)
	suspend fun deleteLostItem(item: LostItemEntity): Boolean =
		deleteLostItemOwned(item.id, item.postedByUserId)
	suspend fun updateLostItemOwned(
		id: Long,
		ownerId: Long,
		title: String,
		description: String,
		location: String,
		category: String,
		imageName: String
	): Boolean = lostItems.updateOwned(
		id, ownerId, title, description, location, category, imageName
	) > 0
	suspend fun deleteLostItemOwned(id: Long, ownerId: Long): Boolean =
		lostItems.deleteOwned(id, ownerId) > 0

	fun observeFoundItems(): Flow<List<FoundItemEntity>> = foundItems.observeAll()
	suspend fun getFoundItem(id: Long): FoundItemEntity? = foundItems.getById(id)
	suspend fun addFoundItem(item: FoundItemEntity): Long = foundItems.insert(item)
	suspend fun updateFoundItem(item: FoundItemEntity): Boolean = updateFoundItemOwned(
		item.id,
		item.postedByUserId,
		item.title,
		item.description,
		item.location,
		item.category,
		item.imageName
	)
	suspend fun deleteFoundItem(item: FoundItemEntity): Boolean =
		deleteFoundItemOwned(item.id, item.postedByUserId)
	suspend fun updateFoundItemOwned(
		id: Long,
		ownerId: Long,
		title: String,
		description: String,
		location: String,
		category: String,
		imageName: String
	): Boolean = foundItems.updateOwned(
		id, ownerId, title, description, location, category, imageName
	) > 0
	suspend fun deleteFoundItemOwned(id: Long, ownerId: Long): Boolean =
		foundItems.deleteOwned(id, ownerId) > 0

	fun observeClaims(userId: Long): Flow<List<ClaimEntity>> = claims.observeByClaimant(userId)
	fun observeAllClaims(): Flow<List<ClaimEntity>> = claims.observeAll()
	suspend fun hasClaim(userId: Long, foundItemId: Long): Boolean =
		claims.getByClaimantAndItem(userId, foundItemId)
			?.let { it.status != ClaimStatus.REJECTED } ?: false

	suspend fun submitClaim(foundItemId: Long, claimantUserId: Long, description: String): ClaimSubmissionResult {
		return try {
			database.withTransaction {
				val item = foundItems.getById(foundItemId)
					?: return@withTransaction ClaimSubmissionResult.ITEM_NOT_FOUND
				if (item.status == ItemStatus.CLOSED) {
					return@withTransaction ClaimSubmissionResult.ITEM_CLOSED
				}
				if (item.postedByUserId == claimantUserId) {
					return@withTransaction ClaimSubmissionResult.OWNER_CANNOT_CLAIM
				}
				val existingClaim = claims.getByClaimantAndItem(claimantUserId, foundItemId)
				if (existingClaim != null && existingClaim.status != ClaimStatus.REJECTED) {
					return@withTransaction ClaimSubmissionResult.ALREADY_CLAIMED
				}
				val validatedDescription = description.trim()
				when (ClaimValidation.validateDescription(validatedDescription)) {
					ClaimDescriptionValidation.TOO_SHORT ->
						return@withTransaction ClaimSubmissionResult.DESCRIPTION_TOO_SHORT
					ClaimDescriptionValidation.TOO_LONG ->
						return@withTransaction ClaimSubmissionResult.DESCRIPTION_TOO_LONG
					ClaimDescriptionValidation.VALID -> Unit
				}

				val submittedAt = System.currentTimeMillis()
				val id = if (existingClaim == null) {
					claims.insert(
						ClaimEntity(
							foundItemId = foundItemId,
							claimantUserId = claimantUserId,
							description = validatedDescription,
							status = ClaimStatus.PENDING,
							submittedAt = submittedAt
						)
					)
				} else {
					if (claims.resubmitRejected(existingClaim.id, validatedDescription, submittedAt) != 1) {
						return@withTransaction ClaimSubmissionResult.ALREADY_CLAIMED
					}
					existingClaim.id
				}
				if (id <= 0) return@withTransaction ClaimSubmissionResult.FAILED
				foundItems.updateStatusIfOpen(foundItemId, ItemStatus.CLAIMED)
				ClaimSubmissionResult.SUCCESS
			}
		} catch (exception: kotlinx.coroutines.CancellationException) {
			throw exception
		} catch (_: Exception) {
			ClaimSubmissionResult.FAILED
		}
	}

	suspend fun isAdmin(userId: Long): Boolean {
		val firebaseUid = users.getById(userId)?.firebaseUid ?: return false
		return FirebaseAuthManager().hasAdminRole(firebaseUid)
	}

	suspend fun approveClaim(claimId: Long, adminId: Long): ClaimReviewResult {
		if (!isAdmin(adminId)) return ClaimReviewResult.ACCESS_DENIED
		return try {
			database.withTransaction {
				val claim = claims.getById(claimId)
					?: return@withTransaction ClaimReviewResult.NOT_FOUND
				if (!ClaimReviewPolicy.canProcess(claim.status)) return@withTransaction ClaimReviewResult.ALREADY_PROCESSED
				val item = foundItems.getById(claim.foundItemId)
					?: return@withTransaction ClaimReviewResult.ITEM_NOT_FOUND
				if (item.status == ItemStatus.CLOSED) return@withTransaction ClaimReviewResult.ITEM_CLOSED
				if (foundItems.updateStatusIfNotClosed(item.id, ClaimReviewPolicy.approvedItemStatus()) != 1) {
					return@withTransaction ClaimReviewResult.ITEM_CLOSED
				}
				if (claims.updateStatusIfPending(claimId, ClaimStatus.APPROVED) != 1) {
					throw IllegalStateException("Claim processing changed concurrently")
				}
				ClaimReviewResult.SUCCESS
			}
		} catch (_: Exception) {
			ClaimReviewResult.FAILED
		}
	}

	suspend fun rejectClaim(claimId: Long, adminId: Long): ClaimReviewResult {
		if (!isAdmin(adminId)) return ClaimReviewResult.ACCESS_DENIED
		return try {
			database.withTransaction {
				val claim = claims.getById(claimId)
					?: return@withTransaction ClaimReviewResult.NOT_FOUND
				if (!ClaimReviewPolicy.canProcess(claim.status)) return@withTransaction ClaimReviewResult.ALREADY_PROCESSED
				if (claims.updateStatusIfPending(claimId, ClaimStatus.REJECTED) == 1) {
					if (claims.countPendingForItem(claim.foundItemId) == 0) {
						foundItems.updateStatusIfClaimed(claim.foundItemId, ItemStatus.OPEN)
					}
					ClaimReviewResult.SUCCESS
				} else {
					ClaimReviewResult.ALREADY_PROCESSED
				}
			}
		} catch (_: Exception) {
			ClaimReviewResult.FAILED
		}
	}

	suspend fun deleteLostItemAsAdmin(id: Long, adminId: Long): Boolean {
		if (!isAdmin(adminId)) return false
		return lostItems.deleteOwnedById(id) > 0
	}

	suspend fun deleteFoundItemAsAdmin(id: Long, adminId: Long): Boolean {
		if (!isAdmin(adminId)) return false
		return foundItems.deleteOwnedById(id) > 0
	}
}

enum class ClaimSubmissionResult {
	SUCCESS,
	ITEM_NOT_FOUND,
	ITEM_CLOSED,
	OWNER_CANNOT_CLAIM,
	ALREADY_CLAIMED,
	DESCRIPTION_TOO_SHORT,
	DESCRIPTION_TOO_LONG,
	FAILED
}

enum class ClaimReviewResult {
	SUCCESS,
	ACCESS_DENIED,
	NOT_FOUND,
	ALREADY_PROCESSED,
	ITEM_NOT_FOUND,
	ITEM_CLOSED,
	FAILED
}
