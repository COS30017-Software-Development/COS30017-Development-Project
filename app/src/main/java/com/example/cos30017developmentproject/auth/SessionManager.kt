package com.example.cos30017developmentproject.auth

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.example.cos30017developmentproject.data.local.UserEntity
import com.example.cos30017developmentproject.data.local.UserRole
import com.example.cos30017developmentproject.data.remote.FcmTokenRepository
import com.google.firebase.messaging.FirebaseMessaging

class SessionManager(context: Context) {
	private val preferences = context.getSharedPreferences("auth_session", Context.MODE_PRIVATE)
	private val firebaseAuthManager = FirebaseAuthManager()

	val userId: Long?
		get() {
			val firebaseUid = firebaseAuthManager.getCurrentUser()?.uid ?: return null
			if (preferences.getString(KEY_FIREBASE_UID, null) != firebaseUid) return null
			return preferences.getLong(KEY_USER_ID, NO_USER).takeUnless { it == NO_USER }
		}

	val role: String
		get() {
			if (userId == null) return UserRole.STUDENT
			return preferences.getString(KEY_ROLE, UserRole.STUDENT) ?: UserRole.STUDENT
		}

	fun login(user: UserEntity) {
		val firebaseUid = requireNotNull(user.firebaseUid) {
			"Cannot create a session without a Firebase UID mapping"
		}
		preferences.edit {
			putLong(KEY_USER_ID, user.id)
			putString(KEY_FIREBASE_UID, firebaseUid)
			putString(KEY_ROLE, user.role)
		}
	}

	fun updateRole(role: String) {
		if (userId == null) return
		preferences.edit {
			putString(KEY_ROLE, role)
		}
	}

	fun logout() {
		val uid = firebaseAuthManager.getCurrentUser()?.uid
		if (uid == null) {
			completeLogout()
			return
		}
		FirebaseMessaging.getInstance().token
			.addOnSuccessListener { token ->
				FcmTokenRepository.unregister(uid, token, ::completeLogout)
			}
			.addOnFailureListener { error ->
				Log.e(TAG, "Unable to retrieve FCM token during logout", error)
				completeLogout()
			}
	}

	private fun completeLogout() {
		firebaseAuthManager.signOut()
		preferences.edit {
			remove(KEY_USER_ID)
			remove(KEY_FIREBASE_UID)
			remove(KEY_ROLE)
		}
	}

	private companion object {
		const val TAG = "SessionManager"
		const val KEY_USER_ID = "user_id"
		const val KEY_FIREBASE_UID = "firebase_uid"
		const val KEY_ROLE = "role"
		const val NO_USER = -1L
	}
}
