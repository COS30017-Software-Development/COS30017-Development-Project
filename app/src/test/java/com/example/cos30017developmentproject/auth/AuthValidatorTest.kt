package com.example.cos30017developmentproject.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidatorTest {
	@Test
	fun passwordRequiresEveryRule() {
		assertTrue(AuthValidator.isPasswordValid("Valid1!a"))
		assertTrue(AuthValidator.isPasswordValid("Aa1!abcdefghijkl"))
		for (invalid in listOf(
			"Short1!", "Aa1!abcdefghijklM", "NoNumber!", "NoSpecial1",
			"nouppercase1!", "NOLOWERCASE1!", "Has space1A"
		)) assertFalse(invalid, AuthValidator.isPasswordValid(invalid))
	}

	@Test
	fun registrationFieldsUseExpectedFormats() {
		assertTrue(AuthValidator.isNameValid("Student Name"))
		assertFalse(AuthValidator.isNameValid("   "))
		assertTrue(AuthValidator.isStudentIdValid("123456789"))
		assertFalse(AuthValidator.isStudentIdValid("12345678"))
		assertTrue(AuthValidator.isEmailValid("student@example.com"))
		assertFalse(AuthValidator.isEmailValid("student@invalid"))
		assertFalse(AuthValidator.isEmailValid("student.example.com"))
	}
}
