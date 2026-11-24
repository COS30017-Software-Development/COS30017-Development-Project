package com.example.cos30017developmentproject.data.local

object ClaimReviewPolicy {
	fun canProcess(status: String): Boolean = status == ClaimStatus.PENDING
	fun approvedItemStatus(): String = ItemStatus.CLOSED
}
