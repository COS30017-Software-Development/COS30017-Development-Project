package com.example.cos30017developmentproject.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {
	private val hasher = PasswordHasher(iterations = 1_000)

	@Test
	fun verifiesCorrectPasswordAndRejectsWrongPassword() {
		val stored = hasher.hash("Valid1!a".toCharArray())
		assertTrue(hasher.verify("Valid1!a".toCharArray(), stored.hash, stored.salt))
		assertFalse(hasher.verify("Wrong1!a".toCharArray(), stored.hash, stored.salt))
	}

	@Test
	fun randomSaltProducesDifferentHashes() {
		val first = hasher.hash("Valid1!a".toCharArray())
		val second = hasher.hash("Valid1!a".toCharArray())
		assertNotEquals(first.salt.toList(), second.salt.toList())
		assertNotEquals(first.hash.toList(), second.hash.toList())
	}
}
