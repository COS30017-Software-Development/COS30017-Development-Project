package com.example.cos30017developmentproject.ui.claims

data class AdminClaimCard(
	val claimId: Long,
	val itemTitle: String,
	val itemLocation: String,
	val applicantName: String,
	val applicantEmail: String,
	val applicantStudentId: String,
	val description: String,
	val status: String,
	val submittedAt: Long
)
