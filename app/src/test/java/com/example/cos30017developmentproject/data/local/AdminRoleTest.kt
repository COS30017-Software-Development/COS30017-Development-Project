package com.example.cos30017developmentproject.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AdminRoleTest {
	@Test
	fun studentIsTheDefaultUserRole() {
		val user = UserEntity(
			username = "Student",
			email = "student@example.com",
			studentId = "100000001"
		)
		assertEquals(UserRole.STUDENT, user.role)
	}

	@Test
	fun adminAndStudentRolesAreDistinct() {
		assertNotEquals(UserRole.ADMIN, UserRole.STUDENT)
	}
}
