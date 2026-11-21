package com.example.cos30017developmentproject.auth

import android.database.sqlite.SQLiteConstraintException
import com.example.cos30017developmentproject.data.local.UserDao
import com.example.cos30017developmentproject.data.local.UserEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserRepository(
	private val userDao: UserDao,
	private val passwordHasher: PasswordHasher = PasswordHasher()
) {
	sealed interface AuthResult {
		data class Success(val user: UserEntity) : AuthResult
		data object DuplicateEmail : AuthResult
		data object InvalidCredentials : AuthResult
		data object InvalidRegistration : AuthResult
	}

	suspend fun register(name: String, studentId: String, email: String, password: CharArray): AuthResult {
		if (!AuthValidator.isNameValid(name) ||
			!AuthValidator.isStudentIdValid(studentId) ||
			!AuthValidator.isEmailValid(email) ||
			!AuthValidator.isPasswordValid(password.concatToString())
		) return AuthResult.InvalidRegistration
		val normalizedEmail = email.trim().lowercase()
		if (userDao.getByEmail(normalizedEmail) != null) return AuthResult.DuplicateEmail
		val passwordHash = withContext(Dispatchers.Default) { passwordHasher.hash(password) }
		val user = UserEntity(
			username = name.trim(),
			email = normalizedEmail,
			studentId = studentId,
			passwordHash = passwordHash.hash,
			passwordSalt = passwordHash.salt
		)
		val id = try {
			userDao.insert(user)
		} catch (_: SQLiteConstraintException) {
			return AuthResult.DuplicateEmail
		}
		return AuthResult.Success(user.copy(id = id))
	}

	suspend fun login(email: String, password: CharArray): AuthResult {
		if (!AuthValidator.isEmailValid(email) || password.isEmpty()) return AuthResult.InvalidCredentials
		val user = userDao.getByEmail(email.trim().lowercase()) ?: return AuthResult.InvalidCredentials
		val hash = user.passwordHash ?: return AuthResult.InvalidCredentials
		val salt = user.passwordSalt ?: return AuthResult.InvalidCredentials
		val valid = withContext(Dispatchers.Default) { passwordHasher.verify(password, hash, salt) }
		return if (valid) AuthResult.Success(user) else AuthResult.InvalidCredentials
	}

	suspend fun getUser(id: Long) = userDao.getById(id)

	suspend fun updateUsername(id: Long, name: String): Boolean {
		val username = name.trim()
		return AuthValidator.isNameValid(username) && userDao.updateUsername(id, username) == 1
	}
}
