package com.example.cos30017developmentproject.ui.claims

data class ClaimCard(
	val claimId: String,
	val itemId: String,
	val itemType: String,
	val title: String,
	val itemLocation: String,
	val description: String,
	val status: String,
	val submittedAt: Long,
	val senderName: String,
	val isIncoming: Boolean
)
