package com.example.cos30017developmentproject.auth

object AuthValidator {

	fun isNameValid(name: String) = name.trim().isNotEmpty()

	fun isStudentIdValid(studentId: String) = studentId.matches(Regex("\\d{9}"))

	fun isEmailValid(email: String) = email.trim().let { '@' in it && '.' in it }
}
