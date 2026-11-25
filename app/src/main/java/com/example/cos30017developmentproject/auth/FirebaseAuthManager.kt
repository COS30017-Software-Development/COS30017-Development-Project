package com.example.cos30017developmentproject.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class FirebaseAuthManager(
	private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) {
	data class AuthenticatedUser(
		val uid: String,
		val email: String?,
		val displayName: String?
	)

	enum class AuthError {
		EMAIL_ALREADY_IN_USE,
		INVALID_EMAIL,
		WEAK_PASSWORD,
		INVALID_CREDENTIALS,
		NETWORK,
		TOO_MANY_REQUESTS,
		EMAIL_PROVIDER_DISABLED,
		UNKNOWN
	}

	sealed interface AuthResult {
		data class Success(val user: AuthenticatedUser) : AuthResult
		data class Failure(val error: AuthError) : AuthResult
	}

	fun getCurrentUser(): AuthenticatedUser? = firebaseAuth.currentUser?.toAuthenticatedUser()

	val isLoggedIn: Boolean
		get() = firebaseAuth.currentUser != null

	suspend fun hasAdminRole(firebaseUid: String): Boolean {
		val user = firebaseAuth.currentUser ?: return false
		if (user.uid != firebaseUid) return false
		return user.getIdToken(false).await().claims["role"] == "ADMIN"
	}

	suspend fun register(email: String, password: String): AuthResult = authenticate {
		firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).await().user?.toAuthenticatedUser()
	}

	suspend fun login(email: String, password: String): AuthResult = authenticate {
		firebaseAuth.signInWithEmailAndPassword(email.trim(), password).await().user?.toAuthenticatedUser()
	}

	fun signOut() {
		firebaseAuth.signOut()
	}

	private suspend fun authenticate(action: suspend () -> AuthenticatedUser?): AuthResult {
		return try {
			action()?.let { AuthResult.Success(it) } ?: AuthResult.Failure(AuthError.UNKNOWN)
		} catch (exception: CancellationException) {
			throw exception
		} catch (exception: FirebaseNetworkException) {
			AuthResult.Failure(AuthError.NETWORK)
		} catch (exception: FirebaseAuthException) {
			AuthResult.Failure(errorForCode(exception.errorCode))
		} catch (exception: FirebaseException) {
			AuthResult.Failure(AuthError.UNKNOWN)
		}
	}

	private fun com.google.firebase.auth.FirebaseUser.toAuthenticatedUser() = AuthenticatedUser(
		uid = uid,
		email = email,
		displayName = displayName
	)

	companion object {
		internal fun errorForCode(code: String?): AuthError = when (
			code?.lowercase()?.removePrefix("error_")?.replace('_', '-')
		) {
			"email-already-in-use" -> AuthError.EMAIL_ALREADY_IN_USE
			"invalid-email" -> AuthError.INVALID_EMAIL
			"weak-password" -> AuthError.WEAK_PASSWORD
			"wrong-password", "user-not-found", "invalid-credential",
			"invalid-login-credentials" -> AuthError.INVALID_CREDENTIALS
			"too-many-requests" -> AuthError.TOO_MANY_REQUESTS
			"operation-not-allowed" -> AuthError.EMAIL_PROVIDER_DISABLED
			"network-request-failed" -> AuthError.NETWORK
			else -> AuthError.UNKNOWN
		}
	}
}
