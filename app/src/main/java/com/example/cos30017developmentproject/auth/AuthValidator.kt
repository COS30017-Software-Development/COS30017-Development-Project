package com.example.cos30017developmentproject.auth

object AuthValidator {
	private val emailPattern = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

	fun isNameValid(name: String) = name.trim().isNotEmpty()

	fun isStudentIdValid(studentId: String) = studentId.matches(Regex("\\d{9}"))

	fun isEmailValid(email: String) = emailPattern.matches(email.trim())

	fun isPasswordValid(password: String) = password.length in 8..16 &&
		password.any(Char::isDigit) &&
		password.any { !it.isLetterOrDigit() && !it.isWhitespace() } &&
		password.any(Char::isUpperCase) &&
		password.any(Char::isLowerCase)
}
