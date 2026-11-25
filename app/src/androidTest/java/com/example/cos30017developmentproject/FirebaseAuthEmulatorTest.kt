package com.example.cos30017developmentproject

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.EditText
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cos30017developmentproject.auth.FirebaseAuthManager
import com.example.cos30017developmentproject.auth.SessionManager
import com.example.cos30017developmentproject.auth.UserRepository
import com.example.cos30017developmentproject.data.local.AppDatabase
import com.example.cos30017developmentproject.data.local.LostAndFoundRepository
import com.example.cos30017developmentproject.data.local.UserEntity
import com.example.cos30017developmentproject.data.local.UserRole
import com.google.firebase.FirebaseNetworkException
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FirebaseAuthEmulatorTest {
	private val context get() = ApplicationProvider.getApplicationContext<Context>()
	private lateinit var authManager: FirebaseAuthManager
	private lateinit var database: AppDatabase
	private lateinit var userRepository: UserRepository

	@Before
	fun setUp() {
		FirebaseAuthEmulatorConfig.requireConfigured()
		authManager = FirebaseAuthManager()
		authManager.signOut()
		context.getSharedPreferences("auth_session", Context.MODE_PRIVATE).edit().clear().commit()
		database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
			.allowMainThreadQueries()
			.build()
		userRepository = UserRepository(database.userDao())
	}

	@After
	fun tearDown() {
		if (::authManager.isInitialized) authManager.signOut()
		if (::database.isInitialized) database.close()
		context.getSharedPreferences("auth_session", Context.MODE_PRIVATE).edit().clear().commit()
	}

	@Test
	fun registrationDuplicateEmailCredentialErrorsLoginAndLogout() = runBlocking {
		val email = uniqueEmail()
		val registration = authManager.register(email, PASSWORD)
		assertTrue(registration is FirebaseAuthManager.AuthResult.Success)
		val firebaseUser = (registration as FirebaseAuthManager.AuthResult.Success).user
		val roomUser = userRepository.registerFirebaseUser(
			firebaseUid = firebaseUser.uid,
			name = "Emulator Student",
			studentId = uniqueStudentId(),
			email = email
		)
		assertNotNull(roomUser)
		assertEquals(firebaseUser.uid, roomUser?.firebaseUid)
		assertEquals(UserRole.STUDENT, roomUser?.role)

		authManager.signOut()
		val duplicate = authManager.register(email, PASSWORD)
		assertEquals(
			FirebaseAuthManager.AuthError.EMAIL_ALREADY_IN_USE,
			(duplicate as FirebaseAuthManager.AuthResult.Failure).error
		)
		val wrongPassword = authManager.login(email, "WrongPassword1!")
		assertEquals(
			FirebaseAuthManager.AuthError.INVALID_CREDENTIALS,
			(wrongPassword as FirebaseAuthManager.AuthResult.Failure).error
		)
		val unknownEmail = authManager.login(uniqueEmail(), PASSWORD)
		assertEquals(
			FirebaseAuthManager.AuthError.INVALID_CREDENTIALS,
			(unknownEmail as FirebaseAuthManager.AuthResult.Failure).error
		)

		val login = authManager.login(email, PASSWORD)
		assertTrue(login is FirebaseAuthManager.AuthResult.Success)
		assertEquals(firebaseUser.uid, (login as FirebaseAuthManager.AuthResult.Success).user.uid)
		val linkedUser = userRepository.findOrLinkFirebaseUser(firebaseUser.uid, email)
		assertEquals(roomUser?.id, linkedUser?.id)

		val session = SessionManager(context)
		session.login(requireNotNull(linkedUser))
		assertEquals(roomUser?.id, session.userId)
		session.logout()
		assertFalse(authManager.isLoggedIn)
		assertNull(session.userId)
	}

	@Test
	fun aNewSessionManagerRecoversTheFirebaseAndRoomSessionPair() = runBlocking {
		val email = uniqueEmail()
		val firebaseUser = registerFirebaseUser(email)
		val roomUser = requireNotNull(
			userRepository.registerFirebaseUser(
				firebaseUid = firebaseUser.uid,
				name = "Persistent Student",
				studentId = uniqueStudentId(),
				email = email
			)
		)
		SessionManager(context).login(roomUser)

		assertEquals(roomUser.id, SessionManager(context).userId)
		assertEquals(firebaseUser.uid, authManager.getCurrentUser()?.uid)
	}

	@Test
	fun sessionRejectsRoomUserMappedToADifferentFirebaseUid() = runBlocking {
		val firebaseUser = registerFirebaseUser(uniqueEmail())
		val wrongRoomUser = UserEntity(
			username = "Wrong mapping",
			email = requireNotNull(firebaseUser.email),
			studentId = uniqueStudentId(),
			firebaseUid = "not-${firebaseUser.uid}"
		)
		val roomId = database.userDao().insert(wrongRoomUser)

		assertNull(userRepository.findOrLinkFirebaseUser(firebaseUser.uid, requireNotNull(firebaseUser.email)))
		SessionManager(context).login(wrongRoomUser.copy(id = roomId))

		assertNull(SessionManager(context).userId)
	}

	@Test
	fun existingFirebaseUserWithoutRoomProfileIsSignedOutByAuthActivity() {
		val firebaseUser = runBlocking { registerFirebaseUser(uniqueEmail()) }
		SessionManager(context).login(
			UserEntity(
				id = 42L,
				username = "Stale profile",
				email = requireNotNull(firebaseUser.email),
				studentId = uniqueStudentId(),
				firebaseUid = firebaseUser.uid
			)
		)
		val scenario = ActivityScenario.launch(AuthActivity::class.java)
		try {
			awaitCondition { !authManager.isLoggedIn }
			scenario.onActivity { activity ->
				assertEquals(
					activity.getString(R.string.auth_profile_missing_error),
					activity.findViewById<TextInputLayout>(R.id.auth_email_layout).error
				)
			}
		} finally {
			scenario.close()
		}
	}

	@Test
	fun changingRoomRoleToAdminDoesNotGrantAccessToAdminDashboard() = runBlocking {
		val email = uniqueEmail()
		val firebaseUser = registerFirebaseUser(email)
		val appDatabase = AppDatabase.getInstance(context)
		val appUsers = UserRepository(appDatabase.userDao())
		val roomUser = requireNotNull(
			appUsers.registerFirebaseUser(
				firebaseUid = firebaseUser.uid,
				name = "Ordinary Student",
				studentId = uniqueStudentId(),
				email = email
			)
		)
		val forgedAdmin = roomUser.copy(role = UserRole.ADMIN)
		appDatabase.userDao().update(forgedAdmin)
		SessionManager(context).login(forgedAdmin)
		assertFalse(LostAndFoundRepository(appDatabase).isAdmin(roomUser.id))

		try {
			ActivityScenario.launch(AdminDashboardActivity::class.java).use {
				awaitCondition { !authManager.isLoggedIn }
			}
		} finally {
			runBlocking { appDatabase.userDao().delete(forgedAdmin) }
		}
	}

	@Test
	fun roomEmailConflictRollsBackNewFirebaseRegistration() = runBlocking {
		val email = uniqueEmail()
		val appDatabase = AppDatabase.getInstance(context)
		val conflictingUser = UserEntity(
			username = "Existing local profile",
			email = email,
			studentId = uniqueStudentId(),
			firebaseUid = "unrelated-firebase-uid"
		)
		val conflictId = appDatabase.userDao().insert(conflictingUser)
		val scenario = ActivityScenario.launch(AuthActivity::class.java)
		try {
			scenario.onActivity { activity ->
				activity.findViewById<View>(R.id.auth_switch).performClick()
				activity.findViewById<EditText>(R.id.auth_name).setText("New Student")
				activity.findViewById<EditText>(R.id.auth_student_id).setText(uniqueStudentId())
				activity.findViewById<EditText>(R.id.auth_email).setText(email)
				activity.findViewById<EditText>(R.id.auth_password).setText(PASSWORD)
				activity.findViewById<EditText>(R.id.auth_confirm_password).setText(PASSWORD)
				activity.findViewById<View>(R.id.auth_submit).performClick()
			}
			awaitCondition {
				var rollbackMessageShown = false
				scenario.onActivity { activity ->
					rollbackMessageShown =
						activity.findViewById<TextInputLayout>(R.id.auth_email_layout).error ==
							activity.getString(R.string.auth_room_save_rolled_back_error)
				}
				rollbackMessageShown
			}
			assertFalse(authManager.isLoggedIn)

			val retry = authManager.register(email, PASSWORD)
			assertTrue("The failed registration should have removed its Firebase account",
				retry is FirebaseAuthManager.AuthResult.Success)
			val retryUser = (retry as FirebaseAuthManager.AuthResult.Success).user
			assertEquals(
				FirebaseAuthManager.AccountDeletionResult.Deleted,
				authManager.deleteCurrentUser(retryUser.uid)
			)
		} finally {
			scenario.close()
			runBlocking { appDatabase.userDao().delete(conflictingUser.copy(id = conflictId)) }
		}
	}

	@Test
	fun tokenRefreshFailureFailsClosedForAdminRole() = runBlocking {
		val isAdmin = FirebaseAuthManager.hasAdminRoleAfterRefresh {
			throw FirebaseNetworkException("emulated network unavailable")
		}

		assertFalse(isAdmin)
	}

	private suspend fun registerFirebaseUser(email: String): FirebaseAuthManager.AuthenticatedUser {
		val result = authManager.register(email, PASSWORD)
		assertTrue(result is FirebaseAuthManager.AuthResult.Success)
		return (result as FirebaseAuthManager.AuthResult.Success).user
	}

	private fun uniqueEmail() = "auth-${UUID.randomUUID()}@example.test"

	private fun uniqueStudentId() = UUID.randomUUID().toString()
		.filter(Char::isDigit)
		.padEnd(9, '0')
		.take(9)

	private fun awaitCondition(timeoutMillis: Long = 10_000, condition: () -> Boolean) {
		val instrumentation = InstrumentationRegistry.getInstrumentation()
		val deadline = SystemClock.uptimeMillis() + timeoutMillis
		while (!condition() && SystemClock.uptimeMillis() < deadline) {
			instrumentation.waitForIdleSync()
			SystemClock.sleep(50)
		}
		assertTrue("Timed out waiting for Firebase/Auth UI state", condition())
	}

	private companion object {
		const val PASSWORD = "EmulatorTest1!"
	}
}
