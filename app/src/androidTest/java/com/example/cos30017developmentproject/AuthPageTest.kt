package com.example.cos30017developmentproject

import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.cos30017developmentproject.auth.SessionManager
import com.google.android.material.navigation.NavigationView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthPageTest {
	private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

	@Test
	fun registerLoginRejectWrongPasswordAndLogoutThroughRealUi() {
		FirebaseAuthEmulatorConfig.requireConfigured()
		val context = InstrumentationRegistry.getInstrumentation().targetContext
		val session = SessionManager(context)
		session.logout()
		val suffix = System.currentTimeMillis()
		val email = "e2e-$suffix@example.test"
		val password = "Student1@Pass"

		ActivityScenario.launch(AuthActivity::class.java).use { scenario ->
			scenario.onActivity { activity ->
				activity.findViewById<View>(R.id.auth_switch).performClick()
				activity.findViewById<EditText>(R.id.auth_name).setText("E2E Student")
				activity.findViewById<EditText>(R.id.auth_student_id).setText("9${suffix.toString().takeLast(8)}")
				activity.findViewById<EditText>(R.id.auth_email).setText(email)
				activity.findViewById<EditText>(R.id.auth_password).setText(password)
				activity.findViewById<EditText>(R.id.auth_confirm_password).setText(password)
				activity.findViewById<View>(R.id.auth_submit).performClick()
			}
			val deadline = android.os.SystemClock.uptimeMillis() + 5000
			while (session.userId == null && android.os.SystemClock.uptimeMillis() < deadline) {
				instrumentation.waitForIdleSync()
				android.os.SystemClock.sleep(50)
			}
			assertTrue("Registration should establish a session", session.userId != null)
		}

		session.logout()
		assertNull(session.userId)

		ActivityScenario.launch(AuthActivity::class.java).use { scenario ->
			scenario.onActivity { activity ->
				activity.findViewById<EditText>(R.id.auth_email).setText(email)
				activity.findViewById<EditText>(R.id.auth_password).setText("Wrong1@Pass")
				activity.findViewById<View>(R.id.auth_submit).performClick()
			}
			instrumentation.waitForIdleSync()
			android.os.SystemClock.sleep(500)
			assertNull("Wrong credentials must not establish a session", session.userId)
		}

		session.logout()
		ActivityScenario.launch(AuthActivity::class.java).use { scenario ->
			scenario.onActivity { activity ->
				activity.findViewById<EditText>(R.id.auth_email).setText(email)
				activity.findViewById<EditText>(R.id.auth_password).setText(password)
				activity.findViewById<View>(R.id.auth_submit).performClick()
			}
			val deadline = android.os.SystemClock.uptimeMillis() + 5000
			while (session.userId == null && android.os.SystemClock.uptimeMillis() < deadline) {
				instrumentation.waitForIdleSync()
				android.os.SystemClock.sleep(50)
			}
			assertTrue("Correct credentials should establish a session", session.userId != null)
		}
		session.logout()
		assertNull(session.userId)
	}

	private fun checkAuth(action: (AuthActivity) -> Unit) {
		val deadline = android.os.SystemClock.uptimeMillis() + 5000
		var checked = false
		while (!checked && android.os.SystemClock.uptimeMillis() < deadline) {
			instrumentation.waitForIdleSync()
			instrumentation.runOnMainSync {
				val activity = ActivityLifecycleMonitorRegistry.getInstance()
					.getActivitiesInStage(Stage.RESUMED).filterIsInstance<AuthActivity>()
					.singleOrNull()
				if (activity != null) {
					action(activity)
					checked = true
				}
			}
			if (!checked) android.os.SystemClock.sleep(50)
		}
		assertTrue("Login activity should reach the resumed state", checked)
	}

	@Test
	fun protectedEntriesOpenLoginAndBackReturnsHome() {
		SessionManager(InstrumentationRegistry.getInstrumentation().targetContext).logout()
		for (entry in listOf(
			R.id.nav_profile, R.id.nav_post, R.id.drawer_profile, R.id.drawer_post
		)) {
			ActivityScenario.launch(MainActivity::class.java).use { scenario ->
				scenario.onActivity { main ->
					main.findViewById<View>(R.id.nav_items).performClick()
					if (entry == R.id.drawer_profile || entry == R.id.drawer_post) {
						val navigation = main.findViewById<NavigationView>(R.id.side_navigation)
						navigation.menu.performIdentifierAction(entry, 0)
					} else main.findViewById<View>(entry).performClick()
				}
				checkAuth {
					assertTrue(it.findViewById<View>(R.id.auth_email).isShown)
					assertFalse(it.findViewById<View>(R.id.auth_name_layout).isShown)
					assertFalse(it.findViewById<View>(R.id.auth_submit).isEnabled)
					it.findViewById<View>(R.id.auth_back).performClick()
				}
				val deadline = android.os.SystemClock.uptimeMillis() + 5000
				var homeShown = false
				while (!homeShown && android.os.SystemClock.uptimeMillis() < deadline) {
					instrumentation.waitForIdleSync()
					scenario.onActivity {
						homeShown = it.findViewById<View>(R.id.home_scroll).isShown
					}
					if (!homeShown) android.os.SystemClock.sleep(50)
				}
				assertTrue("Home should be visible after returning from login", homeShown)
			}
		}
	}

	@Test
	fun directlyLaunchedMyPostsRequiresAuthentication() {
		SessionManager(InstrumentationRegistry.getInstrumentation().targetContext).logout()
		ActivityScenario.launch(MyPostsActivity::class.java).use {
			checkAuth { activity ->
				assertTrue(activity.findViewById<View>(R.id.auth_email).isShown)
			}
		}
	}

	@Test
	fun registrationHasAllFieldsAndSurvivesRecreation() {
		ActivityScenario.launch(AuthActivity::class.java).use { scenario ->
			scenario.onActivity { it.findViewById<View>(R.id.auth_switch).performClick() }
			scenario.recreate()
			scenario.onActivity {
				for (field in listOf(
					R.id.auth_name_layout,
					R.id.auth_student_id_layout,
					R.id.auth_email_layout,
					R.id.auth_password_layout,
					R.id.auth_confirm_password_layout
				)) {
					assertEquals(View.VISIBLE, it.findViewById<View>(field).visibility)
				}
				assertFalse(it.findViewById<View>(R.id.auth_submit).isEnabled)
				it.findViewById<View>(R.id.auth_switch).performClick()
				assertEquals(
					View.GONE, it.findViewById<View>(R.id.auth_confirm_password_layout).visibility
				)
			}
		}
	}

	@Test
	fun passwordValidationIsDelegatedToFirebaseAndConfirmationStillMatches() {
		ActivityScenario.launch(AuthActivity::class.java).use { scenario ->
			scenario.onActivity { activity ->
				val requirements = activity.findViewById<View>(R.id.auth_password_requirements)
				val password = activity.findViewById<EditText>(R.id.auth_password)
				val confirmation = activity.findViewById<EditText>(R.id.auth_confirm_password)
				val firebaseNotice = activity.findViewById<TextView>(R.id.password_requirement_firebase)
				val match = activity.findViewById<TextView>(R.id.password_requirement_match)
				assertEquals(View.GONE, requirements.visibility)
				activity.findViewById<View>(R.id.auth_switch).performClick()
				assertEquals(View.VISIBLE, requirements.visibility)
				assertTrue(firebaseNotice.text.contains("Firebase Authentication"))
				activity.findViewById<EditText>(R.id.auth_name).setText("Student")
				activity.findViewById<EditText>(R.id.auth_student_id).setText("123456789")
				activity.findViewById<EditText>(R.id.auth_email).setText("student@example.test")
				password.setText("simple")
				assertEquals(
					ContextCompat.getColor(activity, R.color.brand_red_accessible),
					match.currentTextColor
				)
				assertFalse(activity.findViewById<View>(R.id.auth_submit).isEnabled)
				confirmation.setText(password.text)
				assertEquals(
					ContextCompat.getColor(activity, R.color.text_secondary),
					match.currentTextColor
				)
				assertTrue(activity.findViewById<View>(R.id.auth_submit).isEnabled)
				activity.findViewById<View>(R.id.auth_switch).performClick()
				assertEquals(View.GONE, requirements.visibility)
			}
		}
	}

	@Test
	fun signUpErrorsClearOnEditAndPasswordVisibilityStaysInSync() {
		ActivityScenario.launch(AuthActivity::class.java).use { scenario ->
			scenario.onActivity { activity ->
				activity.findViewById<View>(R.id.auth_switch).performClick()
				val studentId = activity.findViewById<EditText>(R.id.auth_student_id)
				studentId.text.replace(0, 0, "0a12-3 4!5")
				assertEquals("012345", studentId.text.toString())
				val studentLayout = activity.findViewById<com.google.android.material.textfield.TextInputLayout>(
					R.id.auth_student_id_layout
				)
				assertNull(studentLayout.error)

				val password = activity.findViewById<EditText>(R.id.auth_password)
				val confirmation = activity.findViewById<EditText>(R.id.auth_confirm_password)
				activity.findViewById<EditText>(R.id.auth_name).setText("Student")
				activity.findViewById<EditText>(R.id.auth_email).setText("invalid-email")
				password.setText("Aa1!abcd")
				confirmation.setText("Aa1!abcd")
				assertTrue(activity.findViewById<View>(R.id.auth_submit).isEnabled)
				activity.findViewById<View>(R.id.auth_submit).performClick()
				assertEquals(activity.getString(R.string.auth_student_id_error), studentLayout.error)
				studentId.text.append("6")
				assertNull(studentLayout.error)

				val email = activity.findViewById<EditText>(R.id.auth_email)
				activity.findViewById<View>(R.id.auth_submit).performClick()
				assertEquals(activity.getString(R.string.auth_email_error), activity.findViewById<com.google.android.material.textfield.TextInputLayout>(
					R.id.auth_email_layout
				).error)
				email.append("@example.com")
				assertNull(activity.findViewById<com.google.android.material.textfield.TextInputLayout>(
					R.id.auth_email_layout
				).error)
				password.setSelection(3)
				for (layoutId in listOf(
					R.id.auth_password_layout, R.id.auth_confirm_password_layout
				)) {
					val icon = activity.findViewById<View>(layoutId)
						.findViewById<View>(com.google.android.material.R.id.text_input_end_icon)
					icon.performClick()
					assertNull(password.transformationMethod)
					assertNull(confirmation.transformationMethod)
					assertEquals(3, password.selectionStart)
					icon.performClick()
					assertTrue(password.transformationMethod is android.text.method.PasswordTransformationMethod)
					assertTrue(confirmation.transformationMethod is android.text.method.PasswordTransformationMethod)
				}
			}
		}
	}

}
