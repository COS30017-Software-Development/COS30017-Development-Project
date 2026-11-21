package com.example.cos30017developmentproject.auth

import android.content.Context

class SessionManager(context: Context) {
	private val preferences = context.getSharedPreferences("auth_session", Context.MODE_PRIVATE)

	val userId: Long?
		get() = preferences.getLong(KEY_USER_ID, NO_USER).takeUnless { it == NO_USER }

	fun login(userId: Long) {
		preferences.edit().putLong(KEY_USER_ID, userId).apply()
	}

	fun logout() {
		preferences.edit().remove(KEY_USER_ID).apply()
	}

	private companion object {
		const val KEY_USER_ID = "user_id"
		const val NO_USER = -1L
	}
}
