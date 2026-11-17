package com.example.cos30017developmentproject

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible

class AuthActivity : AppCompatActivity() {
	private var registering = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContentView(R.layout.activity_auth)
		ViewCompat.setAccessibilityHeading(findViewById(R.id.auth_title), true)
		val page = findViewById<View>(R.id.auth_page)
		ViewCompat.setOnApplyWindowInsetsListener(page) { view, insets ->
			val bars =
				insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
			val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
			view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
			WindowInsetsCompat.CONSUMED
		}
		ViewCompat.requestApplyInsets(page)
		findViewById<View>(R.id.auth_back).setOnClickListener { finish() }
		onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() {
				finish()
			}
		})
		findViewById<View>(R.id.auth_switch).setOnClickListener { showForm(!registering) }
		showForm(savedInstanceState?.getBoolean("auth.registering") ?: false)
	}

	override fun onSaveInstanceState(outState: Bundle) {
		outState.putBoolean("auth.registering", registering)
		super.onSaveInstanceState(outState)
	}

	private fun showForm(signUp: Boolean) {
		registering = signUp
		findViewById<View>(R.id.auth_name_layout).isVisible = signUp
		findViewById<View>(R.id.auth_student_id_layout).isVisible = signUp
		findViewById<View>(R.id.auth_confirm_password_layout).isVisible = signUp
		findViewById<TextView>(R.id.auth_title).setText(if (signUp) R.string.auth_register_title else R.string.auth_login_title)
		findViewById<TextView>(R.id.auth_submit).setText(if (signUp) R.string.auth_sign_up else R.string.auth_login)
		findViewById<TextView>(R.id.auth_switch).setText(if (signUp) R.string.auth_login else R.string.auth_sign_up)
		title = getString(if (signUp) R.string.auth_sign_up else R.string.auth_login)
	}
}
