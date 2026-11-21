package com.example.cos30017developmentproject

import android.content.Intent
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.Checkable
import android.widget.EditText
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.example.cos30017developmentproject.auth.AuthValidator
import com.example.cos30017developmentproject.auth.SessionManager
import com.example.cos30017developmentproject.auth.UserRepository
import com.example.cos30017developmentproject.data.local.AppDatabase
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.util.Arrays

class AuthActivity : AppCompatActivity() {
	private var registering = false
	private lateinit var repository: UserRepository
	private lateinit var sessionManager: SessionManager

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContentView(R.layout.activity_auth)
		repository = UserRepository(AppDatabase.getInstance(this).userDao())
		sessionManager = SessionManager(this)
		if (sessionManager.userId != null) {
			openProfile()
			return
		}
		ViewCompat.setAccessibilityHeading(findViewById(R.id.auth_title), true)
		val page = findViewById<View>(R.id.auth_page)
		ViewCompat.setOnApplyWindowInsetsListener(page) { view, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
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
		for (fieldId in listOf(
			R.id.auth_name, R.id.auth_student_id, R.id.auth_email,
			R.id.auth_password, R.id.auth_confirm_password
		)) {
			findViewById<EditText>(fieldId).doAfterTextChanged {
				when (fieldId) {
					R.id.auth_name -> layout(R.id.auth_name_layout).error = null
					R.id.auth_student_id -> layout(R.id.auth_student_id_layout).error = null
					R.id.auth_email -> layout(R.id.auth_email_layout).error = null
				}
				if (fieldId == R.id.auth_password || fieldId == R.id.auth_confirm_password) {
					updatePasswordRequirements()
				}
				updateSubmitState()
			}
		}
		setupPasswordVisibility()
		findViewById<View>(R.id.auth_submit).setOnClickListener { submit() }
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
		clearErrors()
		updateSubmitState()
	}

	private fun updateSubmitState() {
		val emailValid = AuthValidator.isEmailValid(text(R.id.auth_email))
		val password = text(R.id.auth_password)
		findViewById<View>(R.id.auth_submit).isEnabled = if (registering) {
			AuthValidator.isPasswordValid(password) &&
				password == text(R.id.auth_confirm_password)
		} else emailValid && AuthValidator.isPasswordValid(password)
	}

	private fun submit() {
		clearErrors()
		if (registering && !validateRegistration()) return
		if (!registering && !AuthValidator.isEmailValid(text(R.id.auth_email))) {
			layout(R.id.auth_email_layout).error = getString(R.string.auth_email_error)
			return
		}
		val password = text(R.id.auth_password).toCharArray()
		findViewById<View>(R.id.auth_submit).isEnabled = false
		lifecycleScope.launch {
			val result = try {
				if (registering) repository.register(
					text(R.id.auth_name), text(R.id.auth_student_id), text(R.id.auth_email), password
				) else repository.login(text(R.id.auth_email), password)
			} finally {
				Arrays.fill(password, '\u0000')
			}
			when (result) {
				is UserRepository.AuthResult.Success -> {
					sessionManager.login(result.user.id)
					openProfile()
				}
				UserRepository.AuthResult.DuplicateEmail -> {
					layout(R.id.auth_email_layout).error = getString(R.string.auth_duplicate_email_error)
					updateSubmitState()
				}
				UserRepository.AuthResult.InvalidCredentials -> {
					layout(R.id.auth_password_layout).error = getString(R.string.auth_credentials_error)
					updateSubmitState()
				}
				UserRepository.AuthResult.InvalidRegistration -> {
					validateRegistration()
					updateSubmitState()
				}
			}
		}
	}

	private fun validateRegistration(): Boolean {
		var valid = true
		if (!AuthValidator.isNameValid(text(R.id.auth_name))) {
			layout(R.id.auth_name_layout).error = getString(R.string.auth_name_error)
			valid = false
		}
		if (!AuthValidator.isStudentIdValid(text(R.id.auth_student_id))) {
			layout(R.id.auth_student_id_layout).error = getString(R.string.auth_student_id_error)
			valid = false
		}
		if (!AuthValidator.isEmailValid(text(R.id.auth_email))) {
			layout(R.id.auth_email_layout).error = getString(R.string.auth_email_error)
			valid = false
		}
		if (!AuthValidator.isPasswordValid(text(R.id.auth_password))) {
			layout(R.id.auth_password_layout).error = getString(R.string.auth_password_error)
			valid = false
		}
		if (text(R.id.auth_password) != text(R.id.auth_confirm_password)) {
			layout(R.id.auth_confirm_password_layout).error = getString(R.string.auth_confirm_password_error)
			valid = false
		}
		return valid
	}

	private fun clearErrors() {
		for (id in listOf(
			R.id.auth_name_layout, R.id.auth_student_id_layout, R.id.auth_email_layout,
			R.id.auth_password_layout, R.id.auth_confirm_password_layout
		)) layout(id).error = null
	}

	private fun text(id: Int) = findViewById<EditText>(id).text.toString()

	private fun layout(id: Int) = findViewById<TextInputLayout>(id)

	private fun openProfile() {
		startActivity(Intent(this, ProfileActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
		finish()
	}

	private fun setupPasswordVisibility() {
		val layouts = listOf(
			findViewById<TextInputLayout>(R.id.auth_password_layout),
			findViewById<TextInputLayout>(R.id.auth_confirm_password_layout)
		)
		for (layout in layouts) {
			layout.setEndIconOnClickListener {
				val showPassword = layout.editText!!.transformationMethod is PasswordTransformationMethod
				for (passwordLayout in layouts) {
					val field = passwordLayout.editText!!
					val selectionStart = field.selectionStart
					val selectionEnd = field.selectionEnd
					field.transformationMethod = if (showPassword) null else PasswordTransformationMethod.getInstance()
					if (selectionStart >= 0 && selectionEnd >= 0) field.setSelection(
						selectionStart, selectionEnd
					)
					(passwordLayout.findViewById<View>(com.google.android.material.R.id.text_input_end_icon) as Checkable).isChecked = showPassword
				}
			}
		}
	}

	private fun updatePasswordRequirements() {
		val password = findViewById<EditText>(R.id.auth_password).text.toString()
		val confirmation = findViewById<EditText>(R.id.auth_confirm_password).text.toString()
		val requirements = listOf(
			R.id.password_requirement_length to (password.length in 8 .. 16),
			R.id.password_requirement_digit to password.any { it in '0' .. '9' },
			R.id.password_requirement_special to password.any { !it.isLetterOrDigit() && !it.isWhitespace() },
			R.id.password_requirement_uppercase to password.any { it.isUpperCase() },
			R.id.password_requirement_lowercase to password.any { it.isLowerCase() })
		for ((id, met) in requirements) {
			val color = when {
				password.isEmpty() -> R.color.text_secondary
				met -> R.color.text_secondary
				else -> R.color.brand_red_accessible
			}
			findViewById<TextView>(id).setTextColor(ContextCompat.getColor(this, color))
		}
		val matchColor = if (password == confirmation) R.color.text_secondary else R.color.brand_red_accessible
		findViewById<TextView>(R.id.password_requirement_match).setTextColor(
			ContextCompat.getColor(this, matchColor)
		)
	}

}
