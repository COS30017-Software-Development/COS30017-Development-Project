package com.example.cos30017developmentproject.ui.claims

data class ClaimCard(
	val foundItemId: String,
	val itemType: String,
	val title: String,
	val description: String,
	val status: String,
	val submittedAt: Long
)
