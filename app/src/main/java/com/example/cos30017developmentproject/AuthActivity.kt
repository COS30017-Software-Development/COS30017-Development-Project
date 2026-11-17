package com.example.cos30017developmentproject

import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.widget.Checkable
import com.google.android.material.textfield.TextInputLayout
import android.view.View
import android.widget.TextView
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
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
		findViewById<EditText>(R.id.auth_password).doAfterTextChanged { updatePasswordRequirements() }
		findViewById<EditText>(R.id.auth_student_id).doAfterTextChanged { text ->
			findViewById<TextInputLayout>(R.id.auth_student_id_layout).error =
				if (!text.isNullOrEmpty() && text.length != 9) getString(R.string.auth_student_id_error) else null
		}
		setupPasswordVisibility()
		showForm(savedInstanceState?.getBoolean("auth.registering") ?: false)
	}

	override fun onSaveInstanceState(outState: Bundle) {
		outState.putBoolean("auth.registering", registering)
		super.onSaveInstanceState(outState)
	}

	private fun showForm(signUp: Boolean) {
		registering = signUp
		findViewById<View>(R.id.auth_password_requirements).isVisible = signUp
		updatePasswordRequirements()
		findViewById<View>(R.id.auth_name_layout).isVisible = signUp
		findViewById<View>(R.id.auth_student_id_layout).isVisible = signUp
		findViewById<View>(R.id.auth_confirm_password_layout).isVisible = signUp
		findViewById<TextView>(R.id.auth_title).setText(if (signUp) R.string.auth_register_title else R.string.auth_login_title)
		findViewById<TextView>(R.id.auth_submit).setText(if (signUp) R.string.auth_sign_up else R.string.auth_login)
		findViewById<TextView>(R.id.auth_switch).setText(if (signUp) R.string.auth_login else R.string.auth_sign_up)
		title = getString(if (signUp) R.string.auth_sign_up else R.string.auth_login)
	}

	private fun setupPasswordVisibility() {
		val layouts = listOf(
			findViewById<TextInputLayout>(R.id.auth_password_layout),
			findViewById<TextInputLayout>(R.id.auth_confirm_password_layout)
		)
		for (layout in layouts) {
			layout.setEndIconOnClickListener {
				val showPassword =
					layout.editText!!.transformationMethod is PasswordTransformationMethod
				for (passwordLayout in layouts) {
					val field = passwordLayout.editText!!
					val selectionStart = field.selectionStart
					val selectionEnd = field.selectionEnd
					field.transformationMethod =
						if (showPassword) null else PasswordTransformationMethod.getInstance()
					if (selectionStart >= 0 && selectionEnd >= 0) field.setSelection(
						selectionStart,
						selectionEnd
					)
					(passwordLayout.findViewById<View>(com.google.android.material.R.id.text_input_end_icon) as Checkable)
						.isChecked = showPassword
				}
			}
		}
	}

	private fun updatePasswordRequirements() {
		val password = findViewById<EditText>(R.id.auth_password).text.toString()
		val requirements = listOf(
			R.id.password_requirement_length to (password.length in 8..16),
			R.id.password_requirement_digit to password.any { it in '0'..'9' },
			R.id.password_requirement_special to password.any { !it.isLetterOrDigit() && !it.isWhitespace() },
			R.id.password_requirement_uppercase to password.any { it.isUpperCase() },
			R.id.password_requirement_lowercase to password.any { it.isLowerCase() }
		)
		for ((id, met) in requirements) {
			val color = when {
				password.isEmpty() -> R.color.text_secondary
				met -> R.color.password_requirement_met
				else -> R.color.brand_red_accessible
			}
			findViewById<TextView>(id).setTextColor(ContextCompat.getColor(this, color))
		}
	}

}
