package com.example.cos30017developmentproject.ui.items

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItemValidationTest {
	private fun validInput() = listOf("Backpack", "Backpack", "Library", "Found near the library")

	@Test
	fun validItemPassesValidation() {
		val input = validInput()
		assertNull(ItemValidation.validate(input[0], input[1], input[2], input[3]))
	}

	@Test
	fun blankRequiredFieldFailsValidation() {
		val input = validInput()
		assertEquals(
			ItemValidationError.REQUIRED,
			ItemValidation.validate(input[0], " ", input[2], input[3])
		)
	}

	@Test
	fun oversizedTitleFailsValidation() {
		assertEquals(
			ItemValidationError.TITLE_TOO_LONG,
			ItemValidation.validate("x".repeat(101), "Bag", "Library", "Description")
		)
	}

	@Test
	fun imageIsNotRequiredForAnItem() {
		assertEquals(
			null,
			ItemValidation.validate("Bag", "Bag", "Library", "Description")
		)
	}
}
