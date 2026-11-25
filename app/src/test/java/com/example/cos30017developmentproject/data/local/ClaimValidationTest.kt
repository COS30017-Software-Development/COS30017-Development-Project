package com.example.cos30017developmentproject.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class ClaimValidationTest {
	@Test
	fun descriptionBelowMinimumIsRejected() {
		assertEquals(ClaimDescriptionValidation.TOO_SHORT, ClaimValidation.validateDescription("  short  "))
	}

	@Test
	fun descriptionAtMinimumIsValid() {
		assertEquals(ClaimDescriptionValidation.VALID, ClaimValidation.validateDescription("1234567890"))
	}

	@Test
	fun descriptionAtMaximumIsValid() {
		assertEquals(ClaimDescriptionValidation.VALID, ClaimValidation.validateDescription("x".repeat(500)))
	}

	@Test
	fun descriptionAboveMaximumIsRejected() {
		assertEquals(
			ClaimDescriptionValidation.TOO_LONG,
			ClaimValidation.validateDescription("x".repeat(501))
		)
	}

	@Test
	fun whitespaceOnlyDescriptionIsRejected() {
		assertEquals(ClaimDescriptionValidation.TOO_SHORT, ClaimValidation.validateDescription("   "))
	}
}
