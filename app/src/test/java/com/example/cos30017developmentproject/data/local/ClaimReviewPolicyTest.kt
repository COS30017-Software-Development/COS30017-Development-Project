package com.example.cos30017developmentproject.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class ClaimReviewPolicyTest {
	@Test
	fun onlyPendingClaimsCanBeProcessed() {
		assertTrue(ClaimReviewPolicy.canProcess(ClaimStatus.PENDING))
		assertFalse(ClaimReviewPolicy.canProcess(ClaimStatus.APPROVED))
		assertFalse(ClaimReviewPolicy.canProcess(ClaimStatus.REJECTED))
	}

	@Test
	fun approvedClaimsCloseFoundItem() {
		assertEquals(ItemStatus.CLOSED, ClaimReviewPolicy.approvedItemStatus())
	}
}
