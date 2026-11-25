package com.example.cos30017developmentproject.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthValidatorTest {
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
