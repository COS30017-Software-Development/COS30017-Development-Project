package com.example.cos30017developmentproject.data.local

import kotlinx.coroutines.flow.Flow

class LostAndFoundRepository(database: AppDatabase) {
	private val users = database.userDao()
	private val lostItems = database.lostItemDao()
	private val foundItems = database.foundItemDao()

	fun observeUsers(): Flow<List<UserEntity>> = users.observeAll()
	suspend fun getUser(id: Long): UserEntity? = users.getById(id)
	suspend fun addUser(user: UserEntity): Long = users.insert(user)
	suspend fun updateUser(user: UserEntity) = users.update(user)
	suspend fun deleteUser(user: UserEntity) = users.delete(user)

	fun observeLostItems(): Flow<List<LostItemEntity>> = lostItems.observeAll()
	suspend fun getLostItem(id: Long): LostItemEntity? = lostItems.getById(id)
	suspend fun addLostItem(item: LostItemEntity): Long = lostItems.insert(item)
	suspend fun updateLostItem(item: LostItemEntity) = lostItems.update(item)
	suspend fun deleteLostItem(item: LostItemEntity) = lostItems.delete(item)

	fun observeFoundItems(): Flow<List<FoundItemEntity>> = foundItems.observeAll()
	suspend fun getFoundItem(id: Long): FoundItemEntity? = foundItems.getById(id)
	suspend fun addFoundItem(item: FoundItemEntity): Long = foundItems.insert(item)
	suspend fun updateFoundItem(item: FoundItemEntity) = foundItems.update(item)
	suspend fun deleteFoundItem(item: FoundItemEntity) = foundItems.delete(item)
}
