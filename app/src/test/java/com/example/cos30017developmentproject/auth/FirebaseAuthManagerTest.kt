package com.example.cos30017developmentproject.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class FirebaseAuthManagerTest {
	@Test
	fun mapsFirebaseAuthenticationErrors() {
		val cases = mapOf(
			"ERROR_EMAIL_ALREADY_IN_USE" to FirebaseAuthManager.AuthError.EMAIL_ALREADY_IN_USE,
			"ERROR_INVALID_EMAIL" to FirebaseAuthManager.AuthError.INVALID_EMAIL,
			"ERROR_WEAK_PASSWORD" to FirebaseAuthManager.AuthError.WEAK_PASSWORD,
			"ERROR_WRONG_PASSWORD" to FirebaseAuthManager.AuthError.INVALID_CREDENTIALS,
			"ERROR_USER_NOT_FOUND" to FirebaseAuthManager.AuthError.INVALID_CREDENTIALS,
			"ERROR_TOO_MANY_REQUESTS" to FirebaseAuthManager.AuthError.TOO_MANY_REQUESTS,
			"ERROR_OPERATION_NOT_ALLOWED" to FirebaseAuthManager.AuthError.EMAIL_PROVIDER_DISABLED,
			null to FirebaseAuthManager.AuthError.UNKNOWN
		)

		cases.forEach { (code, expected) ->
			assertEquals(expected, FirebaseAuthManager.errorForCode(code))
		}
	}
}
