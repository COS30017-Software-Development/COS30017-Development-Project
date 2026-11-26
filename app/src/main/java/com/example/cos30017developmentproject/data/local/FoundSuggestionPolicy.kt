package com.example.cos30017developmentproject.data.local

object FoundSuggestionPolicy {
	fun isEligible(
		foundType: String,
		foundStatus: String,
		lostType: String,
		lostStatus: String,
		senderUid: String,
		lostItemOwnerUid: String
	): Boolean =
		foundType == "FOUND" &&
			foundStatus != ItemStatus.CLOSED &&
			lostType == "LOST" &&
			lostStatus == ItemStatus.OPEN &&
			senderUid != lostItemOwnerUid
}
