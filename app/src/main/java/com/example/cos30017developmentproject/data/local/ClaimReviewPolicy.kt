package com.example.cos30017developmentproject.data.local

object ClaimReviewPolicy {
	fun canProcess(status: String): Boolean =
		status == ClaimStatus.PENDING || status == LEGACY_PENDING

	fun approvedItemStatus(): String = ItemStatus.PENDING_RETURN

	private const val LEGACY_PENDING = "PENDING"
}
