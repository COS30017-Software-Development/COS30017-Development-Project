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

	@Insert(onConflict = OnConflictStrategy.ABORT)
	suspend fun insert(user: UserEntity): Long

	@Update
	suspend fun update(user: UserEntity)

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

	@Delete
	suspend fun delete(item: LostItemEntity)
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

	@Delete
	suspend fun delete(item: FoundItemEntity)
}
