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
import com.google.android.material.navigation.NavigationView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthPageTest {
	private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

	private fun checkAuth(action: (AuthActivity) -> Unit) {
		instrumentation.waitForIdleSync()
		instrumentation.runOnMainSync {
			val activity = ActivityLifecycleMonitorRegistry.getInstance()
				.getActivitiesInStage(Stage.RESUMED).filterIsInstance<AuthActivity>().single()
			action(activity)
		}
	}

	@Test
	fun protectedEntriesOpenLoginAndBackReturnsHome() {
		for (entry in listOf(
			R.id.nav_profile,
			R.id.nav_post,
			R.id.drawer_profile,
			R.id.drawer_post
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
					View.GONE,
					it.findViewById<View>(R.id.auth_confirm_password_layout).visibility
				)
			}
		}
	}

	@Test
	fun passwordRequirementsUpdateIndividuallyAndOnlyAppearOnRegistration() {
		ActivityScenario.launch(AuthActivity::class.java).use { scenario ->
			scenario.onActivity { activity ->
				val requirements = activity.findViewById<View>(R.id.auth_password_requirements)
				val password = activity.findViewById<EditText>(R.id.auth_password)
				val rules = listOf(
					R.id.password_requirement_length, R.id.password_requirement_digit,
					R.id.password_requirement_special, R.id.password_requirement_uppercase,
					R.id.password_requirement_lowercase
				)

				fun color(id: Int) = activity.findViewById<TextView>(id).currentTextColor
				fun expected(id: Int) = ContextCompat.getColor(activity, id)
				assertEquals(View.GONE, requirements.visibility)
				activity.findViewById<View>(R.id.auth_switch).performClick()
				assertEquals(View.VISIBLE, requirements.visibility)
				rules.forEach { assertEquals(expected(R.color.text_secondary), color(it)) }
				password.setText("a")
				assertEquals(
					expected(R.color.password_requirement_met),
					color(R.id.password_requirement_lowercase)
				)
				rules.filter { it != R.id.password_requirement_lowercase }.forEach {
					assertEquals(expected(R.color.brand_red_accessible), color(it))
				}
				for (value in listOf("Aa1!abcd", "Aa1!abcdefghijkl")) {
					password.setText(value)
					rules.forEach {
						assertEquals(
							expected(R.color.password_requirement_met),
							color(it)
						)
					}
				}
				password.setText("Aa1!abcdefghijklM")
				assertEquals(
					expected(R.color.brand_red_accessible),
					color(R.id.password_requirement_length)
				)
				password.setText("Aa1 abcdef")
				assertEquals(
					expected(R.color.brand_red_accessible),
					color(R.id.password_requirement_special)
				)
				password.setText("")
				rules.forEach { assertEquals(expected(R.color.text_secondary), color(it)) }
				activity.findViewById<View>(R.id.auth_switch).performClick()
				assertEquals(View.GONE, requirements.visibility)
			}
		}
	}

}
