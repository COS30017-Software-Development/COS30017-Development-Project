package com.example.cos30017developmentproject.data.local

object ClaimValidation {
	const val MIN_DESCRIPTION_LENGTH = 10
	const val MAX_DESCRIPTION_LENGTH = 500

	fun validateDescription(description: String): ClaimDescriptionValidation =
		description.trim().let { trimmed ->
			when {
				trimmed.length < MIN_DESCRIPTION_LENGTH -> ClaimDescriptionValidation.TOO_SHORT
				trimmed.length > MAX_DESCRIPTION_LENGTH -> ClaimDescriptionValidation.TOO_LONG
				else -> ClaimDescriptionValidation.VALID
			}
		}
}

enum class ClaimDescriptionValidation {
	VALID,
	TOO_SHORT,
	TOO_LONG
}
