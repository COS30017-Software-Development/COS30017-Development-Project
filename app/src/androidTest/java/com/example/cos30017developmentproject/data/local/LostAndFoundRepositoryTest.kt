package com.example.cos30017developmentproject.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LostAndFoundRepositoryTest {
	private lateinit var database: AppDatabase
	private lateinit var repository: LostAndFoundRepository
	private val context get() = ApplicationProvider.getApplicationContext<Context>()

	@Before
	fun setUp() {
		database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		repository = LostAndFoundRepository(database)
	}

	@After
	fun tearDown() {
		database.close()
	}

	@Test
	fun roomPersistsOwnedLostAndFoundItemCrud() = runBlocking {
		val userId = database.userDao().insert(
			UserEntity(username = "Student", email = "student@test.local", studentId = "123456789")
		)
		val lostId = repository.addLostItem(
			LostItemEntity(
				title = "Keys",
				description = "Keys near library",
				location = "Library",
				category = "Keys",
				imageName = "sample_keys",
				postedByUserId = userId
			)
		)
		assertNotNull(repository.getLostItem(lostId))
		assertTrue(repository.updateLostItemOwned(lostId, userId, "Updated keys", "Updated", "Library", "Keys", "sample_keys"))
		assertFalse(repository.updateLostItemOwned(lostId, userId + 1, "Wrong", "Wrong", "Wrong", "Keys", "sample_keys"))
		assertTrue(repository.deleteLostItemOwned(lostId, userId))
		assertEquals(null, repository.getLostItem(lostId))

		val foundId = repository.addFoundItem(
			FoundItemEntity(
				title = "Bottle",
				description = "Bottle near classroom",
				location = "Classroom",
				category = "Bottle",
				imageName = "sample_bottle",
				postedByUserId = userId
			)
		)
		assertTrue(repository.updateFoundItemOwned(foundId, userId, "Updated bottle", "Updated", "Library", "Bottle", "sample_bottle"))
		assertFalse(repository.deleteFoundItemOwned(foundId, userId + 1))
		assertTrue(repository.deleteFoundItemOwned(foundId, userId))
		assertEquals(null, repository.getFoundItem(foundId))
	}

	@Test
	fun requestRejectsOwnerClosedAndDuplicateSubmissions() = runBlocking {
		val ownerId = database.userDao().insert(
			UserEntity(username = "Owner", email = "owner@test.local", studentId = "100000001")
		)
		val claimantId = database.userDao().insert(
			UserEntity(username = "Claimant", email = "claimant@test.local", studentId = "100000002")
	 )
		val foundId = repository.addFoundItem(
			FoundItemEntity(
				title = "Bag",
				description = "Bag in library",
				location = "Library",
				category = "Bag",
				imageName = "sample_backpack",
				postedByUserId = ownerId
			)
		)

		assertEquals(
			ClaimSubmissionResult.OWNER_CANNOT_CLAIM,
			repository.submitClaim(foundId, ownerId, "This is a valid claim description")
		)
		assertEquals(
			ClaimSubmissionResult.DESCRIPTION_TOO_SHORT,
			repository.submitClaim(foundId, claimantId, "short")
		)
		assertEquals(
			ClaimSubmissionResult.SUCCESS,
			repository.submitClaim(foundId, claimantId, "This bag belongs to me")
		)
		assertEquals(ItemStatus.OPEN, repository.getFoundItem(foundId)?.status)
		assertEquals(
			ClaimSubmissionResult.ALREADY_CLAIMED,
			repository.submitClaim(foundId, claimantId, "This is another claim")
		)

		database.foundItemDao().updateStatus(foundId, ItemStatus.CLOSED)
		assertEquals(
			ClaimSubmissionResult.ITEM_CLOSED,
			repository.submitClaim(foundId, claimantId + 1, "This description is valid")
		)
	}

	@Test
	fun rejectedRoomRequestCanBeResubmittedWithoutCreatingDuplicate() = runBlocking {
		val ownerId = database.userDao().insert(
			UserEntity(username = "Owner", email = "resubmit-owner@test.local", studentId = "110000001")
		)
		val claimantId = database.userDao().insert(
			UserEntity(username = "Claimant", email = "resubmit-claimant@test.local", studentId = "110000002")
		)
		val foundId = repository.addFoundItem(
			FoundItemEntity(
				title = "Umbrella",
				description = "Umbrella by the entrance",
				location = "Entrance",
				category = "Umbrella",
				imageName = "sample_backpack",
				postedByUserId = ownerId
			)
		)

		assertEquals(
			ClaimSubmissionResult.SUCCESS,
			repository.submitClaim(foundId, claimantId, "The umbrella has my initials")
		)
		val originalClaim = database.claimDao().getByClaimantAndItem(claimantId, foundId)
			?: error("Missing original claim")
		assertEquals(1, database.claimDao().updateStatusIfPending(originalClaim.id, ClaimStatus.REJECTED))
		assertEquals(1, database.foundItemDao().updateStatusIfClaimed(foundId, ItemStatus.OPEN))
		assertFalse(repository.hasClaim(claimantId, foundId))

		assertEquals(
			ClaimSubmissionResult.SUCCESS,
			repository.submitClaim(foundId, claimantId, "Here is more information about ownership")
		)
		val resubmittedClaim = database.claimDao().getByClaimantAndItem(claimantId, foundId)
			?: error("Missing resubmitted claim")
		assertEquals(originalClaim.id, resubmittedClaim.id)
		assertEquals(ClaimStatus.PENDING, resubmittedClaim.status)
		assertEquals("Here is more information about ownership", resubmittedClaim.description)
		assertEquals(ItemStatus.OPEN, repository.getFoundItem(foundId)?.status)
		assertEquals(1, database.claimDao().countPendingForItem(foundId))
	}

	@Test
	fun ownerAcceptsOneRequestWithoutChangingOtherRequests() = runBlocking {
		val ownerId = database.userDao().insert(
			UserEntity(username = "Owner", email = "owner-accept@test.local", studentId = "120000001")
		)
		val firstSenderId = database.userDao().insert(
			UserEntity(username = "First", email = "first-request@test.local", studentId = "120000002")
		)
		val secondSenderId = database.userDao().insert(
			UserEntity(username = "Second", email = "second-request@test.local", studentId = "120000003")
		)
		val foundId = repository.addFoundItem(
			FoundItemEntity(
				title = "Wallet",
				description = "Wallet near the library",
				location = "Library",
				category = "Wallet",
				imageName = "sample_backpack",
				postedByUserId = ownerId
			)
		)
		assertEquals(
			ClaimSubmissionResult.SUCCESS,
			repository.submitClaim(foundId, firstSenderId, "The wallet contains my card")
		)
		assertEquals(
			ClaimSubmissionResult.SUCCESS,
			repository.submitClaim(foundId, secondSenderId, "I can identify the wallet")
		)
		val firstRequest = database.claimDao().getByClaimantAndItem(firstSenderId, foundId)
			?: error("Missing first request")
		val secondRequest = database.claimDao().getByClaimantAndItem(secondSenderId, foundId)
			?: error("Missing second request")

		assertEquals(ClaimReviewResult.ACCESS_DENIED, repository.acceptClaim(firstRequest.id, secondSenderId))
		assertEquals(ClaimReviewResult.SUCCESS, repository.acceptClaim(firstRequest.id, ownerId))
		assertEquals(ClaimStatus.APPROVED, database.claimDao().getById(firstRequest.id)?.status)
		assertEquals(ClaimStatus.PENDING, database.claimDao().getById(secondRequest.id)?.status)
		assertEquals(ItemStatus.PENDING_RETURN, repository.getFoundItem(foundId)?.status)
	}

	@Test
	fun localAdminRoleWithoutFirebaseClaimCannotApproveClaims() = runBlocking {
		val ownerId = database.userDao().insert(
			UserEntity(username = "Owner", email = "owner2@test.local", studentId = "200000001")
		)
		val adminId = database.userDao().insert(
			UserEntity(username = "Admin", email = "admin@test.local", studentId = "200000002", role = UserRole.ADMIN)
		)
		val claimant1 = database.userDao().insert(
			UserEntity(username = "One", email = "one@test.local", studentId = "200000003")
		)
		val claimant2 = database.userDao().insert(
			UserEntity(username = "Two", email = "two@test.local", studentId = "200000004")
		)
		val foundId = repository.addFoundItem(
			FoundItemEntity(
				title = "Watch",
				description = "Watch found near office",
				location = "Office",
				category = "Watch",
				imageName = "sample_backpack",
				postedByUserId = ownerId
			)
		)
		assertEquals(ClaimSubmissionResult.SUCCESS, repository.submitClaim(foundId, claimant1, "Watch belongs to claimant one"))
		assertEquals(ClaimSubmissionResult.SUCCESS, repository.submitClaim(foundId, claimant2, "Watch belongs to claimant two"))
		val approvedId = database.claimDao().getByClaimantAndItem(claimant1, foundId)?.id ?: error("Missing claim")

		assertEquals(ClaimReviewResult.ACCESS_DENIED, repository.approveClaim(approvedId, adminId))
		assertEquals(ItemStatus.OPEN, repository.getFoundItem(foundId)?.status)
		assertEquals(ClaimStatus.PENDING, database.claimDao().getById(approvedId)?.status)
		val rejectedId = database.claimDao().getByClaimantAndItem(claimant2, foundId)?.id ?: error("Missing claim")
		assertEquals(ClaimStatus.PENDING, database.claimDao().getById(rejectedId)?.status)
		assertEquals(ClaimReviewResult.ACCESS_DENIED, repository.approveClaim(approvedId, adminId))
	}

	@Test
	fun rejectAndAdminDeleteEnforcePermissions() = runBlocking {
		val studentId = database.userDao().insert(
			UserEntity(username = "Student", email = "student3@test.local", studentId = "300000001")
		)
		val adminId = database.userDao().insert(
			UserEntity(username = "Admin", email = "admin3@test.local", studentId = "300000002", role = UserRole.ADMIN)
		)
		val foundId = repository.addFoundItem(
			FoundItemEntity(
				title = "Card",
				description = "Card found in lecture hall",
				location = "Lecture hall",
				category = "Card",
				imageName = "sample_backpack",
				postedByUserId = studentId
			)
		)
		val claimantId = database.userDao().insert(
			UserEntity(username = "Claimant", email = "claimant3@test.local", studentId = "300000003")
		)
		assertEquals(ClaimSubmissionResult.SUCCESS, repository.submitClaim(foundId, claimantId, "This card is mine with details"))
		val claim = database.claimDao().getByClaimantAndItem(claimantId, foundId) ?: error("Missing claim")

		assertEquals(ClaimReviewResult.ACCESS_DENIED, repository.rejectClaim(claim.id, studentId))
		assertEquals(ClaimReviewResult.ACCESS_DENIED, repository.rejectClaim(claim.id, adminId))
		assertEquals(ClaimStatus.PENDING, database.claimDao().getById(claim.id)?.status)
		assertFalse(repository.deleteFoundItemAsAdmin(foundId, studentId))
		assertFalse(repository.deleteFoundItemAsAdmin(foundId, adminId))
		assertNotNull(repository.getFoundItem(foundId))
	}

	@Test
	fun onDiskRoomDataSurvivesCloseAndReopenWithUpdateAndDelete() {
		runBlocking {
			val dbName = "room-persistence-e2e.db"
			context.deleteDatabase(dbName)
			val first = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
				.allowMainThreadQueries()
				.build()
			val firstRepository = LostAndFoundRepository(first)
			val userId = first.userDao().insert(
				UserEntity(
					username = "Persistent Student",
					email = "persistent@test.local",
					studentId = "400000001"
				)
			)
			val lostId = firstRepository.addLostItem(
				LostItemEntity(
					title = "Persistent keys",
					description = "Inserted before close",
					location = "Library",
					category = "Keys",
					imageName = "sample_keys",
					postedByUserId = userId
				)
			)
			val foundId = firstRepository.addFoundItem(
				FoundItemEntity(
					title = "Persistent bottle",
					description = "Inserted before reopen",
					location = "Cafe",
					category = "Bottle",
					imageName = "sample_bottle",
					postedByUserId = userId
				)
			)
			first.close()

			val reopened = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
				.allowMainThreadQueries()
				.build()
			val reopenedRepository = LostAndFoundRepository(reopened)
			assertEquals("Persistent keys", reopenedRepository.getLostItem(lostId)?.title)
			assertEquals("Persistent bottle", reopenedRepository.getFoundItem(foundId)?.title)
			assertTrue(reopenedRepository.updateLostItemOwned(
				lostId, userId, "Updated persistent keys", "Updated", "Campus", "Keys", "sample_keys"
			))
			assertEquals("Updated persistent keys", reopenedRepository.getLostItem(lostId)?.title)
			assertTrue(reopenedRepository.deleteFoundItemOwned(foundId, userId))
			assertEquals(null, reopenedRepository.getFoundItem(foundId))
			reopened.close()
			context.deleteDatabase(dbName)
		}
	}
}
