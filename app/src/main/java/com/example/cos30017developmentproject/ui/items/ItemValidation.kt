package com.example.cos30017developmentproject.ui.items

enum class ItemValidationError {
	REQUIRED,
	TITLE_TOO_LONG,
	CATEGORY_TOO_LONG,
	LOCATION_TOO_LONG,
	DESCRIPTION_TOO_LONG
}

object ItemValidation {
	const val TITLE_MAX_LENGTH = 100
	const val CATEGORY_MAX_LENGTH = 50
	const val LOCATION_MAX_LENGTH = 100
	const val DESCRIPTION_MAX_LENGTH = 1000
	fun validate(
		title: String,
		category: String,
		location: String,
		description: String
	): ItemValidationError? = when {
		listOf(title, category, location, description).any(String::isBlank) ->
			ItemValidationError.REQUIRED
		title.length > TITLE_MAX_LENGTH -> ItemValidationError.TITLE_TOO_LONG
		category.length > CATEGORY_MAX_LENGTH -> ItemValidationError.CATEGORY_TOO_LONG
		location.length > LOCATION_MAX_LENGTH -> ItemValidationError.LOCATION_TOO_LONG
		description.length > DESCRIPTION_MAX_LENGTH -> ItemValidationError.DESCRIPTION_TOO_LONG
		else -> null
	}
}
