package com.example.cos30017developmentproject.auth

import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PasswordHasher(
	private val iterations: Int = 120_000,
	private val keyLength: Int = 256
) {
	data class PasswordHash(val hash: ByteArray, val salt: ByteArray)

	fun hash(password: CharArray): PasswordHash {
		val salt = ByteArray(16).also(SecureRandom()::nextBytes)
		return PasswordHash(derive(password, salt), salt)
	}

	fun verify(password: CharArray, expectedHash: ByteArray, salt: ByteArray): Boolean {
		val actualHash = derive(password, salt)
		var difference = actualHash.size xor expectedHash.size
		for (index in actualHash.indices) {
			difference = difference or (actualHash[index].toInt() xor expectedHash.getOrElse(index) { 0 }.toInt())
		}
		return difference == 0
	}

	private fun derive(password: CharArray, salt: ByteArray): ByteArray {
		val spec: KeySpec = PBEKeySpec(password, salt, iterations, keyLength)
		return try {
			SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
		} finally {
			(spec as PBEKeySpec).clearPassword()
		}
	}
}
