package com.example.cos30017developmentproject.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
	tableName = "users",
	indices = [
		Index(value = ["email"], unique = true),
		Index(value = ["firebase_uid"], unique = true)
	]
)
data class UserEntity(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	val username: String,
	val email: String,
	@ColumnInfo(name = "student_id") val studentId: String,
	@ColumnInfo(name = "avatar_path") val avatarPath: String? = null,
	@ColumnInfo(name = "password_hash") val passwordHash: ByteArray? = null,
	@ColumnInfo(name = "password_salt") val passwordSalt: ByteArray? = null,
	@ColumnInfo(name = "role") val role: String = UserRole.STUDENT,
	@ColumnInfo(name = "firebase_uid") val firebaseUid: String? = null
)

@Entity(
	tableName = "lost_items",
	foreignKeys = [ForeignKey(
		entity = UserEntity::class,
		parentColumns = ["id"],
		childColumns = ["posted_by_user_id"],
		onDelete = ForeignKey.CASCADE
	)],
	indices = [Index(value = ["posted_by_user_id"])]
)
data class LostItemEntity(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	val title: String,
	val description: String,
	val location: String,
	val category: String,
	@ColumnInfo(name = "image_name") val imageName: String,
	@ColumnInfo(name = "posted_by_user_id") val postedByUserId: Long
)

@Entity(
	tableName = "found_items",
	foreignKeys = [ForeignKey(
		entity = UserEntity::class,
		parentColumns = ["id"],
		childColumns = ["posted_by_user_id"],
		onDelete = ForeignKey.CASCADE
	)],
	indices = [Index(value = ["posted_by_user_id"])]
)
data class FoundItemEntity(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	val title: String,
	val description: String,
	val location: String,
	val category: String,
	@ColumnInfo(name = "image_name") val imageName: String,
	@ColumnInfo(name = "posted_by_user_id") val postedByUserId: Long,
	@ColumnInfo(name = "status") val status: String = ItemStatus.OPEN
)

@Entity(
	tableName = "claims",
	foreignKeys = [
		ForeignKey(
			entity = FoundItemEntity::class,
			parentColumns = ["id"],
			childColumns = ["found_item_id"],
			onDelete = ForeignKey.CASCADE
		),
		ForeignKey(
			entity = UserEntity::class,
			parentColumns = ["id"],
			childColumns = ["claimant_user_id"],
			onDelete = ForeignKey.CASCADE
		)
	],
	indices = [
		Index(value = ["found_item_id"]),
		Index(value = ["claimant_user_id"]),
		Index(value = ["claimant_user_id", "found_item_id"], unique = true)
	]
)
data class ClaimEntity(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	@ColumnInfo(name = "found_item_id") val foundItemId: Long,
	@ColumnInfo(name = "claimant_user_id") val claimantUserId: Long,
	val description: String,
	val status: String = ClaimStatus.PENDING,
	@ColumnInfo(name = "submitted_at") val submittedAt: Long = System.currentTimeMillis()
)

object UserRole {
	const val STUDENT = "STUDENT"
	const val ADMIN = "ADMIN"
}

object ItemStatus {
	const val OPEN = "OPEN"
	const val CLAIMED = "CLAIMED"
	const val PENDING_RETURN = "PENDING_RETURN"
	const val CLOSED = "CLOSED"
	const val ACTIVE = OPEN
}

object ClaimStatus {
	const val PENDING = "Pending"
	const val APPROVED = "Accepted"
	const val REJECTED = "Rejected"
}
