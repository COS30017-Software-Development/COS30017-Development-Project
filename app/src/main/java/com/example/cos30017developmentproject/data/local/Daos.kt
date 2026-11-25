package com.example.cos30017developmentproject.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
	@Query("SELECT * FROM users ORDER BY id")
	fun observeAll(): Flow<List<UserEntity>>

	@Query("SELECT * FROM users WHERE id = :id")
	suspend fun getById(id: Long): UserEntity?

	@Query("SELECT * FROM users WHERE email = :email COLLATE NOCASE LIMIT 1")
	suspend fun getByEmail(email: String): UserEntity?

	@Query("SELECT * FROM users WHERE firebase_uid = :firebaseUid LIMIT 1")
	suspend fun getByFirebaseUid(firebaseUid: String): UserEntity?

	@Query("UPDATE users SET firebase_uid = :firebaseUid WHERE id = :id AND (firebase_uid IS NULL OR firebase_uid = :firebaseUid)")
	suspend fun attachFirebaseUid(id: Long, firebaseUid: String): Int

	@Insert(onConflict = OnConflictStrategy.ABORT)
	suspend fun insert(user: UserEntity): Long

	@Update
	suspend fun update(user: UserEntity)

	@Query("UPDATE users SET username = :username WHERE id = :id")
	suspend fun updateUsername(id: Long, username: String): Int

	@Query("UPDATE users SET username = :username, avatar_path = :avatarPath WHERE id = :id")
	suspend fun updateProfile(id: Long, username: String, avatarPath: String?): Int

	@Delete
	suspend fun delete(user: UserEntity)
}

@Dao
interface LostItemDao {
	@Query("SELECT * FROM lost_items ORDER BY id DESC")
	fun observeAll(): Flow<List<LostItemEntity>>

	@Query("SELECT * FROM lost_items WHERE id = :id")
	suspend fun getById(id: Long): LostItemEntity?

	@Insert(onConflict = OnConflictStrategy.ABORT)
	suspend fun insert(item: LostItemEntity): Long

	@Update
	suspend fun update(item: LostItemEntity)

	@Query("""
		UPDATE lost_items
		SET title = :title, description = :description, location = :location,
		    category = :category, image_name = :imageName
		WHERE id = :id AND posted_by_user_id = :ownerId
	""")
	suspend fun updateOwned(
		id: Long,
		ownerId: Long,
		title: String,
		description: String,
		location: String,
		category: String,
		imageName: String
	): Int

	@Delete
	suspend fun delete(item: LostItemEntity)

	@Query("DELETE FROM lost_items WHERE id = :id AND posted_by_user_id = :ownerId")
	suspend fun deleteOwned(id: Long, ownerId: Long): Int

	@Query("DELETE FROM lost_items WHERE id = :id")
	suspend fun deleteOwnedById(id: Long): Int
}

@Dao
interface FoundItemDao {
	@Query("SELECT * FROM found_items ORDER BY id DESC")
	fun observeAll(): Flow<List<FoundItemEntity>>

	@Query("SELECT * FROM found_items WHERE id = :id")
	suspend fun getById(id: Long): FoundItemEntity?

	@Insert(onConflict = OnConflictStrategy.ABORT)
	suspend fun insert(item: FoundItemEntity): Long

	@Update
	suspend fun update(item: FoundItemEntity)

	@Query("""
		UPDATE found_items
		SET title = :title, description = :description, location = :location,
		    category = :category, image_name = :imageName
		WHERE id = :id AND posted_by_user_id = :ownerId
	""")
	suspend fun updateOwned(
		id: Long,
		ownerId: Long,
		title: String,
		description: String,
		location: String,
		category: String,
		imageName: String
	): Int

	@Delete
	suspend fun delete(item: FoundItemEntity)

	@Query("DELETE FROM found_items WHERE id = :id AND posted_by_user_id = :ownerId")
	suspend fun deleteOwned(id: Long, ownerId: Long): Int

	@Query("DELETE FROM found_items WHERE id = :id")
	suspend fun deleteOwnedById(id: Long): Int

	@Query("UPDATE found_items SET status = :status WHERE id = :id")
	suspend fun updateStatus(id: Long, status: String): Int

	@Query("UPDATE found_items SET status = :status WHERE id = :id AND status != 'CLOSED'")
	suspend fun updateStatusIfNotClosed(id: Long, status: String): Int

	@Query("UPDATE found_items SET status = :status WHERE id = :id AND status = 'OPEN'")
	suspend fun updateStatusIfOpen(id: Long, status: String): Int

	@Query("UPDATE found_items SET status = :status WHERE id = :id AND status = 'CLAIMED'")
	suspend fun updateStatusIfClaimed(id: Long, status: String): Int
}

@Dao
interface ClaimDao {
	@Query("SELECT * FROM claims ORDER BY submitted_at DESC")
	fun observeAll(): Flow<List<ClaimEntity>>

	@Query("SELECT * FROM claims WHERE claimant_user_id = :userId ORDER BY submitted_at DESC")
	fun observeByClaimant(userId: Long): Flow<List<ClaimEntity>>

	@Query("SELECT * FROM claims WHERE id = :id")
	suspend fun getById(id: Long): ClaimEntity?

	@Query("SELECT * FROM claims WHERE claimant_user_id = :userId AND found_item_id = :foundItemId LIMIT 1")
	suspend fun getByClaimantAndItem(userId: Long, foundItemId: Long): ClaimEntity?

	@Insert(onConflict = OnConflictStrategy.ABORT)
	suspend fun insert(claim: ClaimEntity): Long

	@Query("UPDATE claims SET status = :status WHERE id = :id AND status = 'PENDING'")
	suspend fun updateStatusIfPending(id: Long, status: String): Int

	@Query("""
		UPDATE claims
		SET description = :description, status = 'PENDING', submitted_at = :submittedAt
		WHERE id = :id AND status = 'REJECTED'
	""")
	suspend fun resubmitRejected(id: Long, description: String, submittedAt: Long): Int

	@Query("UPDATE claims SET status = 'REJECTED' WHERE found_item_id = :foundItemId AND id != :approvedClaimId AND status = 'PENDING'")
	suspend fun rejectOtherPending(foundItemId: Long, approvedClaimId: Long): Int

	@Query("SELECT COUNT(*) FROM claims WHERE found_item_id = :foundItemId AND status = 'PENDING'")
	suspend fun countPendingForItem(foundItemId: Long): Int
}
