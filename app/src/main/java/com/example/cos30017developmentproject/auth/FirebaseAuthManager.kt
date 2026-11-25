package com.example.cos30017developmentproject.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import android.util.Log
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
		USER_DISABLED,
		NETWORK,
		TOO_MANY_REQUESTS,
		EMAIL_PROVIDER_DISABLED,
		UNKNOWN
	}

	sealed interface AuthResult {
		data class Success(val user: AuthenticatedUser) : AuthResult
		data class Failure(val error: AuthError) : AuthResult
	}

	sealed interface AccountDeletionResult {
		data object Deleted : AccountDeletionResult
		data class Failure(val error: AuthError) : AccountDeletionResult
	}

	fun getCurrentUser(): AuthenticatedUser? = firebaseAuth.currentUser?.toAuthenticatedUser()

	val isLoggedIn: Boolean
		get() = firebaseAuth.currentUser != null

	suspend fun hasAdminRole(firebaseUid: String): Boolean {
		val user = firebaseAuth.currentUser ?: return false
		if (user.uid != firebaseUid) return false
		return hasAdminRoleAfterRefresh { user.getIdToken(true).await().claims }
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

	fun signOutIfCurrentUser(firebaseUid: String) {
		if (firebaseAuth.currentUser?.uid == firebaseUid) {
			firebaseAuth.signOut()
		}
	}

	suspend fun deleteCurrentUser(expectedUid: String): AccountDeletionResult {
		val user = firebaseAuth.currentUser
			?: return AccountDeletionResult.Failure(AuthError.UNKNOWN)
		if (user.uid != expectedUid) return AccountDeletionResult.Failure(AuthError.UNKNOWN)
		return try {
			user.delete().await()
			AccountDeletionResult.Deleted
		} catch (exception: CancellationException) {
			throw exception
		} catch (exception: FirebaseNetworkException) {
			AccountDeletionResult.Failure(AuthError.NETWORK)
		} catch (exception: FirebaseAuthException) {
			AccountDeletionResult.Failure(errorForCode(exception.errorCode))
		} catch (exception: FirebaseException) {
			AccountDeletionResult.Failure(AuthError.UNKNOWN)
		}
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
		internal fun hasAdminRoleClaim(claims: Map<String, Any>): Boolean =
			claims["role"] == "ADMIN"

		internal suspend fun hasAdminRoleAfterRefresh(
			getClaims: suspend () -> Map<String, Any>
		): Boolean = try {
			hasAdminRoleClaim(getClaims())
		} catch (exception: CancellationException) {
			throw exception
		} catch (exception: FirebaseException) {
			Log.w(TAG, "Unable to refresh the ID token for admin claim verification", exception)
			false
		}

		internal fun errorForCode(code: String?): AuthError = when (
			code?.lowercase()?.removePrefix("error_")?.replace('_', '-')
		) {
			"email-already-in-use" -> AuthError.EMAIL_ALREADY_IN_USE
			"invalid-email" -> AuthError.INVALID_EMAIL
			"weak-password" -> AuthError.WEAK_PASSWORD
			"wrong-password", "invalid-password", "user-not-found", "email-not-found",
			"invalid-credential", "invalid-login-credentials" -> AuthError.INVALID_CREDENTIALS
			"user-disabled" -> AuthError.USER_DISABLED
			"too-many-requests" -> AuthError.TOO_MANY_REQUESTS
			"operation-not-allowed" -> AuthError.EMAIL_PROVIDER_DISABLED
			"network-request-failed" -> AuthError.NETWORK
			else -> AuthError.UNKNOWN
		}

		private const val TAG = "FirebaseAuthManager"
	}
}
