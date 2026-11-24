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
}
