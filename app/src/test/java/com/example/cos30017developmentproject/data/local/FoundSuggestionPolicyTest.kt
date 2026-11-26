package com.example.cos30017developmentproject.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoundSuggestionPolicyTest {
	@Test
	fun allowsAvailableFoundItemToAnotherUsersOpenLostItem() {
		assertTrue(
			FoundSuggestionPolicy.isEligible(
				foundType = "FOUND",
				foundStatus = ItemStatus.OPEN,
				lostType = "LOST",
				lostStatus = ItemStatus.OPEN,
				senderUid = "finder",
				lostItemOwnerUid = "owner"
			)
		)
	}

	@Test
	fun rejectsWrongTypesClosedItemsAndSelfSuggestions() {
		val eligible = { foundType: String, foundStatus: String, lostType: String, lostStatus: String,
			senderUid: String, lostOwnerUid: String ->
			FoundSuggestionPolicy.isEligible(foundType, foundStatus, lostType, lostStatus, senderUid, lostOwnerUid)
		}

		assertFalse(eligible("LOST", ItemStatus.OPEN, "LOST", ItemStatus.OPEN, "finder", "owner"))
		assertFalse(eligible("FOUND", ItemStatus.CLOSED, "LOST", ItemStatus.OPEN, "finder", "owner"))
		assertFalse(eligible("FOUND", ItemStatus.OPEN, "FOUND", ItemStatus.OPEN, "finder", "owner"))
		assertFalse(eligible("FOUND", ItemStatus.OPEN, "LOST", ItemStatus.CLAIMED, "finder", "owner"))
		assertFalse(eligible("FOUND", ItemStatus.OPEN, "LOST", ItemStatus.OPEN, "owner", "owner"))
	}
}
