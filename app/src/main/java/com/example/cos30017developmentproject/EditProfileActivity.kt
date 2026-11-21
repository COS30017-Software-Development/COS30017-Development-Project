package com.example.cos30017developmentproject

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.cos30017developmentproject.auth.AuthValidator
import com.example.cos30017developmentproject.auth.SessionManager
import com.example.cos30017developmentproject.auth.UserRepository
import com.example.cos30017developmentproject.data.local.AppDatabase
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class EditProfileActivity : AppCompatActivity() {
	private lateinit var repository: UserRepository
	private var userId: Long? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		userId = SessionManager(this).userId
		if (userId == null) {
			finish()
			return
		}
		enableEdgeToEdge()
		setContentView(R.layout.activity_edit_profile)
		repository = UserRepository(AppDatabase.getInstance(this).userDao())
		ViewCompat.setAccessibilityHeading(findViewById(R.id.profile_edit_title), true)
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.profile_edit_page)) { view, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
			val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
			view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
			WindowInsetsCompat.CONSUMED
		}
		findViewById<View>(R.id.profile_cancel).setOnClickListener { finish() }
		findViewById<View>(R.id.profile_save).setOnClickListener { saveName() }
		lifecycleScope.launch {
			val user = repository.getUser(userId ?: return@launch) ?: return@launch
			findViewById<EditText>(R.id.profile_name_input).setText(user.username)
		}
	}

	private fun saveName() {
		val name = findViewById<EditText>(R.id.profile_name_input).text.toString()
		val nameLayout = findViewById<TextInputLayout>(R.id.profile_name_input_layout)
		if (!AuthValidator.isNameValid(name)) {
			nameLayout.error = getString(R.string.auth_name_error)
			return
		}
		nameLayout.error = null
		findViewById<View>(R.id.profile_save).isEnabled = false
		lifecycleScope.launch {
			if (repository.updateUsername(userId ?: return@launch, name)) {
				Toast.makeText(this@EditProfileActivity, R.string.profile_name_saved, Toast.LENGTH_SHORT).show()
				finish()
			} else findViewById<View>(R.id.profile_save).isEnabled = true
		}
	}
}
