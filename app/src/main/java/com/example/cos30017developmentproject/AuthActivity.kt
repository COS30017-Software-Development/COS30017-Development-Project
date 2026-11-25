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
import com.example.cos30017developmentproject.auth.FirebaseAuthManager
import com.example.cos30017developmentproject.auth.SessionManager
import com.example.cos30017developmentproject.auth.UserRepository
import com.example.cos30017developmentproject.data.local.AppDatabase
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Arrays

class AuthActivity : AppCompatActivity() {
	private var registering = false
	private lateinit var repository: UserRepository
	private lateinit var sessionManager: SessionManager
	private lateinit var firebaseAuthManager: FirebaseAuthManager

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContentView(R.layout.activity_auth)
		repository = UserRepository(AppDatabase.getInstance(this).userDao())
		sessionManager = SessionManager(this)
		firebaseAuthManager = FirebaseAuthManager()
		if (sessionManager.userId != null) {
			openAfterLogin()
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
					R.id.auth_name -> clearFieldError(R.id.auth_name_layout)
					R.id.auth_student_id -> clearFieldError(R.id.auth_student_id_layout)
					R.id.auth_email -> clearFieldError(R.id.auth_email_layout)
					R.id.auth_password -> clearFieldError(R.id.auth_password_layout)
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
		restoreCurrentSession()
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
		val password = text(R.id.auth_password)
		findViewById<View>(R.id.auth_submit).isEnabled = if (registering) {
			text(R.id.auth_name).isNotEmpty() &&
				text(R.id.auth_student_id).isNotEmpty() &&
				text(R.id.auth_email).isNotEmpty() &&
				password.isNotEmpty() &&
				password == text(R.id.auth_confirm_password)
		} else text(R.id.auth_email).isNotEmpty() && password.isNotEmpty()
	}

	private fun submit() {
		clearErrors()
		if (registering && !validateRegistration()) return
		val password = text(R.id.auth_password).toCharArray()
		findViewById<View>(R.id.auth_submit).isEnabled = false
		lifecycleScope.launch {
			try {
				val result = if (registering) {
					firebaseAuthManager.register(text(R.id.auth_email), password.concatToString())
				} else {
					firebaseAuthManager.login(text(R.id.auth_email), password.concatToString())
				}
				when (result) {
					is FirebaseAuthManager.AuthResult.Success -> {
						val firebaseUser = result.user
						val localUser = if (registering) {
							try {
								repository.registerFirebaseUser(
									firebaseUser.uid,
									text(R.id.auth_name),
									text(R.id.auth_student_id),
									firebaseUser.email.orEmpty()
								)
							} catch (exception: CancellationException) {
								withContext(NonCancellable) {
									rollbackRegistration(firebaseUser.uid)
								}
								throw exception
							} catch (exception: Exception) {
								null
							}
						} else {
							repository.findOrLinkFirebaseUser(
								firebaseUser.uid,
								firebaseUser.email.orEmpty()
							)
						}
						if (localUser == null) {
							if (registering) {
								val rolledBack = rollbackRegistration(firebaseUser.uid)
								val message = if (rolledBack) {
									R.string.auth_room_save_rolled_back_error
								} else {
									R.string.auth_room_save_cleanup_failed_error
								}
								showAuthError(getString(message), R.id.auth_email_layout)
							} else {
								firebaseAuthManager.signOut()
								showAuthError(getString(R.string.auth_profile_missing_error), R.id.auth_email_layout)
							}
						} else {
							sessionManager.login(localUser)
							openAfterLogin()
						}
					}
					is FirebaseAuthManager.AuthResult.Failure -> showAuthError(result.error)
				}
			} catch (exception: CancellationException) {
				throw exception
			} catch (exception: Exception) {
				firebaseAuthManager.signOut()
				showAuthError(getString(R.string.auth_unexpected_error), R.id.auth_email_layout)
			} finally {
				Arrays.fill(password, '\u0000')
			}
		}
	}

