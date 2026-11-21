package com.example.cos30017developmentproject

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.cos30017developmentproject.auth.SessionManager
import com.example.cos30017developmentproject.auth.UserRepository
import com.example.cos30017developmentproject.data.local.AppDatabase
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {
	private lateinit var sessionManager: SessionManager

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		sessionManager = SessionManager(this)
		val userId = sessionManager.userId
		if (userId == null) {
			openLogin()
			return
		}
		enableEdgeToEdge()
		setContentView(R.layout.activity_profile)
		ViewCompat.setAccessibilityHeading(findViewById(R.id.profile_title), true)
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.profile_page)) { view, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
			view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
			WindowInsetsCompat.CONSUMED
		}
		findViewById<View>(R.id.profile_back).setOnClickListener { finish() }
		findViewById<View>(R.id.profile_logout).setOnClickListener {
			sessionManager.logout()
			openLogin()
		}
		val repository = UserRepository(AppDatabase.getInstance(this).userDao())
		lifecycleScope.launch {
			val user = repository.getUser(userId)
			if (user == null) {
				sessionManager.logout()
				openLogin()
				return@launch
			}
			findViewById<TextView>(R.id.profile_name).text = user.username
			findViewById<TextView>(R.id.profile_email).text = user.email
			findViewById<TextView>(R.id.profile_student_id).text = user.studentId
		}
	}

	private fun openLogin() {
		startActivity(Intent(this, AuthActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
		finish()
	}
}
