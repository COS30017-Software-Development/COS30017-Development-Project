package com.example.cos30017developmentproject.auth

import android.database.sqlite.SQLiteConstraintException
import com.example.cos30017developmentproject.data.local.UserDao
import com.example.cos30017developmentproject.data.local.UserEntity

class UserRepository(private val userDao: UserDao) {
	suspend fun findOrLinkFirebaseUser(firebaseUid: String, email: String): UserEntity? {
		if (firebaseUid.isBlank() || email.isBlank()) return null
		userDao.getByFirebaseUid(firebaseUid)?.let { return it }

		val user = userDao.getByEmail(email.trim().lowercase()) ?: return null
		if (user.firebaseUid != null && user.firebaseUid != firebaseUid) return null
		if (user.firebaseUid == null && userDao.attachFirebaseUid(user.id, firebaseUid) != 1) {
			return userDao.getByFirebaseUid(firebaseUid)
		}
		return user.copy(firebaseUid = firebaseUid)
	}

	suspend fun registerFirebaseUser(
		firebaseUid: String,
		name: String,
		studentId: String,
		email: String
	): UserEntity? {
		if (firebaseUid.isBlank() || !AuthValidator.isNameValid(name) ||
			!AuthValidator.isStudentIdValid(studentId) || !AuthValidator.isEmailValid(email)
		) return null

		findOrLinkFirebaseUser(firebaseUid, email)?.let { return it }
		val user = UserEntity(
			username = name.trim(),
			email = email.trim().lowercase(),
			studentId = studentId,
			firebaseUid = firebaseUid
		)
		return try {
			user.copy(id = userDao.insert(user))
		} catch (exception: SQLiteConstraintException) {
			findOrLinkFirebaseUser(firebaseUid, email) ?: throw exception
		}
	}

	suspend fun getUser(id: Long) = userDao.getById(id)

	suspend fun updateUsername(id: Long, name: String): Boolean {
		val username = name.trim()
		return AuthValidator.isNameValid(username) && userDao.updateUsername(id, username) == 1
	}

	suspend fun updateProfile(id: Long, name: String, avatarPath: String?): Boolean {
		val username = name.trim()
		return AuthValidator.isNameValid(username) && userDao.updateProfile(id, username, avatarPath) == 1
	}
}
