package com.example.cos30017developmentproject.auth

import android.content.Context
import androidx.core.content.edit
import com.example.cos30017developmentproject.data.local.UserEntity
import com.example.cos30017developmentproject.data.local.UserRole

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
		get() = UserRole.STUDENT

	fun login(user: UserEntity) {
		val firebaseUid = requireNotNull(user.firebaseUid) {
			"Cannot create a session without a Firebase UID mapping"
		}
		preferences.edit {
			putLong(KEY_USER_ID, user.id)
			putString(KEY_FIREBASE_UID, firebaseUid)
			remove(KEY_ROLE)
		}
	}

	fun logout() {
		firebaseAuthManager.signOut()
		preferences.edit {
			remove(KEY_USER_ID)
			remove(KEY_FIREBASE_UID)
			remove(KEY_ROLE)
		}
	}

	private companion object {
		const val KEY_USER_ID = "user_id"
		const val KEY_FIREBASE_UID = "firebase_uid"
		const val KEY_ROLE = "role"
		const val NO_USER = -1L
	}
}
