package com.example.cos30017developmentproject.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "users", indices = [Index(value = ["email"], unique = true)])
data class UserEntity(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	val username: String,
	val email: String,
	@ColumnInfo(name = "student_id") val studentId: String,
	@ColumnInfo(name = "password_hash") val passwordHash: ByteArray? = null,
	@ColumnInfo(name = "password_salt") val passwordSalt: ByteArray? = null
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
	@ColumnInfo(name = "posted_by_user_id") val postedByUserId: Long
)