	private suspend fun rollbackRegistration(firebaseUid: String): Boolean {
		return try {
			firebaseAuthManager.deleteCurrentUser(firebaseUid) is
				FirebaseAuthManager.AccountDeletionResult.Deleted
		} finally {
			firebaseAuthManager.signOutIfCurrentUser(firebaseUid)
		}
	}

	private fun restoreCurrentSession() {
		val firebaseUser = firebaseAuthManager.getCurrentUser() ?: return
		lifecycleScope.launch {
			try {
				val localUser = repository.findOrLinkFirebaseUser(
					firebaseUser.uid,
					firebaseUser.email.orEmpty()
				)
				if (localUser == null) {
					firebaseAuthManager.signOut()
					showAuthError(getString(R.string.auth_profile_missing_error), R.id.auth_email_layout)
					return@launch
				}
				sessionManager.login(localUser)
				openAfterLogin()
			} catch (exception: CancellationException) {
				throw exception
			} catch (exception: Exception) {
				firebaseAuthManager.signOut()
				showAuthError(getString(R.string.auth_unexpected_error), R.id.auth_email_layout)
			}
		}
	}

	private fun showAuthError(error: FirebaseAuthManager.AuthError) {
		val message = when (error) {
			FirebaseAuthManager.AuthError.EMAIL_ALREADY_IN_USE -> R.string.auth_duplicate_email_error
			FirebaseAuthManager.AuthError.INVALID_EMAIL -> R.string.auth_email_error
			FirebaseAuthManager.AuthError.WEAK_PASSWORD -> R.string.auth_weak_password_error
			FirebaseAuthManager.AuthError.INVALID_CREDENTIALS -> R.string.auth_credentials_error
			FirebaseAuthManager.AuthError.USER_DISABLED -> R.string.auth_user_disabled_error
			FirebaseAuthManager.AuthError.NETWORK -> R.string.auth_network_error
			FirebaseAuthManager.AuthError.TOO_MANY_REQUESTS -> R.string.auth_too_many_requests_error
			FirebaseAuthManager.AuthError.EMAIL_PROVIDER_DISABLED -> R.string.auth_provider_disabled_error
			FirebaseAuthManager.AuthError.UNKNOWN -> R.string.auth_unexpected_error
		}
		val target = if (error == FirebaseAuthManager.AuthError.INVALID_CREDENTIALS ||
			error == FirebaseAuthManager.AuthError.WEAK_PASSWORD
		) R.id.auth_password_layout else R.id.auth_email_layout
		showAuthError(getString(message), target)
	}

	private fun showAuthError(message: String, targetLayout: Int) {
		layout(targetLayout).error = message
		updateSubmitState()
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
		)) clearFieldError(id)
	}

	private fun clearFieldError(id: Int) {
		layout(id).apply {
			error = null
			isErrorEnabled = false
		}
	}

	private fun text(id: Int) = findViewById<EditText>(id).text.toString()

	private fun layout(id: Int) = findViewById<TextInputLayout>(id)

	private fun openProfile() {
		startActivity(Intent(this, ProfileActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
		finish()
	}

	private fun openAfterLogin() {
		val currentUser = firebaseAuthManager.getCurrentUser()
		if (currentUser == null || sessionManager.userId == null) {
			showAuthError(getString(R.string.auth_unexpected_error), R.id.auth_email_layout)
			return
		}
		lifecycleScope.launch {
			val destination = if (firebaseAuthManager.hasAdminRole(currentUser.uid)) {
				AdminDashboardActivity::class.java
			} else {
				ProfileActivity::class.java
			}
			startActivity(Intent(this@AuthActivity, destination).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
			finish()
		}
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
		val matchColor = if (password == confirmation) R.color.text_secondary else R.color.brand_red_accessible
		findViewById<TextView>(R.id.password_requirement_match).setTextColor(
			ContextCompat.getColor(this, matchColor)
		)
	}

}
